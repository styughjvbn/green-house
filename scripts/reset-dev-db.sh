#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
BACKEND_DIR="${ROOT}/backend"
TEMP_DIR="${ROOT}/temp"

DB_HOST="${DB_HOST:-${PGHOST:-localhost}}"
DB_PORT="${DB_PORT:-${PGPORT:-5432}}"
DB_NAME="${DB_NAME:-${PGDATABASE:-greenhouse}}"
DB_USER="${DB_USER:-${PGUSER:-greenhouse}}"
DB_PASSWORD="${DB_PASSWORD:-${PGPASSWORD:-greenhouse}}"

BACKUP_FILE=""
ASSUME_YES=false
START_DB=true
MIGRATION_PID=""
MIGRATION_LOG=""

usage() {
    cat <<'EOF'
Usage: scripts/reset-dev-db.sh [backup.dump.gz|backup.dump] [options]

Reset the local development database from an operational PostgreSQL custom
dump, then start the backend temporarily to apply and validate Flyway.

Options:
  --backup FILE   Backup file. Defaults to the newest *.dump.gz or *.dump in temp/.
  --yes           Skip the destructive-action confirmation.
  --no-start-db   Do not run "docker compose up -d db".
  -h, --help      Show this help.

Database environment variables:
  DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD
  PostgreSQL PGHOST, PGPORT, PGDATABASE, PGUSER, PGPASSWORD are also accepted.
EOF
}

require_command() {
    if ! command -v "$1" >/dev/null 2>&1; then
        echo "Required command not found: $1" >&2
        exit 1
    fi
}

stop_process_tree() {
    local pid="$1"
    local child_pid

    if ! kill -0 "$pid" 2>/dev/null; then
        return
    fi

    while read -r child_pid; do
        if [[ -n "$child_pid" ]]; then
            stop_process_tree "$child_pid"
        fi
    done < <(pgrep -P "$pid" 2>/dev/null || true)

    kill "$pid" 2>/dev/null || true
    for _ in {1..20}; do
        if ! kill -0 "$pid" 2>/dev/null; then
            return
        fi
        sleep 0.25
    done
    kill -9 "$pid" 2>/dev/null || true
}

stop_local_backends() {
    local pid
    local stopped=false

    while read -r pid; do
        if [[ -z "$pid" || "$pid" == "$$" || "$pid" == "$PPID" ]]; then
            continue
        fi
        if kill -0 "$pid" 2>/dev/null; then
            stop_process_tree "$pid"
            echo "Stopped local backend process ${pid}"
            stopped=true
        fi
    done < <(
        pgrep -f "${ROOT}/backend.*bootRun|com\\.greenhouse\\.backend\\.BackendApplication" 2>/dev/null |
            sort -u || true
    )

    if [[ "$stopped" == true ]]; then
        rm -f "${ROOT}/.dev-pids/backend.pid"
    fi
}

stop_migration_backend() {
    if [[ -n "$MIGRATION_PID" ]] && kill -0 "$MIGRATION_PID" 2>/dev/null; then
        stop_process_tree "$MIGRATION_PID"
    fi
    MIGRATION_PID=""
}

cleanup() {
    stop_migration_backend
    if [[ -n "$MIGRATION_LOG" && -f "$MIGRATION_LOG" ]]; then
        rm -f "$MIGRATION_LOG"
    fi
}

latest_backup() {
    find "$TEMP_DIR" -maxdepth 1 -type f \
        \( -name '*.dump.gz' -o -name '*.dump' \) \
        -printf '%T@ %p\n' |
        sort -nr |
        head -n 1 |
        cut -d' ' -f2-
}

validate_backup() {
    local backup="$1"
    local gzip_status
    local restore_status

    case "$backup" in
        *.dump.gz)
            echo "Validating compressed backup: $backup"

            if ! gzip -t "$backup"; then
                echo "Invalid or corrupted gzip backup: $backup" >&2
                exit 1
            fi

            echo "Checking PostgreSQL dump format..."

            set +o pipefail
            gzip -cd "$backup" | pg_restore --list >/dev/null
            pipeline_status=("${PIPESTATUS[@]}")
            set -o pipefail

            gzip_status="${pipeline_status[0]}"
            restore_status="${pipeline_status[1]}"

            if (( restore_status != 0 )); then
                echo "pg_restore cannot read backup: $backup" >&2
                exit 1
            fi

            if (( gzip_status != 0 && gzip_status != 141 )); then
                echo "Failed to decompress backup: $backup" >&2
                exit 1
            fi

            echo "Backup validation passed."
            ;;

        *.dump)
            echo "Validating backup: $backup"

            if ! pg_restore --list "$backup" >/dev/null; then
                echo "pg_restore cannot read backup: $backup" >&2
                exit 1
            fi

            echo "Backup validation passed."
            ;;

        *)
            echo "Backup must be a PostgreSQL custom dump (*.dump or *.dump.gz)." >&2
            exit 1
            ;;
    esac
}

restore_backup() {
    local backup="$1"
    local pg_restore_args=(
        --host "$DB_HOST"
        --port "$DB_PORT"
        --username "$DB_USER"
        --dbname "$DB_NAME"
        --no-owner
        --no-privileges
        --exit-on-error
    )

    if [[ "$backup" == *.gz ]]; then
        gzip -cd "$backup" | pg_restore "${pg_restore_args[@]}"
    else
        pg_restore "${pg_restore_args[@]}" "$backup"
    fi
}

find_available_port() {
    local port
    for port in {18080..18099}; do
        if ! ss -ltnH "sport = :${port}" 2>/dev/null | grep -q .; then
            echo "$port"
            return
        fi
    done
    echo "No temporary backend port is available in 18080-18099." >&2
    exit 1
}

apply_flyway_and_validate_schema() {
    local port
    local deadline
    local status_code

    port="$(find_available_port)"
    MIGRATION_LOG="$(mktemp)"

    (
        cd "$BACKEND_DIR"
        SERVER_PORT="$port" \
            AUTH_ENABLED=false \
            DATABASE_URL="jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}" \
            DATABASE_USERNAME="$DB_USER" \
            DATABASE_PASSWORD="$DB_PASSWORD" \
            ./gradlew bootRun --no-daemon
    ) >"$MIGRATION_LOG" 2>&1 &
    MIGRATION_PID="$!"
    deadline=$((SECONDS + 120))

    while ((SECONDS < deadline)); do
        if ! kill -0 "$MIGRATION_PID" 2>/dev/null; then
            echo "Backend exited before Flyway/schema validation completed." >&2
            cat "$MIGRATION_LOG" >&2
            exit 1
        fi

        status_code="$(
            curl --silent --output /dev/null --write-out '%{http_code}' \
                --max-time 2 "http://127.0.0.1:${port}/actuator/health" 2>/dev/null || true
        )"
        if [[ "$status_code" =~ ^[234][0-9][0-9]$ ]]; then
            stop_migration_backend
            echo "Flyway migration and Hibernate schema validation passed."
            return
        fi
        sleep 1
    done

    echo "Backend did not become ready within 120 seconds." >&2
    cat "$MIGRATION_LOG" >&2
    exit 1
}

print_verification() {
    psql \
        --host "$DB_HOST" \
        --port "$DB_PORT" \
        --username "$DB_USER" \
        --dbname "$DB_NAME" \
        --set ON_ERROR_STOP=1 \
        --pset pager=off \
        --command "
            SELECT installed_rank, version, description, success
            FROM flyway_schema_history
            ORDER BY installed_rank;
        " \
        --command "
            SELECT 'houses' AS item, count(*) FROM houses
            UNION ALL SELECT 'orchid_groups', count(*) FROM orchid_groups
            UNION ALL SELECT 'work_records', count(*) FROM work_records
            UNION ALL SELECT 'work_operations', count(*) FROM work_operations
            UNION ALL SELECT 'work_operation_targets', count(*) FROM work_operation_targets
            UNION ALL SELECT 'work_target_executions', count(*) FROM work_target_executions
            ORDER BY item;
        " \
        --command "
            SELECT 'orphan_targets' AS check_name, count(*) AS count
            FROM work_operation_targets target
            LEFT JOIN work_operations operation ON operation.id = target.work_operation_id
            WHERE operation.id IS NULL
            UNION ALL
            SELECT 'targets_without_execution', count(*)
            FROM work_operation_targets target
            LEFT JOIN work_target_executions execution
                ON execution.work_operation_target_id = target.id
            WHERE execution.id IS NULL
            UNION ALL
            SELECT 'duplicate_executions', count(*)
            FROM (
                SELECT work_operation_target_id
                FROM work_target_executions
                GROUP BY work_operation_target_id
                HAVING count(*) > 1
            ) duplicate;
        "
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --backup)
            BACKUP_FILE="${2:-}"
            shift 2
            ;;
        --yes)
            ASSUME_YES=true
            shift
            ;;
        --no-start-db)
            START_DB=false
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        -*)
            echo "Unknown option: $1" >&2
            usage >&2
            exit 1
            ;;
        *)
            if [[ -n "$BACKUP_FILE" ]]; then
                echo "Only one backup file can be specified." >&2
                exit 1
            fi
            BACKUP_FILE="$1"
            shift
            ;;
    esac
done

for command_name in psql pg_restore gzip curl ss pgrep; do
    require_command "$command_name"
done

case "$DB_HOST" in
    localhost|127.0.0.1|::1)
        ;;
    *)
        echo "Refusing to reset non-local database host: ${DB_HOST}" >&2
        exit 1
        ;;
esac

if [[ -z "$BACKUP_FILE" ]]; then
    BACKUP_FILE="$(latest_backup)"
fi
if [[ -z "$BACKUP_FILE" || ! -f "$BACKUP_FILE" ]]; then
    echo "Backup file not found: ${BACKUP_FILE:-<none>}" >&2
    exit 1
fi
BACKUP_FILE="$(cd -- "$(dirname -- "$BACKUP_FILE")" && pwd)/$(basename -- "$BACKUP_FILE")"

validate_backup "$BACKUP_FILE"

echo "Development database reset"
echo "  Database: ${DB_USER}@${DB_HOST}:${DB_PORT}/${DB_NAME}"
echo "  Backup:   ${BACKUP_FILE}"
echo
if [[ "$ASSUME_YES" != true ]]; then
    if [[ ! -t 0 ]]; then
        echo "Interactive confirmation is unavailable. Re-run with --yes." >&2
        exit 1
    fi
    read -r -p "Drop and restore the development database? Type RESET: " confirmation
    if [[ "$confirmation" != "RESET" ]]; then
        echo "Canceled."
        exit 0
    fi
fi

trap cleanup EXIT INT TERM

if [[ "$START_DB" == true ]]; then
    require_command docker
    (cd "$ROOT" && docker compose up -d db)
fi

stop_local_backends

export PGPASSWORD="$DB_PASSWORD"
psql \
    --host "$DB_HOST" \
    --port "$DB_PORT" \
    --username "$DB_USER" \
    --dbname "$DB_NAME" \
    --set ON_ERROR_STOP=1 \
    --command "
        SELECT pg_terminate_backend(pid)
        FROM pg_stat_activity
        WHERE datname = current_database()
          AND pid <> pg_backend_pid();
    " \
    --command "DROP SCHEMA public CASCADE;" \
    --command "CREATE SCHEMA public AUTHORIZATION ${DB_USER};"

restore_backup "$BACKUP_FILE"
echo "Backup restored."

apply_flyway_and_validate_schema
print_verification

echo "Development database reset completed."
