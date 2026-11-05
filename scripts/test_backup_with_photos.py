"""Real rclone local-alias regressions; optional real PostgreSQL dump/restore."""
import fcntl
import hashlib
import gzip
import importlib.util
import json
import os
from pathlib import Path
import secrets
import shutil
import shlex
import subprocess
import tempfile
import time
import unittest
import uuid


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts/backup-with-photos.sh"
RCLONE = os.environ.get("RCLONE_TEST_BIN") or shutil.which("rclone")


class BackupEnvironmentTest(unittest.TestCase):
    def test_literal_settings_and_exported_precedence(self):
        spec = importlib.util.spec_from_file_location("backup_environment", ROOT / "scripts/backup_environment.py")
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        with tempfile.TemporaryDirectory() as directory:
            config = Path(directory) / "backup.env"
            marker = Path(directory) / "must-not-exist"
            config.write_text("PGUSER=file-user\nLOCAL_BACKUP_DIR=" + shlex.quote(f"$(touch {marker})") + "\n")
            result = module.load(config, {"PGUSER": "exported-user"})
            self.assertEqual(result["PGUSER"], "exported-user")
            self.assertEqual(result["LOCAL_BACKUP_DIR"], f"$(touch {marker})")
            self.assertFalse(marker.exists())

    def test_invalid_settings_do_not_disclose_values(self):
        spec = importlib.util.spec_from_file_location("backup_environment", ROOT / "scripts/backup_environment.py")
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        with tempfile.TemporaryDirectory() as directory:
            config = Path(directory) / "backup.env"
            for line in ["UNKNOWN=private-value", "PGPASSWORD='private-value", "PGPASSWORD=private-value extra"]:
                config.write_text(line)
                with self.assertRaises(ValueError) as raised:
                    module.load(config, {})
                self.assertNotIn("private-value", str(raised.exception))


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
            RCLONE_BACKUP_REMOTE="testdrive:green-house-backups",
            LOCAL_BACKUP_DIR=str(self.backup),
            RCLONE_CONFIG=str(config),
            RCLONE_TEST_REAL_BIN=str(Path(RCLONE).resolve()),
            RCLONE_TEST_LOG=str(self.log),
            GREENHOUSE_OPERATION_LOCK_FILE=str(self.root / "operation.lock"),
            RCLONE_TEST_MODE="",
        )
        self.settings = self.root / "backup.env"
        keys = ["SALES_PHOTO_STORAGE_ROOT", "LOCAL_BACKUP_DIR", "RCLONE_BACKUP_REMOTE", "RCLONE_CONFIG", "GREENHOUSE_OPERATION_LOCK_FILE"]
        self.settings.write_text("".join(key + "=" + shlex.quote(self.env[key]) + "\n" for key in keys))
        self.settings.chmod(0o600)
        self.write_executable(
            "pg_dump",
            """#!/usr/bin/python3
from pathlib import Path
import os,sys
sys.stdout.buffer.write(b'PGDMP unit fixture')
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
       'database' if len(a)>2 and '/db/' in a[2] else
       'manifest' if len(a)>2 and '/manifests/' in a[2] else '')
if mode=='fail-photo-check' and a[0]=='check' and phase=='photos': sys.exit(23)
if mode=='fail-db-copy' and a[0]=='copyto' and phase=='database': sys.exit(24)
if mode=='fail-manifest-check' and a[0]=='check' and phase=='manifest': sys.exit(25)
result=subprocess.run([os.environ['RCLONE_TEST_REAL_BIN']]+a).returncode
if result==0 and mode=='late-upload' and a[0]=='copy' and phase=='photos':
    (Path(os.environ['SALES_PHOTO_STORAGE_ROOT'])/'ab/late.png').write_bytes(b'after snapshot')
if result==0 and mode=='changed-photo' and a[0]=='check' and '/manifests/' in a[2]:
    (Path(os.environ['SALES_PHOTO_STORAGE_ROOT'])/'ab/photo.png').write_bytes(b'X'*len((Path(os.environ['SALES_PHOTO_STORAGE_ROOT'])/'ab/photo.png').read_bytes()))
sys.exit(result)
""",
        )

    def write_executable(self, name, content):
        path = self.bin / name
        path.write_text(content)
        path.chmod(0o755)

    def run_backup(self, success=True, verify=False, **env):
        result = subprocess.run(
            ["bash", str(SCRIPT), "--env-file", str(self.settings)] + (["--verify-photos"] if verify else []),
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
        return list((self.backup / "completed").glob("*.json"))

    def test_round_trip_completion_and_no_local_photo_replica(self):
        self.run_backup()
        completion = json.loads(self.completed()[0].read_text())
        self.assertEqual(len(completion["photos"]), 1)
        self.assertFalse((self.backup / "photos").exists())
        remote_root = self.remote / "green-house-backups"
        self.assertFalse((remote_root / "photos/ab/.upload-pending.tmp").exists())
        self.assertEqual((remote_root / "photos/ab/photo.png").read_bytes(), self.photo.read_bytes())
        self.assertEqual(
            hashlib.sha256((remote_root / completion["database"]["path"]).read_bytes()).hexdigest(),
            completion["database"]["sha256"],
        )
        calls = [json.loads(line) for line in self.log.read_text().splitlines()]
        uploads = [call for call in calls if call[0] in ["copy", "copyto"]]
        self.assertEqual(uploads[-1][0], "copyto")
        self.assertIn("/db/", uploads[-1][2])
        self.assertTrue(uploads[-1][2].endswith(".dump.gz"))
        self.assertFalse((remote_root / "completed").exists())
        with gzip.open(remote_root / completion["database"]["path"], "rb") as stream:
            self.assertEqual(stream.read(), b"PGDMP unit fixture")
        self.assertIsNone(completion["photos"][0]["sha256"])
        self.assertEqual(completion["photoVerification"], "size")
        photo_checks = [call for call in calls if call[0]=="check" and call[2].endswith("/photos")]
        self.assertIn("--size-only", photo_checks[0])
        self.assertNotIn("--download", photo_checks[0])
        self.assertTrue(list((self.backup / "completed").glob("*.json")))

    def test_incremental_copy_preserves_remote_only_files_and_old_dumps(self):
        self.run_backup()
        old = self.remote / "green-house-backups/photos/old-only.png"
        old.write_bytes(b"needed by an older database")
        (self.source / "ab/new.png").write_bytes(b"new photo")
        self.run_backup()
        self.assertEqual(len(self.completed()), 2)
        self.assertEqual(len(list((self.backup).glob("*.dump.gz"))), 2)
        self.assertEqual(old.read_bytes(), b"needed by an older database")

    def test_daily_skips_existing_and_precise_replaces_with_history(self):
        self.run_backup()
        original = self.photo.read_bytes()
        replacement = b"X" * len(original)
        self.photo.write_bytes(replacement)
        self.run_backup()
        remote_photo = self.remote / "green-house-backups/photos/ab/photo.png"
        self.assertEqual(remote_photo.read_bytes(), original)
        self.run_backup(verify=True)
        precise = next(json.loads(p.read_text()) for p in self.completed() if json.loads(p.read_text())["photoVerification"] == "checksums")
        history = self.remote / "green-house-backups" / precise["photoHistory"] / "ab/photo.png"
        self.assertEqual(history.read_bytes(), original)
        self.assertEqual(remote_photo.read_bytes(), replacement)
        self.assertEqual(precise["photos"][0]["sha256"], hashlib.sha256(replacement).hexdigest())
        self.assertEqual(len(self.completed()), 3)

    def test_failure_never_publishes_completion_and_keeps_local_dump(self):
        for mode in ["fail-photo-check", "fail-db-copy", "fail-manifest-check"]:
            with self.subTest(mode=mode):
                self.run_backup(success=False, RCLONE_TEST_MODE=mode)
                self.assertEqual(self.completed(), [])
                self.assertEqual(list((self.backup / "completed").glob("*.json")), [])
        self.assertEqual(len(list((self.backup).glob("*.dump.gz"))), 3)
        self.assertEqual(list((self.backup).glob("*.partial")), [])

    def test_upload_after_snapshot_does_not_invalidate_the_dump(self):
        self.run_backup(RCLONE_TEST_MODE="late-upload")
        self.assertTrue((self.source / "ab/late.png").exists())
        completion = json.loads(self.completed()[0].read_text())
        self.assertEqual([p["key"] for p in completion["photos"]], ["ab/photo.png"])
        self.assertFalse((self.remote / "green-house-backups/photos/ab/late.png").exists())

    def test_changed_photo_before_completion_is_rejected(self):
        self.run_backup(success=False, verify=True, RCLONE_TEST_MODE="changed-photo")
        self.assertEqual(self.completed(), [])

    def test_failed_dump_removes_partial_and_does_not_upload(self):
        self.run_backup(success=False, TEST_DUMP_FAIL="1")
        self.assertFalse(self.log.exists())
        self.assertEqual(list(self.backup.glob("*.dump.gz")), [])
        self.assertEqual(list(self.backup.glob("*.partial")), [])

    def test_empty_photo_store_is_a_valid_backup(self):
        self.photo.unlink()
        self.run_backup()
        self.assertEqual(json.loads(self.completed()[0].read_text())["photos"], [])

    def test_invalid_remote_fails_before_dump_or_upload(self):
        for remote in ["", "testdrive:", "testdrive:/", "testdrive:../outside"]:
            with self.subTest(remote=remote):
                self.run_backup(success=False, RCLONE_BACKUP_REMOTE=remote)
                self.assertFalse(self.log.exists())
                self.assertEqual(list(self.backup.glob("*.dump.gz")), [])

    def test_relative_or_overlapping_paths_fail_before_dump(self):
        for directory in ["relative-backups", str(self.source / "nested")]:
            with self.subTest(directory=directory):
                self.run_backup(success=False, LOCAL_BACKUP_DIR=directory)
                self.assertFalse(self.log.exists())
                self.assertEqual(list(self.backup.glob("*.dump.gz")), [])

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

    def test_local_mtime_rule_does_not_delete_remote_or_photos(self):
        self.backup.mkdir()
        expired = self.backup / "old.dump.gz"
        boundary = self.backup / "seven-point-nine-days.dump.gz"
        expired.write_bytes(b"old local dump")
        boundary.write_bytes(b"still within find mtime +7")
        os.utime(expired, (time.time()-8.1*86400,)*2)
        os.utime(boundary, (time.time()-7.9*86400,)*2)
        os.utime(self.photo, (time.time()-100*86400,)*2)
        cloud_db = self.remote / "green-house-backups/db/old.dump.gz"
        cloud_db.parent.mkdir(parents=True)
        cloud_db.write_bytes(b"never age-delete Drive DB")
        self.run_backup()
        self.assertFalse(expired.exists())
        self.assertTrue(boundary.exists())
        self.assertTrue(self.photo.exists())
        self.assertEqual(cloud_db.read_bytes(), b"never age-delete Drive DB")

    def test_failure_keeps_expired_local_dump(self):
        self.backup.mkdir()
        old = self.backup / "old.dump.gz"
        old.write_bytes(b"keep until a successful backup")
        os.utime(old, (time.time()-10*86400,)*2)
        self.run_backup(success=False, RCLONE_TEST_MODE="fail-photo-check")
        self.assertTrue(old.exists())

    def test_existing_cron_entrypoint_forwards_env_file_and_verify_flag(self):
        env = dict(self.env, GREENHOUSE_BACKUP_REPO=str(ROOT))
        # Exercise settings from the file, not duplicate exported configuration.
        for key in ["SALES_PHOTO_STORAGE_ROOT", "LOCAL_BACKUP_DIR", "RCLONE_BACKUP_REMOTE", "RCLONE_CONFIG", "GREENHOUSE_OPERATION_LOCK_FILE"]:
            env.pop(key)
        result = subprocess.run(
            ["bash", str(ROOT / "deploy/backup-greenhouse-db.sh"), "--env-file", str(self.settings), "--verify-photos"],
            env=env, capture_output=True, text=True, timeout=90,
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        completion = json.loads(self.completed()[0].read_text())
        self.assertEqual(completion["photoVerification"], "checksums")
        self.assertIsNotNone(completion["photos"][0]["sha256"])

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
subprocess.run(['docker','exec','--user',str(os.getuid())+':'+str(os.getgid()),os.environ['TEST_POSTGRES_CONTAINER'],'pg_dump','-U','postgres','-Fc','postgres'],stdout=sys.stdout.buffer,check=True)
""",
            )
            self.run_backup()
            completed = json.loads(self.completed()[0].read_text())
            restored = self.root / "restored"
            subprocess.run(
                [RCLONE, "copy", "testdrive:green-house-backups/photos", str(restored)],
                env=self.env, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE,
            )
            subprocess.run(["docker", "exec", name, "createdb", "-U", "postgres", "restored"], check=True)
            cloud_dump = self.remote / "green-house-backups" / completed["database"]["path"]
            with gzip.open(cloud_dump, "rb") as stream:
                restore = subprocess.Popen(
                    ["docker", "exec", "-i", "--user", f"{os.getuid()}:{os.getgid()}", name,
                     "pg_restore", "-U", "postgres", "--exit-on-error", "-d", "restored"],
                    stdin=subprocess.PIPE,
                )
                shutil.copyfileobj(stream, restore.stdin)
                restore.stdin.close()
                self.assertEqual(restore.wait(timeout=60), 0)
            result = subprocess.check_output(
                ["docker", "exec", name, "psql", "-U", "postgres", "-d", "restored",
                 "-Atc", "select sha256 from photo_refs where path='ab/photo.png'"], text=True,
            ).strip()
            self.assertEqual(hashlib.sha256((restored / "ab/photo.png").read_bytes()).hexdigest(), result)
        finally:
            subprocess.run(["docker", "stop", name], check=True, stdout=subprocess.DEVNULL)


if __name__ == "__main__":
    unittest.main()
