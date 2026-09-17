#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEMO_DB_NAME="${DEMO_DB_NAME:-greenhouse_demo}"
DEMO_DB_NEXT_NAME="${DEMO_DB_NEXT_NAME:-greenhouse_demo_next}"
DEMO_DB_PREV_NAME="${DEMO_DB_PREV_NAME:-greenhouse_demo_prev}"
DEMO_DB_OWNER="${DEMO_DB_OWNER:-greenhouse_demo}"
PRODUCTION_READ_ROLE="${PRODUCTION_READ_ROLE:-greenhouse}"
DEMO_NAMESPACE="${DEMO_NAMESPACE:-green-house-demo}"
DEMO_BACKEND_DEPLOYMENT="${DEMO_BACKEND_DEPLOYMENT:-green-house-backend}"
ROLLOUT_TIMEOUT="${ROLLOUT_TIMEOUT:-300s}"
OPERATION_LOCK_FILE="${GREENHOUSE_OPERATION_LOCK_FILE:-/tmp/green-house-operation.lock}"

fail() { echo "[ERROR] $*" >&2; exit 1; }
require_command() { command -v "$1" >/dev/null 2>&1 || fail "Command not found: $1"; }

admin_sql() {
  psql "${DEMO_DB_ADMIN_URL}" --set=ON_ERROR_STOP=1 --quiet --command="$1"
}

terminate_connections() {
  local name="$1"
  admin_sql "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='${name}' AND pid<>pg_backend_pid();"
}

configure_database() {
  local name="$1" url="$2"
  admin_sql "ALTER DATABASE ${name} OWNER TO ${DEMO_DB_OWNER}; REVOKE ALL ON DATABASE ${name} FROM PUBLIC; GRANT CONNECT ON DATABASE ${name} TO ${DEMO_DB_OWNER}; GRANT CONNECT ON DATABASE ${name} TO ${PRODUCTION_READ_ROLE}; ALTER ROLE ${DEMO_DB_OWNER} IN DATABASE ${name} SET statement_timeout='15s'; ALTER ROLE ${DEMO_DB_OWNER} IN DATABASE ${name} SET lock_timeout='3s'; ALTER ROLE ${DEMO_DB_OWNER} IN DATABASE ${name} SET idle_in_transaction_session_timeout='60s'; ALTER ROLE ${DEMO_DB_OWNER} IN DATABASE ${name} SET temp_file_limit='128MB';"
  psql "${url}" --set=ON_ERROR_STOP=1 --quiet <<SQL
ALTER SCHEMA public OWNER TO ${DEMO_DB_OWNER};
ALTER SCHEMA demo_internal OWNER TO ${DEMO_DB_OWNER};
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
REVOKE CREATE ON SCHEMA demo_internal FROM PUBLIC;
GRANT USAGE, CREATE ON SCHEMA public TO ${DEMO_DB_OWNER};
GRANT USAGE, CREATE ON SCHEMA demo_internal TO ${DEMO_DB_OWNER};
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO ${DEMO_DB_OWNER};
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA demo_internal TO ${DEMO_DB_OWNER};
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO ${DEMO_DB_OWNER};
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA demo_internal TO ${DEMO_DB_OWNER};
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO ${DEMO_DB_OWNER};
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA demo_internal TO ${DEMO_DB_OWNER};
GRANT USAGE ON SCHEMA public TO ${PRODUCTION_READ_ROLE};
GRANT USAGE ON SCHEMA demo_internal TO ${PRODUCTION_READ_ROLE};
GRANT SELECT ON ALL TABLES IN SCHEMA public TO ${PRODUCTION_READ_ROLE};
GRANT SELECT ON ALL TABLES IN SCHEMA demo_internal TO ${PRODUCTION_READ_ROLE};
GRANT SELECT ON ALL SEQUENCES IN SCHEMA public TO ${PRODUCTION_READ_ROLE};
GRANT SELECT ON ALL SEQUENCES IN SCHEMA demo_internal TO ${PRODUCTION_READ_ROLE};
ALTER DEFAULT PRIVILEGES FOR ROLE ${DEMO_DB_OWNER} IN SCHEMA public GRANT SELECT ON TABLES TO ${PRODUCTION_READ_ROLE};
ALTER DEFAULT PRIVILEGES FOR ROLE ${DEMO_DB_OWNER} IN SCHEMA public GRANT SELECT ON SEQUENCES TO ${PRODUCTION_READ_ROLE};
ALTER DEFAULT PRIVILEGES FOR ROLE ${DEMO_DB_OWNER} IN SCHEMA demo_internal GRANT SELECT ON TABLES TO ${PRODUCTION_READ_ROLE};
ALTER DEFAULT PRIVILEGES FOR ROLE ${DEMO_DB_OWNER} IN SCHEMA demo_internal GRANT SELECT ON SEQUENCES TO ${PRODUCTION_READ_ROLE};
SQL
}

restore_dump() {
  local dump="$1"
  if [[ "${dump}" == *.gz ]]; then
    gzip -cd "${dump}" | pg_restore --dbname="${DEMO_DB_NEXT_URL}" --no-owner --no-privileges \
      --clean --if-exists --exit-on-error
  else
    pg_restore --dbname="${DEMO_DB_NEXT_URL}" --no-owner --no-privileges \
      --clean --if-exists --exit-on-error "${dump}"
  fi
}

start_backend() {
  kubectl -n "${DEMO_NAMESPACE}" scale deployment "${DEMO_BACKEND_DEPLOYMENT}" --replicas="${ORIGINAL_BACKEND_REPLICAS}"
  kubectl -n "${DEMO_NAMESPACE}" rollout status "deployment/${DEMO_BACKEND_DEPLOYMENT}" --timeout="${ROLLOUT_TIMEOUT}"
}

stop_backend() {
  kubectl -n "${DEMO_NAMESPACE}" scale deployment "${DEMO_BACKEND_DEPLOYMENT}" --replicas=0
  kubectl -n "${DEMO_NAMESPACE}" wait --for=delete pod \
    -l app.kubernetes.io/name=green-house,app.kubernetes.io/component=backend \
    --timeout="${ROLLOUT_TIMEOUT}"
}

smoke_test() {
  local pod="demo-refresh-smoke-$(date +%s)"
  kubectl -n "${DEMO_NAMESPACE}" run "${pod}" --rm -i --restart=Never \
    --image=curlimages/curl --command -- curl -fsS \
    "http://${DEMO_BACKEND_DEPLOYMENT}:8080/actuator/health"
  if [[ -n "${DEMO_API_SMOKE_URL:-}" ]]; then
    curl -fsS --max-time 30 "${DEMO_API_SMOKE_URL}" >/dev/null
  fi
}

rollback_swap() {
  echo "[WARN] Rolling demo database back to ${DEMO_DB_PREV_NAME}" >&2
  stop_backend || true
  local has_demo has_next has_prev rollback_ok=true
  has_demo="$(psql "${DEMO_DB_ADMIN_URL}" -Atqc "SELECT count(*) FROM pg_database WHERE datname='${DEMO_DB_NAME}'")"
  has_next="$(psql "${DEMO_DB_ADMIN_URL}" -Atqc "SELECT count(*) FROM pg_database WHERE datname='${DEMO_DB_NEXT_NAME}'")"
  has_prev="$(psql "${DEMO_DB_ADMIN_URL}" -Atqc "SELECT count(*) FROM pg_database WHERE datname='${DEMO_DB_PREV_NAME}'")"
  if [[ "${has_prev}" == "1" ]]; then
    if [[ "${has_demo}" == "1" && "${has_next}" == "0" ]]; then
      admin_sql "REVOKE CONNECT ON DATABASE ${DEMO_DB_NAME} FROM ${DEMO_DB_OWNER}, ${PRODUCTION_READ_ROLE};" || true
      terminate_connections "${DEMO_DB_NAME}" || true
      admin_sql "ALTER DATABASE ${DEMO_DB_NAME} RENAME TO ${DEMO_DB_NEXT_NAME};" || rollback_ok=false
    fi
    if [[ "${rollback_ok}" == "true" && "$(psql "${DEMO_DB_ADMIN_URL}" -Atqc "SELECT count(*) FROM pg_database WHERE datname='${DEMO_DB_NAME}'")" == "0" ]]; then
      admin_sql "ALTER DATABASE ${DEMO_DB_PREV_NAME} RENAME TO ${DEMO_DB_NAME};" || rollback_ok=false
    fi
  fi
  if [[ "${rollback_ok}" == "true" ]] \
    && [[ "$(psql "${DEMO_DB_ADMIN_URL}" -Atqc "SELECT count(*) FROM pg_database WHERE datname='${DEMO_DB_NAME}'")" == "1" ]] \
    && configure_database "${DEMO_DB_NAME}" "${DEMO_DB_URL}" \
    && start_backend; then
    echo "[WARN] Previous demo database restored" >&2
  else
    echo "[ERROR] Automatic database rollback or restart failed; backend remains stopped" >&2
  fi
}

main() {
  [[ $# -eq 1 ]] || fail "Usage: $0 <sanitized.dump|sanitized.dump.gz>"
  for command in createdb dropdb find flock flyway gzip kubectl pg_restore psql sha256sum curl; do require_command "${command}"; done
  [[ "${DEMO_DB_NAME}" == "greenhouse_demo" ]] || fail "DEMO_DB_NAME must be exactly greenhouse_demo"
  [[ "${DEMO_DB_NEXT_NAME}" == "greenhouse_demo_next" ]] || fail "DEMO_DB_NEXT_NAME must be exactly greenhouse_demo_next"
  [[ "${DEMO_DB_PREV_NAME}" == "greenhouse_demo_prev" ]] || fail "DEMO_DB_PREV_NAME must be exactly greenhouse_demo_prev"
  [[ "${DEMO_DB_OWNER}" == "greenhouse_demo" ]] || fail "DEMO_DB_OWNER must be exactly greenhouse_demo"
  [[ "${PRODUCTION_READ_ROLE}" == "greenhouse" ]] || fail "PRODUCTION_READ_ROLE must be exactly greenhouse"
  [[ "${DEMO_REFRESH_CONFIRM:-}" == "greenhouse_demo:greenhouse_demo_next:greenhouse_demo_prev" ]] \
    || fail "Set DEMO_REFRESH_CONFIRM=greenhouse_demo:greenhouse_demo_next:greenhouse_demo_prev"
  [[ -n "${DEMO_DB_ADMIN_URL:-}" && -n "${DEMO_DB_URL:-}" && -n "${DEMO_DB_NEXT_URL:-}" ]] \
    || fail "DEMO_DB_ADMIN_URL, DEMO_DB_URL and DEMO_DB_NEXT_URL are required"
  [[ "$(psql "${DEMO_DB_ADMIN_URL}" -Atqc "SELECT current_database()||':'||(SELECT rolsuper FROM pg_roles WHERE rolname=current_user)")" == "postgres:true" ]] \
    || fail "DEMO_DB_ADMIN_URL must target postgres as a superuser"
  [[ "$(psql "${DEMO_DB_URL}" -Atqc "SELECT current_database()||':'||current_user")" == "${DEMO_DB_NAME}:${DEMO_DB_OWNER}" ]] \
    || fail "DEMO_DB_URL must target ${DEMO_DB_NAME} as ${DEMO_DB_OWNER}"

  local dump="$1"
  [[ -f "${dump}" && -f "${dump}.sha256" ]] || fail "Dump and checksum are required"
  (cd "$(dirname "${dump}")" && sha256sum -c "$(basename "${dump}").sha256")
  if [[ "${GREENHOUSE_OPERATION_LOCK_HELD:-false}" != "true" ]]; then
    exec 9>"${OPERATION_LOCK_FILE}"
    flock -n 9 || fail "Another deployment or demo refresh is running: ${OPERATION_LOCK_FILE}"
  fi

  dropdb --if-exists --force --maintenance-db="${DEMO_DB_ADMIN_URL}" "${DEMO_DB_NEXT_NAME}"
  createdb --maintenance-db="${DEMO_DB_ADMIN_URL}" --owner="${DEMO_DB_OWNER}" "${DEMO_DB_NEXT_NAME}"
  [[ "$(psql "${DEMO_DB_NEXT_URL}" -Atqc "SELECT current_database()||':'||current_user")" == "${DEMO_DB_NEXT_NAME}:${DEMO_DB_OWNER}" ]] \
    || fail "DEMO_DB_NEXT_URL must target ${DEMO_DB_NEXT_NAME} as ${DEMO_DB_OWNER}"
  restore_dump "${dump}"
  configure_database "${DEMO_DB_NEXT_NAME}" "${DEMO_DB_NEXT_URL}"
  DEMO_VALIDATE_DB_URL="${DEMO_DB_NEXT_URL}" \
  DEMO_VALIDATE_DB_NAME="${DEMO_DB_NEXT_NAME}" \
  DEMO_VALIDATE_FLYWAY_URL="${DEMO_NEXT_FLYWAY_URL:?DEMO_NEXT_FLYWAY_URL is required}" \
  DEMO_VALIDATE_FLYWAY_USER="${DEMO_DB_OWNER}" \
  DEMO_VALIDATE_FLYWAY_PASSWORD="${DEMO_DB_PASSWORD:?DEMO_DB_PASSWORD is required}" \
    "${SCRIPT_DIR}/validate-demo-db.sh"

  ORIGINAL_BACKEND_REPLICAS="$(kubectl -n "${DEMO_NAMESPACE}" get deployment "${DEMO_BACKEND_DEPLOYMENT}" -o jsonpath='{.spec.replicas}')"
  [[ "${ORIGINAL_BACKEND_REPLICAS}" =~ ^[1-9][0-9]*$ ]] || fail "Backend must currently have at least one replica"
  SWAP_STARTED=true
  trap '[[ "${SWAP_STARTED:-false}" == "true" ]] && rollback_swap' EXIT
  stop_backend

  admin_sql "REVOKE CONNECT ON DATABASE ${DEMO_DB_NAME} FROM ${DEMO_DB_OWNER}, ${PRODUCTION_READ_ROLE};"
  admin_sql "REVOKE CONNECT ON DATABASE ${DEMO_DB_NEXT_NAME} FROM ${DEMO_DB_OWNER}, ${PRODUCTION_READ_ROLE};"
  terminate_connections "${DEMO_DB_NAME}"
  terminate_connections "${DEMO_DB_NEXT_NAME}"
  dropdb --if-exists --force --maintenance-db="${DEMO_DB_ADMIN_URL}" "${DEMO_DB_PREV_NAME}"
  admin_sql "ALTER DATABASE ${DEMO_DB_NAME} RENAME TO ${DEMO_DB_PREV_NAME};"
  admin_sql "ALTER DATABASE ${DEMO_DB_NEXT_NAME} RENAME TO ${DEMO_DB_NAME};"
  configure_database "${DEMO_DB_NAME}" "${DEMO_DB_URL}"
  start_backend
  smoke_test

  SWAP_STARTED=false
  trap - EXIT
  echo "Demo database refresh completed; rollback database retained as ${DEMO_DB_PREV_NAME}"
}

main "$@"
