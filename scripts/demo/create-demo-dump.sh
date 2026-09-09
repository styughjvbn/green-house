#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ALLOWLIST="${SCRIPT_DIR}/schema-allowlist.tsv"
TEMP_DB_NAME="${SANITIZE_DB_NAME:-greenhouse_demo_sanitize_tmp}"
ANONYMIZATION_KEY="${DEMO_ANONYMIZATION_KEY:-}"
DATE_SHIFT_DAYS="${DEMO_DATE_SHIFT_DAYS:-}"
QUANTITY_FACTOR="${DEMO_QUANTITY_FACTOR:-}"
PRICE_FACTOR="${DEMO_PRICE_FACTOR:-}"
BACKEND_DIR="${SANITIZE_BACKEND_DIR:-${SCRIPT_DIR}/../../backend}"
STATE_CHAIN_SOURCE="${DEMO_STATE_CHAIN_MANIFEST_SOURCE:-${SCRIPT_DIR}/../data-audit/orchid-state-chain-migration-manifest.json}"
STATE_CHAIN_OUTPUT="${DEMO_STATE_CHAIN_MANIFEST_OUTPUT:-/tmp/demo-state-chain-manifest.json}"
CUTOVER_METADATA_OUTPUT="${DEMO_CUTOVER_METADATA_OUTPUT:-/tmp/demo-cutover.env}"
WRITER_VERSION="${ORCHID_LEDGER_WRITER_VERSION:-2.0.0}"

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
  [[ "${DEMO_SOURCE_CUTOVER_BUSINESS_DATE:-}" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]] \
    || fail "DEMO_SOURCE_CUTOVER_BUSINESS_DATE must use YYYY-MM-DD"
  [[ -f "${STATE_CHAIN_SOURCE}" ]] || fail "State-chain manifest not found: ${STATE_CHAIN_SOURCE}"
  [[ -d "${BACKEND_DIR}" ]] || fail "Backend directory not found: ${BACKEND_DIR}"
  [[ "$(psql "${SANITIZE_DB_URL}" -Atqc 'SELECT current_database()')" == "${TEMP_DB_NAME}" ]] \
    || fail "SANITIZE_DB_URL does not target ${TEMP_DB_NAME}"
  pg_dump "${SANITIZE_DB_URL}" --schema-only --no-owner --no-privileges \
    --file=/dev/null \
    || fail "pg_dump is incompatible with the temporary PostgreSQL server"

  local sanitization_state
  sanitization_state="$(
    psql "${SANITIZE_DB_URL}" -Atqc "
      SELECT CASE
        WHEN to_regclass('demo_internal.sanitization_marker') IS NOT NULL
          THEN 'MARKED'
        WHEN EXISTS (SELECT 1 FROM business_partners)
         AND NOT EXISTS (
           SELECT 1
           FROM business_partners
           WHERE name NOT LIKE '데모 소매 거래처 %'
             AND name NOT LIKE '데모 도매 거래처 %'
             AND name NOT LIKE '데모 경매장 %'
         )
          THEN 'LEGACY_SANITIZED'
        ELSE 'CLEAN'
      END
    "
  )"
  [[ "${sanitization_state}" == "CLEAN" ]] \
    || fail "Temporary database is already sanitized (${sanitization_state}). Restore it from the source dump before running again."
}

run_backend_task() {
  local task="$1"
  local arguments="${2:-}"
  (
    cd "${BACKEND_DIR}"
    if [[ -n "${arguments}" ]]; then
      DATABASE_URL="${SANITIZE_FLYWAY_URL}" \
      DATABASE_USERNAME="${SANITIZE_FLYWAY_USER}" \
      DATABASE_PASSWORD="${SANITIZE_FLYWAY_PASSWORD}" \
      ORCHID_LEDGER_WRITER_VERSION="${WRITER_VERSION}" \
        ./gradlew --offline --no-daemon "${task}" --args="${arguments}"
    else
      DATABASE_URL="${SANITIZE_FLYWAY_URL}" \
      DATABASE_USERNAME="${SANITIZE_FLYWAY_USER}" \
      DATABASE_PASSWORD="${SANITIZE_FLYWAY_PASSWORD}" \
      ORCHID_LEDGER_WRITER_VERSION="${WRITER_VERSION}" \
        ./gradlew --offline --no-daemon "${task}"
    fi
  )
}

transition_to_engine() {
  export DEMO_STATE_CHAIN_MANIFEST_SOURCE="${STATE_CHAIN_SOURCE}"
  export DEMO_STATE_CHAIN_MANIFEST_OUTPUT="${STATE_CHAIN_OUTPUT}"
  export DEMO_CUTOVER_METADATA_OUTPUT="${CUTOVER_METADATA_OUTPUT}"

  [[ -f "${STATE_CHAIN_OUTPUT}" ]] || fail "Sanitized state-chain manifest was not created"
  [[ -f "${CUTOVER_METADATA_OUTPUT}" ]] || fail "Demo cutover metadata was not created"

  local cutover_key="" cutover_business_date="" name value
  while IFS='=' read -r name value; do
    case "${name}" in
      DEMO_CUTOVER_KEY) cutover_key="${value}" ;;
      DEMO_CUTOVER_BUSINESS_DATE) cutover_business_date="${value}" ;;
      *) fail "Unexpected cutover metadata field: ${name}" ;;
    esac
  done < "${CUTOVER_METADATA_OUTPUT}"
  [[ "${cutover_key}" =~ ^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$ ]] \
    || fail "Invalid generated demo cutover key"
  [[ "${cutover_business_date}" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]] \
    || fail "Invalid generated demo cutover business date"

  local migration_args="--cutover-key=${cutover_key} --manifest=${STATE_CHAIN_OUTPUT} --effective-business-date=${cutover_business_date} --minimum-writer-version=${WRITER_VERSION}"
  run_backend_task orchidStateChainMigrate "${migration_args} --apply=false --confirmation=PLAN:${cutover_key}"
  run_backend_task orchidStateChainMigrate "${migration_args} --apply=true --confirmation=IMPORT:${cutover_key}"
  run_backend_task orchidStateChainMigrate "${migration_args} --apply=true --confirmation=IMPORT:${cutover_key}"

  local cutover_args="--cutover-key=${cutover_key} --effective-business-date=${cutover_business_date} --minimum-writer-version=${WRITER_VERSION} --current-writer-version=${WRITER_VERSION}"
  run_backend_task orchidLedgerCutover "${cutover_args} --activate=false --confirmation=VERIFY:${cutover_key}"
  run_backend_task orchidLedgerCutover "${cutover_args} --activate=true --confirmation=ACTIVATE:${cutover_key}"
  run_backend_task orchidLedgerReconcile
  psql "${SANITIZE_DB_URL}" --quiet --set=ON_ERROR_STOP=1 \
    --command="ALTER TABLE orchid_groups VALIDATE CONSTRAINT ck_orchid_groups_quantity_nonnegative" \
    --command="ALTER TABLE orchid_groups VALIDATE CONSTRAINT ck_orchid_groups_reserved_quantity" \
    --command="ALTER TABLE orchid_groups VALIDATE CONSTRAINT ck_orchid_groups_state_revision" \
    --command="ALTER TABLE sales_slips VALIDATE CONSTRAINT ck_sales_slips_sales_status"
}

run_validation_sql() {
  local directory="$1"
  shift
  local files=()
  while IFS= read -r file; do files+=(-f "${file}"); done < <(find "${directory}" -maxdepth 1 -name '*.sql' -print | sort)
  [[ ${#files[@]} -gt 0 ]] || fail "No SQL files found in ${directory}"
  psql "${SANITIZE_DB_URL}" --quiet --set=ON_ERROR_STOP=1 "$@" \
    --single-transaction "${files[@]}"
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
  require_command python3
  require_command sha256sum
  validate_target

  local output="$1"
  [[ "${output}" == *.dump ]] || fail "Output must use the .dump extension"
  [[ ! -e "${output}" ]] || fail "Output already exists: ${output}"
  [[ ! -e "${output}.sha256" ]] || fail "Checksum already exists: ${output}.sha256"

  local allowlist_values
  allowlist_values="$(schema_allowlist_values)"

  psql "${SANITIZE_DB_URL}" --quiet --set=ON_ERROR_STOP=1 \
    --set=allowlist_values="${allowlist_values}" \
    --single-transaction \
    -f "${SCRIPT_DIR}/validate/schema-allowlist-check.sql"

  export DEMO_ANONYMIZATION_KEY="${ANONYMIZATION_KEY}"
  export DEMO_DATE_SHIFT_DAYS="${DATE_SHIFT_DAYS}"
  export DEMO_QUANTITY_FACTOR="${QUANTITY_FACTOR}"
  export DEMO_PRICE_FACTOR="${PRICE_FACTOR}"
  export DEMO_STATE_CHAIN_MANIFEST_SOURCE="${STATE_CHAIN_SOURCE}"
  export DEMO_STATE_CHAIN_MANIFEST_OUTPUT="${STATE_CHAIN_OUTPUT}"
  export DEMO_CUTOVER_METADATA_OUTPUT="${CUTOVER_METADATA_OUTPUT}"

  python3 "${SCRIPT_DIR}/sanitize_demo.py"
  transition_to_engine
  run_validation_sql "${SCRIPT_DIR}/validate" --set=allowlist_values="${allowlist_values}"

  pg_dump "${SANITIZE_DB_URL}" --format=custom --no-owner --no-privileges \
    --schema=public --file="${output}"
  pg_restore --list "${output}" >/dev/null
  (
    cd "$(dirname "${output}")"
    sha256sum "$(basename "${output}")" > "$(basename "${output}").sha256"
  )

  echo "Sanitized demo dump created: ${output}"
  echo "Checksum created: ${output}.sha256"
}

main "$@"
