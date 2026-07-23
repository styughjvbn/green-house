#!/usr/bin/env bash
set -euo pipefail

DEMO_DB_NAME="${DEMO_DB_NAME:-greenhouse_demo}"
DEMO_DB_APP_ROLE="${DEMO_DB_APP_ROLE:-greenhouse_demo_app}"
DEMO_DB_MIGRATOR_ROLE="${DEMO_DB_MIGRATOR_ROLE:-greenhouse_demo_migrator}"
DEMO_NAMESPACE="${DEMO_NAMESPACE:-green-house-demo}"
DEMO_BACKEND_DEPLOYMENT="${DEMO_BACKEND_DEPLOYMENT:-green-house-backend}"
ROLLOUT_TIMEOUT="${ROLLOUT_TIMEOUT:-300s}"
OPERATION_LOCK_FILE="${GREENHOUSE_OPERATION_LOCK_FILE:-/tmp/green-house-operation.lock}"

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
  [[ "${DEMO_DB_APP_ROLE}" =~ ^[a-z_][a-z0-9_]*$ ]] \
    || fail "Invalid DEMO_DB_APP_ROLE"
  [[ "${DEMO_DB_MIGRATOR_ROLE}" =~ ^[a-z_][a-z0-9_]*$ ]] \
    || fail "Invalid DEMO_DB_MIGRATOR_ROLE"
  [[ -n "${DEMO_DB_ADMIN_URL:-}" ]] || fail "DEMO_DB_ADMIN_URL is required"
  [[ -n "${DEMO_DB_TARGET_URL:-}" ]] || fail "DEMO_DB_TARGET_URL is required"
  [[ "${DEMO_RESET_CONFIRM:-}" == "greenhouse_demo" ]] \
    || fail "Set DEMO_RESET_CONFIRM=greenhouse_demo to confirm the reset"
  [[ "${DEMO_SANITIZATION_VERIFIED:-}" == "true" ]] \
    || fail "Set DEMO_SANITIZATION_VERIFIED=true only after anonymization verification"
}

validate_backup() {
  local backup="$1"
  [[ -f "${backup}" ]] || fail "Backup not found: ${backup}"
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
    --role="${DEMO_DB_MIGRATOR_ROLE}"
    --exit-on-error
  )

  if [[ "${backup}" == *.gz ]]; then
    gzip -cd "${backup}" | pg_restore "${restore_args[@]}"
  else
    pg_restore "${restore_args[@]}" "${backup}"
  fi
}

grant_application_privileges() {
  psql "${DEMO_DB_TARGET_URL}" \
    --set=ON_ERROR_STOP=1 \
    --set=app_role="${DEMO_DB_APP_ROLE}" \
    --set=migrator_role="${DEMO_DB_MIGRATOR_ROLE}" <<'SQL'
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO :"app_role";
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO :"app_role";
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO :"app_role";
ALTER DEFAULT PRIVILEGES FOR ROLE :"migrator_role" IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO :"app_role";
ALTER DEFAULT PRIVILEGES FOR ROLE :"migrator_role" IN SCHEMA public
  GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO :"app_role";
SQL
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
  require_command dropdb
  require_command createdb
  require_command gzip

  validate_target
  validate_backup "$1"

  exec 9>"${OPERATION_LOCK_FILE}"
  flock -n 9 || fail "Another deployment or demo reset is running: ${OPERATION_LOCK_FILE}"

  ORIGINAL_BACKEND_REPLICAS="$(
    kubectl -n "${DEMO_NAMESPACE}" get deployment "${DEMO_BACKEND_DEPLOYMENT}" \
      -o jsonpath='{.spec.replicas}'
  )"
  [[ "${ORIGINAL_BACKEND_REPLICAS}" =~ ^[0-9]+$ ]] || fail "Cannot determine backend replica count"

  restore_backend() {
    kubectl -n "${DEMO_NAMESPACE}" scale deployment "${DEMO_BACKEND_DEPLOYMENT}" \
      --replicas="${ORIGINAL_BACKEND_REPLICAS}" >/dev/null || true
  }
  trap restore_backend EXIT

  kubectl -n "${DEMO_NAMESPACE}" scale deployment "${DEMO_BACKEND_DEPLOYMENT}" --replicas=0
  kubectl -n "${DEMO_NAMESPACE}" rollout status "deployment/${DEMO_BACKEND_DEPLOYMENT}" \
    --timeout="${ROLLOUT_TIMEOUT}"

  dropdb --if-exists --force --maintenance-db="${DEMO_DB_ADMIN_URL}" "${DEMO_DB_NAME}"
  createdb --maintenance-db="${DEMO_DB_ADMIN_URL}" \
    --owner="${DEMO_DB_MIGRATOR_ROLE}" "${DEMO_DB_NAME}"
  restore_backup "$1"
  grant_application_privileges

  restore_backend
  trap - EXIT
  kubectl -n "${DEMO_NAMESPACE}" rollout status "deployment/${DEMO_BACKEND_DEPLOYMENT}" \
    --timeout="${ROLLOUT_TIMEOUT}"

  kubectl -n "${DEMO_NAMESPACE}" delete pod demo-reset-smoke --ignore-not-found >/dev/null
  kubectl -n "${DEMO_NAMESPACE}" run demo-reset-smoke \
    --rm -i \
    --restart=Never \
    --image=curlimages/curl \
    --command -- curl -fsS "http://${DEMO_BACKEND_DEPLOYMENT}:8080/actuator/health"

  echo "Demo database reset completed: ${DEMO_DB_NAME}"
}

main "$@"
