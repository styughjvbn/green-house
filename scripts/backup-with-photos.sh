#!/usr/bin/env bash
# One existing host cron: DB dump + photo backup, DB uploaded last.
set -Eeuo pipefail
umask 077
script_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [[ "${1:-}" != --configured ]]; then
  exec python3 "$script_root/backup_environment.py" "${BASH_SOURCE[0]}" "$@"
fi
[[ "$#" == 1 && "${GREENHOUSE_BACKUP_ENV_LOADED:-}" == 1 ]] || exit 2
fail() { echo "[ERROR] $*" >&2; exit 1; }
for setting in SALES_PHOTO_STORAGE_ROOT LOCAL_BACKUP_DIR RCLONE_BACKUP_REMOTE; do
  [[ -n "${!setting:-}" ]] || fail "Missing backup setting: $setting"
done
[[ "$SALES_PHOTO_STORAGE_ROOT" == /* && "$LOCAL_BACKUP_DIR" == /* ]] || fail 'Photo and local DB directories must be absolute paths'
for executable in pg_dump gzip rclone python3 realpath flock find; do
  command -v "$executable" >/dev/null || fail "Required command not found: $executable"
done
[[ "$RCLONE_BACKUP_REMOTE" =~ ^[a-zA-Z0-9_-]+:[^/].* ]] || fail 'Use a named remote and folder: remote:green-house-backups'
remote="${RCLONE_BACKUP_REMOTE%/}"
remote_path="${remote#*:}"
[[ ! "$remote_path" =~ (^|/)\.\.?(/|$) ]] || fail 'Remote folder must not contain . or .. path components'
source_root="$(realpath -e "$SALES_PHOTO_STORAGE_ROOT")"
[[ -d "$source_root" && -r "$source_root" && -x "$source_root" ]] || fail 'Photo source must be readable'
mkdir -p "$LOCAL_BACKUP_DIR"
backup_root="$(realpath -e "$LOCAL_BACKUP_DIR")"
[[ "$backup_root" != / && "$source_root" != / ]] || fail 'Filesystem root is not a backup/photo directory'
if [[ "$backup_root/" == "$source_root/"* || "$source_root/" == "$backup_root/"* ]]; then
  fail 'Photo storage and backup directories must not contain one another.'
fi
exec 9>"${GREENHOUSE_OPERATION_LOCK_FILE:-/tmp/green-house-operation.lock}"
flock -n 9 || fail 'Another deployment, backup or demo refresh is running'
backup_id="$(date -u +%Y%m%d_%H%M%S)-$(python3 -c 'import uuid; print(uuid.uuid4().hex)')"
mkdir -p "$backup_root/manifests" "$backup_root/completed"
database="$backup_root/green-house_$backup_id.dump.gz"
pending="$backup_root/.$backup_id.partial"
manifest="$backup_root/manifests/$backup_id"
trap 'rm -f "$pending"' EXIT
pg_dump --no-password --format=custom | gzip -c > "$pending"
gzip -t "$pending"
mv "$pending" "$database"

# Files are immutable and published before DB commit. Capture after the dump;
# later uploads belong to the next backup. No cleanup may run during this job.
manifest_options=()
if [[ "$GREENHOUSE_BACKUP_VERIFY_PHOTOS" == 1 ]]; then
  manifest_options+=(--verify-photos)
fi
python3 "$script_root/photo_backup_manifest.py" create "$source_root" "$manifest" \
  --database "$database" --backup-id "$backup_id" "${manifest_options[@]}"
rclone mkdir "$remote/photos"
if [[ "$GREENHOUSE_BACKUP_VERIFY_PHOTOS" == 1 ]]; then
  # Preserve the previous remote bytes BEFORE replacing a mismatching object.
  rclone copy "$source_root" "$remote/photos" --files-from-raw "$manifest/files.txt" \
    --checksum --backup-dir "$remote/replaced/$backup_id"
  rclone check "$source_root" "$remote/photos" \
    --files-from-raw "$manifest/files.txt" --one-way --download
else
  # Daily: upload missing files only, never read/hash all existing photo content.
  rclone copy "$source_root" "$remote/photos" \
    --files-from-raw "$manifest/files.txt" --ignore-existing
  rclone check "$source_root" "$remote/photos" \
    --files-from-raw "$manifest/files.txt" --one-way --size-only
fi
python3 "$script_root/photo_backup_manifest.py" verify "$source_root" "$manifest"
rclone copy "$manifest" "$remote/manifests/$backup_id" --checksum --immutable
rclone check "$manifest" "$remote/manifests/$backup_id" --one-way --download
# Recheck the captured source before publishing the DB. Do not mutate remote
# metadata after the dump: db/<name>.dump.gz is always the last uploaded object.
python3 "$script_root/photo_backup_manifest.py" verify "$source_root" "$manifest"
rclone copyto "$database" "$remote/db/$(basename "$database")" --checksum --immutable
rclone check "$backup_root" "$remote/db" \
  --files-from-raw "$manifest/database-files.txt" --one-way --download
cp "$manifest/backup.json" "$backup_root/completed/$backup_id.json"
# Preserve the existing local DB age rule. Never delete remote DBs or photos.
find "$backup_root" -maxdepth 1 -type f -name '*.dump.gz' -mtime +7 -delete
echo "Backup complete: $database; Drive DB: $remote/db/$(basename "$database"); verify-photos=$GREENHOUSE_BACKUP_VERIFY_PHOTOS"
