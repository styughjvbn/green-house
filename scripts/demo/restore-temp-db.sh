#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
TEMP_DB_NAME="${SANITIZE_DB_NAME:-greenhouse_demo_sanitize_tmp}"
MIGRATION_DIR="${PROJECT_ROOT}/backend/src/main/resources/db/migration"

fail() {
  echo "[ERROR] $*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Command not found: $1"
}

validate_target() {
  [[ "${TEMP_DB_NAME}" == "greenhouse_demo_sanitize_tmp" ]] \
    || fail "SANITIZE_DB_NAME must be exactly greenhouse_demo_sanitize_tmp"
  [[ -n "${SANITIZE_DB_ADMIN_URL:-}" ]] || fail "SANITIZE_DB_ADMIN_URL is required"
  [[ -n "${SANITIZE_DB_URL:-}" ]] || fail "SANITIZE_DB_URL is required"
  [[ -n "${SANITIZE_FLYWAY_URL:-}" ]] || fail "SANITIZE_FLYWAY_URL is required"
  [[ -n "${SANITIZE_FLYWAY_USER:-}" ]] || fail "SANITIZE_FLYWAY_USER is required"
  [[ -n "${SANITIZE_FLYWAY_PASSWORD:-}" ]] || fail "SANITIZE_FLYWAY_PASSWORD is required"
  [[ "${SANITIZE_RESTORE_CONFIRM:-}" == "${TEMP_DB_NAME}" ]] \
    || fail "Set SANITIZE_RESTORE_CONFIRM=${TEMP_DB_NAME}"
}

main() {
  [[ $# -eq 1 ]] || fail "Usage: $0 <production.dump|production.dump.gz>"

  require_command createdb
  require_command dropdb
  require_command flyway
  require_command gzip
  require_command pg_restore
  require_command psql
  validate_target

  local source_dump="$1"
  [[ -f "${source_dump}" ]] || fail "Backup not found: ${source_dump}"

  dropdb --if-exists --force --maintenance-db="${SANITIZE_DB_ADMIN_URL}" "${TEMP_DB_NAME}"
  createdb --maintenance-db="${SANITIZE_DB_ADMIN_URL}" "${TEMP_DB_NAME}"

  case "${source_dump}" in
    *.dump)
      pg_restore --dbname="${SANITIZE_DB_URL}" --no-owner --no-privileges \
        --exit-on-error "${source_dump}"
      ;;
    *.dump.gz)
      gzip -cd "${source_dump}" | pg_restore --dbname="${SANITIZE_DB_URL}" \
        --no-owner --no-privileges --exit-on-error
      ;;
    *)
      fail "Only .dump and .dump.gz backups are supported"
      ;;
  esac

  [[ "$(psql "${SANITIZE_DB_URL}" -Atqc 'SELECT current_database()')" == "${TEMP_DB_NAME}" ]] \
    || fail "SANITIZE_DB_URL does not target ${TEMP_DB_NAME}"

  FLYWAY_URL="${SANITIZE_FLYWAY_URL}" \
  FLYWAY_USER="${SANITIZE_FLYWAY_USER}" \
  FLYWAY_PASSWORD="${SANITIZE_FLYWAY_PASSWORD}" \
  FLYWAY_LOCATIONS="filesystem:${MIGRATION_DIR}" \
    flyway migrate

  echo "Temporary database restored and migrated: ${TEMP_DB_NAME}"
}

main "$@"
