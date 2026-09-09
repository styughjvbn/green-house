#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ALLOWLIST="${SCRIPT_DIR}/schema-allowlist.tsv"
DEMO_DB_NAME="${DEMO_DB_NAME:-greenhouse_demo}"
DEMO_NAMESPACE="${DEMO_NAMESPACE:-green-house-demo}"
DEMO_BACKEND_DEPLOYMENT="${DEMO_BACKEND_DEPLOYMENT:-green-house-backend}"
ROLLOUT_TIMEOUT="${ROLLOUT_TIMEOUT:-300s}"
OPERATION_LOCK_FILE="${GREENHOUSE_OPERATION_LOCK_FILE:-/tmp/green-house-operation.lock}"
RESTART_BACKEND="${DEMO_RESET_RESTART:-true}"

usage() {
  echo "Usage: DEMO_DB_ADMIN_URL=... DEMO_DB_TARGET_URL=... $0 <sanitized.dump|sanitized.dump.gz>"
}

fail() {
  echo "[ERROR] $*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Command not found: $1"
}

validate_target() {
  [[ "${DEMO_DB_NAME}" == "greenhouse_demo" ]] \
    || fail "DEMO_DB_NAME must be exactly greenhouse_demo"
  [[ "${DEMO_DB_NAME}" != *prod* ]] \
    || fail "Production-like database name is forbidden"
  [[ -n "${DEMO_DB_ADMIN_URL:-}" ]] || fail "DEMO_DB_ADMIN_URL is required"
  [[ -n "${DEMO_DB_TARGET_URL:-}" ]] || fail "DEMO_DB_TARGET_URL is required"
  [[ "${DEMO_RESET_CONFIRM:-}" == "greenhouse_demo" ]] \
    || fail "Set DEMO_RESET_CONFIRM=greenhouse_demo to confirm the reset"
  [[ "${RESTART_BACKEND}" == "true" || "${RESTART_BACKEND}" == "false" ]] \
    || fail "DEMO_RESET_RESTART must be true or false"
}

validate_target_connection() {
  [[ "$(psql "${DEMO_DB_TARGET_URL}" -Atqc 'SELECT current_user' 2>/dev/null || true)" == "greenhouse_demo" ]] \
    || fail "DEMO_DB_TARGET_URL must authenticate as greenhouse_demo"
}

validate_backup() {
  local backup="$1"
  [[ -f "${backup}" ]] || fail "Backup not found: ${backup}"
  [[ -f "${backup}.sha256" ]] || fail "Sanitization checksum not found: ${backup}.sha256"
  (
    cd "$(dirname "${backup}")"
    sha256sum -c "$(basename "${backup}").sha256"
  ) || fail "Sanitization checksum verification failed"
  case "${backup}" in
    *.dump)
      pg_restore --list "${backup}" >/dev/null
      ;;
    *.dump.gz)
      gzip -cd "${backup}" | pg_restore --list >/dev/null
      ;;
    *)
      fail "Only .dump and .dump.gz backups are supported"
      ;;
  esac
}

restore_backup() {
  local backup="$1"
  local restore_args=(
    --dbname="${DEMO_DB_TARGET_URL}"
    --no-owner
    --no-privileges
    --clean
    --if-exists
    --exit-on-error
  )

  if [[ "${backup}" == *.gz ]]; then
    gzip -cd "${backup}" | pg_restore "${restore_args[@]}"
  else
    pg_restore "${restore_args[@]}" "${backup}"
  fi
}

configure_application_schema() {
  psql "${DEMO_DB_ADMIN_URL}" --set=ON_ERROR_STOP=1 \
    --set=demo_db="${DEMO_DB_NAME}" <<'SQL'
REVOKE ALL ON DATABASE greenhouse_demo FROM PUBLIC;
GRANT CONNECT ON DATABASE greenhouse_demo TO greenhouse_demo;
ALTER ROLE greenhouse_demo IN DATABASE greenhouse_demo SET statement_timeout = '15s';
ALTER ROLE greenhouse_demo IN DATABASE greenhouse_demo SET lock_timeout = '3s';
ALTER ROLE greenhouse_demo IN DATABASE greenhouse_demo SET idle_in_transaction_session_timeout = '60s';
ALTER ROLE greenhouse_demo IN DATABASE greenhouse_demo SET temp_file_limit = '128MB';
SQL
  psql "${DEMO_DB_TARGET_URL}" \
    --set=ON_ERROR_STOP=1 <<'SQL'
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
SQL
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

validate_restored_database() {
  local allowlist_values
  allowlist_values="$(schema_allowlist_values)"
  local files=()
  while IFS= read -r file; do files+=(-f "${file}"); done < <(
    find "${SCRIPT_DIR}/validate" -maxdepth 1 -name '*.sql' -print | sort
  )
  psql "${DEMO_DB_TARGET_URL}" --quiet --set=ON_ERROR_STOP=1 \
    --set=allowlist_values="${allowlist_values}" --single-transaction "${files[@]}"
}

main() {
  [[ $# -eq 1 ]] || {
    usage
    exit 1
  }

  require_command flock
  require_command kubectl
  require_command psql
  require_command pg_restore
  require_command sha256sum
  require_command dropdb
  require_command createdb
  require_command gzip
  require_command find

  validate_target
  validate_backup "$1"

  exec 9>"${OPERATION_LOCK_FILE}"
  flock -n 9 || fail "Another deployment or demo reset is running: ${OPERATION_LOCK_FILE}"

  ORIGINAL_BACKEND_REPLICAS="$(
    kubectl -n "${DEMO_NAMESPACE}" get deployment "${DEMO_BACKEND_DEPLOYMENT}" \
      -o jsonpath='{.spec.replicas}'
  )"
  [[ "${ORIGINAL_BACKEND_REPLICAS}" =~ ^[0-9]+$ ]] || fail "Cannot determine backend replica count"

  RESET_SUCCEEDED=false
  finish_reset() {
    if [[ "${RESET_SUCCEEDED}" == "true" && "${RESTART_BACKEND}" == "true" ]]; then
      kubectl -n "${DEMO_NAMESPACE}" scale deployment "${DEMO_BACKEND_DEPLOYMENT}" \
        --replicas="${ORIGINAL_BACKEND_REPLICAS}" >/dev/null || true
    elif [[ "${RESET_SUCCEEDED}" == "true" ]]; then
      echo "Demo backend remains stopped for the initial Engine deployment."
    else
      echo "[ERROR] Demo backend remains stopped because database reset did not complete." >&2
    fi
  }
  trap finish_reset EXIT

  kubectl -n "${DEMO_NAMESPACE}" scale deployment "${DEMO_BACKEND_DEPLOYMENT}" --replicas=0
  kubectl -n "${DEMO_NAMESPACE}" rollout status "deployment/${DEMO_BACKEND_DEPLOYMENT}" \
    --timeout="${ROLLOUT_TIMEOUT}"

  dropdb --if-exists --force --maintenance-db="${DEMO_DB_ADMIN_URL}" "${DEMO_DB_NAME}"
  createdb --maintenance-db="${DEMO_DB_ADMIN_URL}" \
    --owner="${DEMO_DB_NAME}" "${DEMO_DB_NAME}"
  validate_target_connection
  restore_backup "$1"
  configure_application_schema
  validate_restored_database

  RESET_SUCCEEDED=true
  finish_reset
  trap - EXIT
  if [[ "${RESTART_BACKEND}" == "true" ]]; then
    kubectl -n "${DEMO_NAMESPACE}" rollout status "deployment/${DEMO_BACKEND_DEPLOYMENT}" \
      --timeout="${ROLLOUT_TIMEOUT}"

    kubectl -n "${DEMO_NAMESPACE}" delete pod demo-reset-smoke --ignore-not-found >/dev/null
    kubectl -n "${DEMO_NAMESPACE}" run demo-reset-smoke \
      --rm -i \
      --restart=Never \
      --image=curlimages/curl \
      --command -- curl -fsS "http://${DEMO_BACKEND_DEPLOYMENT}:8080/actuator/health"
  fi

  echo "Demo database reset completed: ${DEMO_DB_NAME}"
}

main "$@"
