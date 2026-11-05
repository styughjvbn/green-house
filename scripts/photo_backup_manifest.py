#!/usr/bin/env python3
"""Record/verify immutable photo files without keeping a local photo replica."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import stat


def inspect_file(path, checksums=True):
    descriptor = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    with os.fdopen(descriptor, "rb") as stream:
        info = os.fstat(stream.fileno())
        if not stat.S_ISREG(info.st_mode):
            raise ValueError(f"Not a regular backup file: {path}")
        if not checksums:
            return info.st_size, None
        size = 0
        checksum = hashlib.sha256()
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            size += len(chunk)
            checksum.update(chunk)
        return size, checksum.hexdigest()


def digest(path):
    return inspect_file(path, checksums=True)


def raise_error(error):
    raise error


def entries(source, checksums):
    for folder, directories, files in os.walk(source, onerror=raise_error):
        for name in directories + files:
            path = Path(folder) / name
            if path.is_symlink():
                raise ValueError(f"Symlinks are not allowed in photo storage: {path}")
        for name in sorted(files):
            if name.startswith(".upload-") and name.endswith(".tmp"):
                continue
            path = Path(folder) / name
            relative = path.relative_to(source).as_posix()
            if "\n" in relative or "\r" in relative:
                raise ValueError("Photo names must not contain line separators")
            size, checksum = inspect_file(path, checksums)
            yield {"key": relative, "bytes": size, "sha256": checksum}


def create(source, output, database, backup_id, verify_photos=False):
    photos = sorted(entries(source, verify_photos), key=lambda entry: entry["key"])
    output.mkdir(mode=0o700)
    (output / "files.txt").write_text("".join(p["key"] + "\n" for p in photos))
    (output / "photos.sha256").write_text(
        "".join(p["sha256"] + "  " + p["key"] + "\n" for p in photos if p["sha256"])
    )
    (output / "database-files.txt").write_text(database.name + "\n")
    size, checksum = digest(database)
    (output / "database.sha256").write_text(checksum + "  " + database.name + "\n")
    manifest = {
        "schemaVersion": 1,
        "backupId": backup_id,
        "database": {
            "path": "db/" + database.name,
            "bytes": size,
            "sha256": checksum,
        },
        "photoRoot": "photos",
        "photoVerification": "checksums" if verify_photos else "size",
        "photoHistory": "replaced/" + backup_id if verify_photos else None,
        "photos": photos,
    }
    (output / "backup.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n"
    )


def verify(source, output):
    manifest = json.loads((output / "backup.json").read_text())
    for entry in manifest["photos"]:
        key = Path(entry["key"])
        if key.is_absolute() or ".." in key.parts:
            raise ValueError("Unsafe photo key")
        path = source
        for part in key.parts:
            path = path / part
            if path.is_symlink():
                raise ValueError(f"Symlink encountered: {path}")
        if inspect_file(path, bool(entry.get("sha256"))) != (entry["bytes"], entry.get("sha256")):
            raise ValueError(f"Photo changed since the database dump: {entry['key']}")
    checked = sum(bool(p.get("sha256")) for p in manifest["photos"])
    print(f"verified={len(manifest['photos'])} checksums={checked}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=["create", "verify"])
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--database", type=Path)
    parser.add_argument("--backup-id")
    parser.add_argument("--verify-photos", action="store_true")
    args = parser.parse_args()
    if args.command == "create":
        if not args.database or not args.backup_id:
            parser.error("create requires --database and --backup-id")
        create(args.source, args.output, args.database, args.backup_id, args.verify_photos)
    else:
        verify(args.source, args.output)


if __name__ == "__main__":
    main()
