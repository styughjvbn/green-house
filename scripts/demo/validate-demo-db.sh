#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
ALLOWLIST="${SCRIPT_DIR}/schema-allowlist.tsv"
MIGRATION_DIR="${PROJECT_ROOT}/backend/src/main/resources/db/migration"
FLYWAY_IMAGE="redgate/flyway:11"

fail() { echo "[ERROR] $*" >&2; exit 1; }
require_command() { command -v "$1" >/dev/null 2>&1 || fail "Command not found: $1"; }

schema_allowlist_values() {
  local values="" separator="" table_name fingerprint
  while IFS=$'\t' read -r table_name fingerprint; do
    [[ "${table_name}" =~ ^[a-z_][a-z0-9_]*$ ]] || fail "Invalid allowlisted table: ${table_name}"
    [[ "${fingerprint}" =~ ^[a-f0-9]{32}$ ]] || fail "Invalid schema fingerprint: ${table_name}"
    values+="${separator}('${table_name}','${fingerprint}')"
    separator=","
  done < "${ALLOWLIST}"
  printf '%s' "${values}"
}

validate_flyway_history() {
  (
    export FLYWAY_URL="${DEMO_VALIDATE_FLYWAY_URL}"
    export FLYWAY_USER="${DEMO_VALIDATE_FLYWAY_USER}"
    export FLYWAY_PASSWORD="${DEMO_VALIDATE_FLYWAY_PASSWORD}"
    export FLYWAY_LOCATIONS="filesystem:/flyway/sql"
    docker run --rm --network host \
      --env FLYWAY_URL \
      --env FLYWAY_USER \
      --env FLYWAY_PASSWORD \
      --env FLYWAY_LOCATIONS \
      --volume "${MIGRATION_DIR}:/flyway/sql:ro" \
      "${FLYWAY_IMAGE}" validate
  )
}

main() {
  require_command docker
  require_command find
  require_command psql
  [[ -n "${DEMO_VALIDATE_DB_URL:-}" ]] || fail "DEMO_VALIDATE_DB_URL is required"
  [[ -n "${DEMO_VALIDATE_FLYWAY_URL:-}" ]] || fail "DEMO_VALIDATE_FLYWAY_URL is required"
  [[ -n "${DEMO_VALIDATE_FLYWAY_USER:-}" ]] || fail "DEMO_VALIDATE_FLYWAY_USER is required"
  [[ -n "${DEMO_VALIDATE_FLYWAY_PASSWORD:-}" ]] || fail "DEMO_VALIDATE_FLYWAY_PASSWORD is required"
  [[ "$(psql "${DEMO_VALIDATE_DB_URL}" -Atqc 'SELECT current_database()')" == "${DEMO_VALIDATE_DB_NAME:?DEMO_VALIDATE_DB_NAME is required}" ]] \
    || fail "Validation URL does not target ${DEMO_VALIDATE_DB_NAME}"

  validate_flyway_history

  local latest applied allowlist_values files=()
  latest="$(find "${MIGRATION_DIR}" -maxdepth 1 -type f -name 'V*__*.sql' -printf '%f\n' \
    | sed -E 's/^V([0-9]+)__.*/\1/' | sort -n | tail -1)"
  applied="$(psql "${DEMO_VALIDATE_DB_URL}" -Atqc \
    "SELECT version FROM flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1")"
  [[ "${applied}" == "${latest}" ]] || fail "Flyway version ${applied:-<none>} does not match code V${latest}"

  allowlist_values="$(schema_allowlist_values)"
  while IFS= read -r file; do files+=(-f "${file}"); done < <(
    find "${SCRIPT_DIR}/validate" -maxdepth 1 -name '*.sql' -print | sort
  )
  psql "${DEMO_VALIDATE_DB_URL}" --quiet --set=ON_ERROR_STOP=1 \
    --set=allowlist_values="${allowlist_values}" --single-transaction "${files[@]}"
  echo "Demo database validation passed: ${DEMO_VALIDATE_DB_NAME}, Flyway V${applied}"
}

main "$@"
