#!/usr/bin/env bash
# Run pg_dump using libpq PG* variables / PGSERVICE. Never copy a live PostgreSQL data directory.
set -Eeuo pipefail
umask 077
if [[ $# != 1 || -z "${SALES_PHOTO_STORAGE_ROOT:-}" ]]; then
  echo 'Usage: SALES_PHOTO_STORAGE_ROOT=/absolute/photo/root PGSERVICE=greenhouse scripts/backup-with-photos.sh /backup/greenhouse' >&2
  exit 2
fi
for executable in pg_dump rsync realpath; do command -v "$executable" >/dev/null; done
source_root="$(realpath "$SALES_PHOTO_STORAGE_ROOT")"
[[ -d "$source_root" ]]
mkdir -p "$1/database" "$1/photos"
backup_root="$(realpath "$1")"
if [[ "$backup_root/" == "$source_root/"* || "$source_root/" == "$backup_root/"* ]]; then
  echo 'Photo storage and backup directories must not contain one another.' >&2
  exit 2
fi
backup_id="$(date -u +%Y%m%dT%H%M%SZ)-$$"
pending="$backup_root/database/.$backup_id.partial"
trap 'rm -f "$pending"' EXIT
pg_dump --format=custom --file="$pending"
# Files are immutable and published before DB commit. Copy after the consistent dump.
# Keep all existing objects: older daily DB snapshots can still refer to them.
# Recheck checksums so corrupted backup objects can be repaired.
# Retain replaced bytes as well: a damaged source must not silently erase the last good copy.
rsync -rt --checksum --backup --backup-dir="$backup_root/replaced/$backup_id" \
  --exclude='.upload-*.tmp' "$source_root/" "$backup_root/photos/"
mv "$pending" "$backup_root/database/$backup_id.dump"
echo "Backup complete: $backup_root/database/$backup_id.dump"
