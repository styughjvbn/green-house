#!/usr/bin/env bash
set -euo pipefail
umask 077

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUTPUT_DIR="${DEMO_SANITIZED_DUMP_DIR:?DEMO_SANITIZED_DUMP_DIR is required}"
KEEP_COUNT="${DEMO_SANITIZED_DUMP_KEEP:-3}"
LOCK_FILE="${GREENHOUSE_OPERATION_LOCK_FILE:-/tmp/green-house-operation.lock}"

[[ "${KEEP_COUNT}" =~ ^[23]$ ]] || { echo "[ERROR] DEMO_SANITIZED_DUMP_KEEP must be 2 or 3" >&2; exit 1; }
[[ "${DEMO_REFRESH_CONFIRM:-}" == "greenhouse_demo:greenhouse_demo_next:greenhouse_demo_prev" ]] \
  || { echo "[ERROR] DEMO_REFRESH_CONFIRM is required" >&2; exit 1; }
mkdir -p "${OUTPUT_DIR}"
exec 9>"${LOCK_FILE}"
flock -n 9 || { echo "[ERROR] Another deployment or demo refresh is running: ${LOCK_FILE}" >&2; exit 1; }
export GREENHOUSE_OPERATION_LOCK_HELD=true

timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
dump="${OUTPUT_DIR}/greenhouse_demo_sanitized_${timestamp}.dump"
"${SCRIPT_DIR}/create-sanitized-demo-dump.sh" "${dump}"
"${SCRIPT_DIR}/refresh-demo-db.sh" "${dump}"

mapfile -t old_dumps < <(find "${OUTPUT_DIR}" -maxdepth 1 -type f \
  -name 'greenhouse_demo_sanitized_*.dump' -printf '%T@ %p\n' | sort -nr | tail -n +$((KEEP_COUNT + 1)) | cut -d' ' -f2-)
for old_dump in "${old_dumps[@]}"; do
  [[ "${old_dump}" == "${OUTPUT_DIR}"/greenhouse_demo_sanitized_*.dump ]] || continue
  rm -f -- "${old_dump}" "${old_dump}.sha256"
done

echo "Scheduled demo refresh completed: ${dump}"
