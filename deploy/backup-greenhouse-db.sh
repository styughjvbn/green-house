#!/usr/bin/env bash
# Install at the EXISTING cron entrypoint /opt/green-house/scripts/backup-greenhouse-db.sh.
set -euo pipefail
export PATH="${PATH:-/usr/local/bin:/usr/bin:/bin}:/usr/local/bin"
backup_repo="${GREENHOUSE_BACKUP_REPO:-/home/sjw/projects/green-house}"
exec /bin/bash "$backup_repo/scripts/backup-with-photos.sh" "$@"
