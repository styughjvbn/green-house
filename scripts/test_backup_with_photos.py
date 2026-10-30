"""Real rclone local-alias regressions; optional real PostgreSQL dump/restore."""
import fcntl
import hashlib
import json
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import tempfile
import time
import unittest
import uuid


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts/backup-with-photos.sh"
RCLONE = os.environ.get("RCLONE_TEST_BIN") or shutil.which("rclone")


@unittest.skipUnless(RCLONE, "Set RCLONE_TEST_BIN to run real local-alias tests")
class RemoteBackupTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory(prefix="photo-backup-test-")
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.source = self.root / "source"
        (self.source / "ab").mkdir(parents=True)
        self.photo = self.source / "ab/photo.png"
        self.photo.write_bytes(b"immutable test photo")
        (self.source / "ab/.upload-pending.tmp").write_bytes(b"unfinished")
        self.backup = self.root / "backup"
        self.remote = self.root / "remote"
        self.remote.mkdir()
        self.bin = self.root / "bin"
        self.bin.mkdir()
        config = self.root / "rclone.conf"
        config.write_text(f"[testdrive]\ntype = alias\nremote = {self.remote}\n")
        self.log = self.root / "rclone.jsonl"
        self.env = dict(
            os.environ,
            PATH=str(self.bin) + ":" + os.environ["PATH"],
            SALES_PHOTO_STORAGE_ROOT=str(self.source),
            RCLONE_BACKUP_REMOTE="testdrive:green-house",
            RCLONE_CONFIG=str(config),
            RCLONE_TEST_REAL_BIN=str(Path(RCLONE).resolve()),
            RCLONE_TEST_LOG=str(self.log),
            GREENHOUSE_OPERATION_LOCK_FILE=str(self.root / "operation.lock"),
            RCLONE_TEST_MODE="",
        )
        self.write_executable(
            "pg_dump",
            """#!/usr/bin/python3
from pathlib import Path
import os,sys
target=next(arg.split('=',1)[1] for arg in sys.argv if arg.startswith('--file='))
Path(target).write_bytes(b'PGDMP unit fixture')
if os.environ.get('TEST_DUMP_FAIL')=='1': sys.exit(26)
""",
        )
        # The transport is real rclone. Only failure/race injection is wrapped.
        self.write_executable(
            "rclone",
            """#!/usr/bin/python3
import json,os,subprocess,sys
from pathlib import Path
a=sys.argv[1:]
with open(os.environ['RCLONE_TEST_LOG'],'a') as log: log.write(json.dumps(a)+'\\n')
mode=os.environ.get('RCLONE_TEST_MODE','')
phase=('photos' if len(a)>2 and a[2].endswith('/photos') else
       'database' if len(a)>2 and '/database/' in a[2] else
       'complete' if len(a)>2 and '/completed/' in a[2] else '')
if mode=='fail-photo-check' and a[0]=='check' and phase=='photos': sys.exit(23)
if mode=='fail-db-copy' and a[0]=='copyto' and phase=='database': sys.exit(24)
if mode=='fail-complete' and a[0]=='copyto' and phase=='complete': sys.exit(25)
result=subprocess.run([os.environ['RCLONE_TEST_REAL_BIN']]+a).returncode
if result==0 and mode=='late-upload' and a[0]=='copy' and phase=='photos':
    (Path(os.environ['SALES_PHOTO_STORAGE_ROOT'])/'ab/late.png').write_bytes(b'after snapshot')
if result==0 and mode=='changed-photo' and a[0]=='check' and '/manifests/' in a[2]:
    (Path(os.environ['SALES_PHOTO_STORAGE_ROOT'])/'ab/photo.png').write_bytes(b'changed after remote verification')
sys.exit(result)
""",
        )

    def write_executable(self, name, content):
        path = self.bin / name
        path.write_text(content)
        path.chmod(0o755)

    def run_backup(self, success=True, **env):
        result = subprocess.run(
            ["bash", str(SCRIPT), str(self.backup)],
            env=dict(self.env, **env),
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=90,
            text=True,
        )
        if success:
            self.assertEqual(result.returncode, 0, result.stderr)
        else:
            self.assertNotEqual(result.returncode, 0, result.stdout)
        return result

    def completed(self):
        return list((self.remote / "green-house/completed").glob("*.json"))

    def test_round_trip_completion_and_no_local_photo_replica(self):
        self.run_backup()
        completion = json.loads(self.completed()[0].read_text())
        self.assertEqual(len(completion["photos"]), 1)
        self.assertFalse((self.backup / "photos").exists())
        remote_root = self.remote / "green-house"
        self.assertFalse((remote_root / "photos/ab/.upload-pending.tmp").exists())
        self.assertEqual((remote_root / "photos/ab/photo.png").read_bytes(), self.photo.read_bytes())
        self.assertEqual(
            hashlib.sha256((remote_root / completion["database"]["path"]).read_bytes()).hexdigest(),
            completion["database"]["sha256"],
        )
        calls = [json.loads(line) for line in self.log.read_text().splitlines()]
        self.assertEqual(calls[-2][0], "copyto")
        self.assertIn("/completed/", calls[-2][2])
        self.assertEqual(calls[-1][0], "cat")
        self.assertTrue(list((self.backup / "completed").glob("*.json")))

    def test_incremental_copy_preserves_remote_only_files_and_old_dumps(self):
        self.run_backup()
        old = self.remote / "green-house/photos/old-only.png"
        old.write_bytes(b"needed by an older database")
        (self.source / "ab/new.png").write_bytes(b"new photo")
        self.run_backup()
        self.assertEqual(len(self.completed()), 2)
        self.assertEqual(len(list((self.backup / "database").glob("*.dump"))), 2)
        self.assertEqual(old.read_bytes(), b"needed by an older database")

    def test_changed_immutable_source_does_not_overwrite_good_remote(self):
        self.run_backup()
        original = self.photo.read_bytes()
        self.photo.write_bytes(b"corrupted original")
        self.run_backup(success=False)
        self.assertEqual(len(self.completed()), 1)
        self.assertEqual((self.remote / "green-house/photos/ab/photo.png").read_bytes(), original)
        self.assertEqual(len(list((self.backup / "database").glob("*.dump"))), 2)

    def test_failure_never_publishes_completion_and_keeps_local_dump(self):
        for mode in ["fail-photo-check", "fail-db-copy", "fail-complete"]:
            with self.subTest(mode=mode):
                self.run_backup(success=False, RCLONE_TEST_MODE=mode)
                self.assertEqual(self.completed(), [])
                self.assertEqual(list((self.backup / "completed").glob("*.json")), [])
        self.assertEqual(len(list((self.backup / "database").glob("*.dump"))), 3)
        self.assertEqual(list((self.backup / "database").glob("*.partial")), [])

    def test_upload_after_snapshot_does_not_invalidate_the_dump(self):
        self.run_backup(RCLONE_TEST_MODE="late-upload")
        self.assertTrue((self.source / "ab/late.png").exists())
        completion = json.loads(self.completed()[0].read_text())
        self.assertEqual([p["key"] for p in completion["photos"]], ["ab/photo.png"])
        self.assertFalse((self.remote / "green-house/photos/ab/late.png").exists())

    def test_changed_photo_before_completion_is_rejected(self):
        self.run_backup(success=False, RCLONE_TEST_MODE="changed-photo")
        self.assertEqual(self.completed(), [])

    def test_failed_dump_removes_partial_and_does_not_upload(self):
        self.run_backup(success=False, TEST_DUMP_FAIL="1")
        self.assertFalse(self.log.exists())
        self.assertEqual(list((self.backup / "database").iterdir()), [])

    def test_empty_photo_store_is_a_valid_backup(self):
        self.photo.unlink()
        self.run_backup()
        self.assertEqual(json.loads(self.completed()[0].read_text())["photos"], [])

    def test_invalid_remote_fails_before_dump_or_upload(self):
        for remote in ["", "testdrive:", "testdrive:/", "testdrive:../outside"]:
            with self.subTest(remote=remote):
                self.run_backup(success=False, RCLONE_BACKUP_REMOTE=remote)
                self.assertFalse(self.log.exists())
                self.assertFalse((self.backup / "database").exists())

    def test_symlinks_and_special_files_fail_without_cloud_commit(self):
        link = self.source / "ab/link.png"
        link.symlink_to(self.photo)
        self.run_backup(success=False)
        self.assertFalse(self.log.exists())
        link.unlink()
        os.mkfifo(link)
        self.run_backup(success=False)
        self.assertFalse(self.log.exists())

    def test_operation_lock_prevents_overlapping_runs(self):
        with open(self.env["GREENHOUSE_OPERATION_LOCK_FILE"], "w") as lock:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            self.run_backup(success=False)
        self.assertFalse(self.log.exists())

    @unittest.skipUnless(
        os.environ.get("GREENHOUSE_BACKUP_POSTGRES_TEST") == "1" and shutil.which("docker"),
        "Set GREENHOUSE_BACKUP_POSTGRES_TEST=1 for real PostgreSQL restore",
    )
    def test_real_postgres_dump_remote_download_and_restore(self):
        name = "green-house-backup-test-" + uuid.uuid4().hex[:12]
        env = dict(os.environ, POSTGRES_PASSWORD=secrets.token_urlsafe(24))
        subprocess.run(
            ["docker", "run", "-d", "--rm", "--name", name, "--network", "none",
             "--tmpfs", "/var/lib/postgresql:rw", "-e", "POSTGRES_PASSWORD", "postgres:18"],
            env=env, check=True, stdout=subprocess.DEVNULL,
        )
        try:
            for _ in range(60):
                ready = subprocess.run(
                    # The image briefly starts a Unix-socket-only server during
                    # initialization. Wait for final TCP readiness, not that server.
                    ["docker", "exec", name, "pg_isready", "-h", "127.0.0.1", "-U", "postgres"],
                    stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                )
                if ready.returncode == 0:
                    break
                time.sleep(1)
            else:
                self.fail("PostgreSQL did not become ready")
            sql = "create table photo_refs (path text primary key, sha256 text not null); insert into photo_refs values ('ab/photo.png', '" + hashlib.sha256(self.photo.read_bytes()).hexdigest() + "');"
            subprocess.run(
                ["docker", "exec", name, "psql", "-U", "postgres", "-v", "ON_ERROR_STOP=1", "-c", sql],
                check=True, stdout=subprocess.DEVNULL,
            )
            self.env["TEST_POSTGRES_CONTAINER"] = name
            self.write_executable(
                "pg_dump",
                """#!/usr/bin/python3
import os,subprocess,sys
target=next(arg.split('=',1)[1] for arg in sys.argv if arg.startswith('--file='))
with open(target,'wb') as output:
    subprocess.run(['docker','exec','--user',str(os.getuid())+':'+str(os.getgid()),os.environ['TEST_POSTGRES_CONTAINER'],'pg_dump','-U','postgres','-Fc','postgres'],stdout=output,check=True)
""",
            )
            self.run_backup()
            completed = json.loads(self.completed()[0].read_text())
            restored = self.root / "restored"
            subprocess.run(
                [RCLONE, "copy", "testdrive:green-house/photos", str(restored)],
                env=self.env, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE,
            )
            subprocess.run(["docker", "exec", name, "createdb", "-U", "postgres", "restored"], check=True)
            cloud_dump = self.remote / "green-house" / completed["database"]["path"]
            with cloud_dump.open("rb") as stream:
                subprocess.run(
                    ["docker", "exec", "-i", "--user", f"{os.getuid()}:{os.getgid()}", name,
                     "pg_restore", "-U", "postgres", "--exit-on-error", "-d", "restored"],
                    stdin=stream, check=True,
                )
            result = subprocess.check_output(
                ["docker", "exec", name, "psql", "-U", "postgres", "-d", "restored",
                 "-Atc", "select sha256 from photo_refs where path='ab/photo.png'"], text=True,
            ).strip()
            self.assertEqual(hashlib.sha256((restored / "ab/photo.png").read_bytes()).hexdigest(), result)
        finally:
            subprocess.run(["docker", "stop", name], check=True, stdout=subprocess.DEVNULL)


if __name__ == "__main__":
    unittest.main()
