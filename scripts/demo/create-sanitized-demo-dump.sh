#!/usr/bin/env bash
set -euo pipefail
umask 077

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OPERATION_LOCK_FILE="${GREENHOUSE_OPERATION_LOCK_FILE:-/tmp/green-house-operation.lock}"
PRODUCTION_DB_NAME="${PRODUCTION_DB_NAME:-greenhouse}"

fail() { echo "[ERROR] $*" >&2; exit 1; }
require_command() { command -v "$1" >/dev/null 2>&1 || fail "Command not found: $1"; }

main() {
  [[ $# -eq 1 ]] || fail "Usage: $0 <sanitized-output.dump>"
  require_command flock
  require_command mktemp
  require_command pg_dump
  require_command pg_restore
  require_command psql
  [[ "${PRODUCTION_DB_NAME}" == "greenhouse" ]] || fail "PRODUCTION_DB_NAME must be exactly greenhouse"
  [[ "${DEMO_SOURCE_DUMP_CONFIRM:-}" == "greenhouse" ]] || fail "Set DEMO_SOURCE_DUMP_CONFIRM=greenhouse"
  [[ -n "${PRODUCTION_DB_URL:-}" ]] || fail "PRODUCTION_DB_URL is required"
  [[ "$(psql "${PRODUCTION_DB_URL}" -Atqc "SELECT current_database()||':'||current_user")" == "greenhouse:greenhouse" ]] \
    || fail "PRODUCTION_DB_URL must target greenhouse as role greenhouse"

  if [[ "${GREENHOUSE_OPERATION_LOCK_HELD:-false}" != "true" ]]; then
    exec 9>"${OPERATION_LOCK_FILE}"
    flock -n 9 || fail "Another deployment or demo refresh is running: ${OPERATION_LOCK_FILE}"
  fi

  local output="$1" raw_dir raw_dump
  [[ "${output}" == *.dump ]] || fail "Output must use the .dump extension"
  [[ ! -e "${output}" && ! -e "${output}.sha256" ]] || fail "Output already exists: ${output}"
  mkdir -p "$(dirname "${output}")"
  raw_dir="$(mktemp -d "${DEMO_SOURCE_TEMP_DIR:-/tmp}/green-house-demo-source.XXXXXX")"
  chmod 700 "${raw_dir}"
  raw_dump="${raw_dir}/greenhouse-production.dump"
  cleanup() { rm -f -- "${raw_dump}"; rmdir "${raw_dir}" 2>/dev/null || true; }
  trap cleanup EXIT

  pg_dump "${PRODUCTION_DB_URL}" --format=custom --file="${raw_dump}"
  pg_restore --list "${raw_dump}" >/dev/null
  "${SCRIPT_DIR}/docker-sanitize.sh" "${raw_dump}" "${output}"
  cleanup
  trap - EXIT
  echo "Production source dump removed after sanitization: ${raw_dump}"
}

main "$@"
