#!/usr/bin/env python3
"""Load literal backup settings without sourcing executable shell code."""
import argparse
import os
from pathlib import Path
import re
import shlex


ALLOWED = {
    "PGHOST", "PGPORT", "PGDATABASE", "PGUSER", "PGPASSWORD", "PGPASSFILE",
    "PGSERVICE", "PGSERVICEFILE", "SALES_PHOTO_STORAGE_ROOT", "LOCAL_BACKUP_DIR",
    "RCLONE_BACKUP_REMOTE", "RCLONE_CONFIG", "GREENHOUSE_OPERATION_LOCK_FILE",
}


def load(path, environment):
    result = dict(environment)
    try:
        lines = path.read_text().splitlines()
    except OSError:
        raise ValueError(f"Cannot read backup settings: {path}") from None
    for number, line in enumerate(lines, 1):
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("export "):
            line = line[7:].lstrip()
        key, separator, value = line.partition("=")
        key = key.strip()
        if not separator or not re.fullmatch(r"[A-Z_][A-Z0-9_]*", key) or key not in ALLOWED:
            raise ValueError(f"Unsupported setting name at {path}:{number}")
        try:
            words = shlex.split(value, comments=True)
        except ValueError:
            raise ValueError(f"Invalid quoted value at {path}:{number}") from None
        if len(words) > 1:
            raise ValueError(f"Quote values containing spaces at {path}:{number}")
        # Exported values win. No interpolation, command substitution or eval.
        result.setdefault(key, words[0] if words else "")
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("script", type=Path)
    parser.add_argument(
        "--env-file", type=Path,
        default=Path(os.environ.get("BACKUP_ENV_FILE", Path(__file__).resolve().parents[1] / "deploy/backup.env")),
    )
    parser.add_argument("--verify-photos", action="store_true")
    args = parser.parse_args()
    try:
        environment = load(args.env_file, os.environ)
    except ValueError as error:
        parser.error(str(error))
    environment["GREENHOUSE_BACKUP_ENV_LOADED"] = "1"
    environment["GREENHOUSE_BACKUP_VERIFY_PHOTOS"] = "1" if args.verify_photos else "0"
    os.execvpe("/bin/bash", ["/bin/bash", str(args.script), "--configured"], environment)


if __name__ == "__main__":
    main()
