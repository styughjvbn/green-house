#!/usr/bin/env bash
# Keep DB dumps locally; copy immutable photos directly to rclone storage.
set -Eeuo pipefail
umask 077

fail() { echo "[ERROR] $*" >&2; exit 1; }
if [[ $# != 1 || -z "${SALES_PHOTO_STORAGE_ROOT:-}" || -z "${RCLONE_BACKUP_REMOTE:-}" ]]; then
  echo 'Usage: SALES_PHOTO_STORAGE_ROOT=/absolute/photo/root RCLONE_BACKUP_REMOTE=remote:green-house PGSERVICE=greenhouse bash scripts/backup-with-photos.sh /opt/green-house/backups/local' >&2
  exit 2
fi
for executable in pg_dump rclone python3 realpath flock cmp; do
  command -v "$executable" >/dev/null || fail "Required command not found: $executable"
done
[[ "$RCLONE_BACKUP_REMOTE" =~ ^[a-zA-Z0-9_-]+:[^/].* ]] || fail 'Use a named rclone remote and folder: remote:green-house'
remote="${RCLONE_BACKUP_REMOTE%/}"
remote_path="${remote#*:}"
[[ ! "$remote_path" =~ (^|/)\.\.?(/|$) ]] || fail 'Remote folder must not contain . or .. path components'
source_root="$(realpath -e "$SALES_PHOTO_STORAGE_ROOT")"
[[ -d "$source_root" && -r "$source_root" && -x "$source_root" ]] || fail 'Photo source must be a readable directory'
mkdir -p "$1"
backup_root="$(realpath -e "$1")"
if [[ "$backup_root/" == "$source_root/"* || "$source_root/" == "$backup_root/"* ]]; then
  fail 'Photo storage and backup directories must not contain one another.'
fi
script_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec 9>"${GREENHOUSE_OPERATION_LOCK_FILE:-/tmp/green-house-operation.lock}"
flock -n 9 || fail 'Another deployment, backup or demo refresh is running'
backup_id="$(date -u +%Y%m%dT%H%M%SZ)-$(python3 -c 'import uuid; print(uuid.uuid4().hex)')"
mkdir -p "$backup_root/database" "$backup_root/manifests" "$backup_root/completed"
pending="$backup_root/database/.$backup_id.partial"
database="$backup_root/database/$backup_id.dump"
manifest="$backup_root/manifests/$backup_id"
trap 'rm -f "$pending"' EXIT
pg_dump --format=custom --file="$pending"
# Keep a valid local dump even if remote backup fails.
mv "$pending" "$database"

# Snapshot the file list AFTER the dump: files are published before DB commit.
# Later uploads can be omitted. Cleanup is forbidden while this backup runs.
# Stage only metadata locally, never another photo directory.
python3 "$script_root/photo_backup_manifest.py" create "$source_root" "$manifest" \
  --database "$database" --backup-id "$backup_id"
rclone mkdir "$remote/photos"
rclone copy "$source_root" "$remote/photos" \
  --files-from-raw "$manifest/files.txt" --checksum --immutable
# Streaming comparison does not create a local photo replica. Check only the
# captured list; remote-only files remain available to older DB snapshots.
rclone check "$source_root" "$remote/photos" \
  --files-from-raw "$manifest/files.txt" --one-way --download
rclone copyto "$database" "$remote/database/$backup_id.dump" --checksum --immutable
rclone check "$backup_root/database" "$remote/database" \
  --files-from-raw "$manifest/database-files.txt" --one-way --download
rclone copy "$manifest" "$remote/manifests/$backup_id" --checksum --immutable
rclone check "$manifest" "$remote/manifests/$backup_id" --one-way --download
python3 "$script_root/photo_backup_manifest.py" verify "$source_root" "$manifest"

# Only a last-published completion record identifies a restorable cloud backup.
rclone copyto "$manifest/backup.json" "$remote/completed/$backup_id.json" --checksum --immutable
rclone cat "$remote/completed/$backup_id.json" | cmp -s "$manifest/backup.json" -
cp "$manifest/backup.json" "$backup_root/completed/$backup_id.json"
echo "Backup complete: $database; remote completion: $remote/completed/$backup_id.json"
