#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ALLOWLIST="${SCRIPT_DIR}/schema-allowlist.tsv"
TEMP_DB_NAME="${SANITIZE_DB_NAME:-greenhouse_demo_sanitize_tmp}"
ANONYMIZATION_KEY="${DEMO_ANONYMIZATION_KEY:-}"
DATE_SHIFT_DAYS="${DEMO_DATE_SHIFT_DAYS:-}"
QUANTITY_FACTOR="${DEMO_QUANTITY_FACTOR:-}"
PRICE_FACTOR="${DEMO_PRICE_FACTOR:-}"

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
  [[ -n "${SANITIZE_DB_URL:-}" ]] || fail "SANITIZE_DB_URL is required"
  [[ "${SANITIZE_DUMP_CONFIRM:-}" == "${TEMP_DB_NAME}" ]] \
    || fail "Set SANITIZE_DUMP_CONFIRM=${TEMP_DB_NAME}"
  [[ ${#ANONYMIZATION_KEY} -ge 32 ]] \
    || fail "DEMO_ANONYMIZATION_KEY must contain at least 32 characters"
  [[ "${DATE_SHIFT_DAYS}" =~ ^-?[0-9]+$ ]] || fail "DEMO_DATE_SHIFT_DAYS must be an integer"
  [[ "${DATE_SHIFT_DAYS}" != "0" ]] || fail "DEMO_DATE_SHIFT_DAYS must not be zero"
  [[ "${QUANTITY_FACTOR}" =~ ^[2-9]$ ]] || fail "DEMO_QUANTITY_FACTOR must be an integer from 2 to 9"
  [[ "${PRICE_FACTOR}" =~ ^[2-9]$ ]] || fail "DEMO_PRICE_FACTOR must be an integer from 2 to 9"
  [[ "$(psql "${SANITIZE_DB_URL}" -Atqc 'SELECT current_database()')" == "${TEMP_DB_NAME}" ]] \
    || fail "SANITIZE_DB_URL does not target ${TEMP_DB_NAME}"
}

run_sql() {
  local directory="$1"
  shift
  local files=()
  while IFS= read -r file; do files+=(-f "${file}"); done < <(find "${directory}" -maxdepth 1 -name '*.sql' -print | sort)
  [[ ${#files[@]} -gt 0 ]] || fail "No SQL files found in ${directory}"
  psql "${SANITIZE_DB_URL}" --set=ON_ERROR_STOP=1 "$@" --single-transaction "${files[@]}"
}

schema_allowlist_values() {
  local values=""
  local separator=""
  local table_name fingerprint
  while IFS=$'\t' read -r table_name fingerprint; do
    [[ "${table_name}" =~ ^[a-z_][a-z0-9_]*$ ]] || fail "Invalid allowlisted table: ${table_name}"
    [[ "${fingerprint}" =~ ^[a-f0-9]{32}$ ]] || fail "Invalid schema fingerprint: ${table_name}"
    values+="${separator}('${table_name}','${fingerprint}')"
    separator=","
  done < "${ALLOWLIST}"
  printf '%s' "${values}"
}

main() {
  [[ $# -eq 1 ]] || fail "Usage: $0 <greenhouse_demo_sanitized.dump>"

  require_command find
  require_command pg_dump
  require_command pg_restore
  require_command psql
  require_command sha256sum
  validate_target

  local output="$1"
  [[ "${output}" == *.dump ]] || fail "Output must use the .dump extension"
  [[ ! -e "${output}" ]] || fail "Output already exists: ${output}"
  [[ ! -e "${output}.sha256" ]] || fail "Checksum already exists: ${output}.sha256"

  local allowlist_values
  allowlist_values="$(schema_allowlist_values)"

  psql "${SANITIZE_DB_URL}" --set=ON_ERROR_STOP=1 \
    --set=allowlist_values="${allowlist_values}" \
    --single-transaction \
    -f "${SCRIPT_DIR}/validate/schema-allowlist-check.sql"

  export DEMO_ANONYMIZATION_KEY="${ANONYMIZATION_KEY}"
  export DEMO_DATE_SHIFT_DAYS="${DATE_SHIFT_DAYS}"
  export DEMO_QUANTITY_FACTOR="${QUANTITY_FACTOR}"
  export DEMO_PRICE_FACTOR="${PRICE_FACTOR}"

  run_sql "${SCRIPT_DIR}/sanitize"
  run_sql "${SCRIPT_DIR}/validate" --set=allowlist_values="${allowlist_values}"

  local table_args=()
  while IFS=$'\t' read -r table_name _; do
    [[ -z "${table_name}" || "${table_name}" == \#* ]] && continue
    table_args+=(--table="public.${table_name}")
  done < "${ALLOWLIST}"
  table_args+=(--table="public.flyway_schema_history")

  pg_dump "${SANITIZE_DB_URL}" --format=custom --no-owner --no-privileges \
    --file="${output}" "${table_args[@]}"
  pg_restore --list "${output}" >/dev/null
  (
    cd "$(dirname "${output}")"
    sha256sum "$(basename "${output}")" > "$(basename "${output}").sha256"
  )

  echo "Sanitized demo dump created: ${output}"
  echo "Checksum created: ${output}.sha256"
}

main "$@"
