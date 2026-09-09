#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ALLOWLIST="${SCRIPT_DIR}/schema-allowlist.tsv"
BACKEND_DIR="${SANITIZE_BACKEND_DIR:-${SCRIPT_DIR}/../../backend}"

fail() {
  echo "[ERROR] $*" >&2
  exit 1
}

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

main() {
  [[ $# -eq 1 ]] || fail "Usage: $0 <greenhouse_demo_engine.dump>"
  local dump="$1"
  [[ -f "${dump}" ]] || fail "Demo dump not found: ${dump}"

  "${SCRIPT_DIR}/restore-temp-db.sh" "${dump}"

  local allowlist_values files=()
  allowlist_values="$(schema_allowlist_values)"
  while IFS= read -r file; do files+=(-f "${file}"); done < <(
    find "${SCRIPT_DIR}/validate" -maxdepth 1 -name '*.sql' -print | sort
  )
  psql "${SANITIZE_DB_URL}" --quiet --set=ON_ERROR_STOP=1 \
    --set=allowlist_values="${allowlist_values}" --single-transaction "${files[@]}"

  (
    cd "${BACKEND_DIR}"
    DATABASE_URL="${SANITIZE_FLYWAY_URL}" \
    DATABASE_USERNAME="${SANITIZE_FLYWAY_USER}" \
    DATABASE_PASSWORD="${SANITIZE_FLYWAY_PASSWORD}" \
    ORCHID_LEDGER_WRITER_VERSION="2.0.0" \
      ./gradlew --offline --no-daemon orchidLedgerReconcile
    DATABASE_URL="${SANITIZE_FLYWAY_URL}" \
    DATABASE_USERNAME="${SANITIZE_FLYWAY_USER}" \
    DATABASE_PASSWORD="${SANITIZE_FLYWAY_PASSWORD}" \
    ORCHID_LEDGER_WRITER_VERSION="2.0.0" \
      ./gradlew --offline --no-daemon orchidLedgerStartupVerify
  )

  echo "Fresh PostgreSQL 14 restore verification passed: ${dump}"
}

main "$@"
