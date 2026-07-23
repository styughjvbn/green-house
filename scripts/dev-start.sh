#!/usr/bin/env bash

set -Eeuo pipefail

NO_DB=false
LAN=false
FRONTEND_PRODUCTION=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        --frontend-production)
            FRONTEND_PRODUCTION=true
            shift
            ;;
        --lan)
            LAN=true
            shift
            ;;
        --no-db)
            NO_DB=true
            shift
            ;;
        *)
            echo "Unknown option: $1" >&2
            echo "Usage: $0 [--no-db] [--lan] [--frontend-production]" >&2
            exit 1
            ;;
    esac
done

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
LOGS="${ROOT}/logs"
PIDS="${ROOT}/.dev-pids"

mkdir -p "$LOGS" "$PIDS"

test_port_listening() {
    local port="$1"

    ss -ltnH "sport = :${port}" 2>/dev/null | grep -q .
}

wait_http_ready() {
    local url="$1"
    local name="$2"
    local timeout_seconds="${3:-90}"
    local started_at
    local current_time
    local status_code

    started_at="$(date +%s)"

    while true; do
        status_code="$(
            curl \
                --silent \
                --output /dev/null \
                --write-out "%{http_code}" \
                --max-time 3 \
                "$url" 2>/dev/null || true
        )"

        if [[ "$status_code" =~ ^[234][0-9][0-9]$ ]]; then
            echo "${name} ready: ${url}"
            return 0
        fi

        current_time="$(date +%s)"

        if (( current_time - started_at >= timeout_seconds )); then
            echo "${name} did not become ready in ${timeout_seconds} seconds." >&2
            return 1
        fi

        sleep 2
    done
}

require_command() {
    local command_name="$1"

    if ! command -v "$command_name" >/dev/null 2>&1; then
        echo "Required command not found: ${command_name}" >&2
        exit 1
    fi
}

require_command curl
require_command ss
require_command docker
require_command npm
if [[ "$LAN" == true ]]; then
    require_command ip
fi

cd "$ROOT"

if [[ "$NO_DB" == false ]]; then
    echo "Starting PostgreSQL..."
    docker compose up -d db
fi

if test_port_listening 8080; then
    echo "Backend already listening on http://localhost:8080"
else
    echo "Starting backend..."

    BACKEND_OUT="${LOGS}/backend.out.log"
    BACKEND_ERR="${LOGS}/backend.err.log"
    BACKEND_PID_FILE="${PIDS}/backend.pid"

    if [[ ! -f "${ROOT}/backend/gradlew" ]]; then
        echo "Gradle wrapper not found: ${ROOT}/backend/gradlew" >&2
        exit 1
    fi

    chmod +x "${ROOT}/backend/gradlew"

    (
        cd "${ROOT}/backend"

        nohup ./gradlew bootRun \
            >"$BACKEND_OUT" \
            2>"$BACKEND_ERR" &

        echo "$!" >"$BACKEND_PID_FILE"
    )

    echo "Backend PID: $(cat "$BACKEND_PID_FILE")"
fi

if ! wait_http_ready \
    "http://localhost:8080/actuator/health" \
    "Backend" \
    90; then
    echo "Backend logs:"
    echo "  ${LOGS}/backend.out.log"
    echo "  ${LOGS}/backend.err.log"
    exit 1
fi

if test_port_listening 3000 && [[ "$FRONTEND_PRODUCTION" == true ]]; then
    echo "Frontend production mode requires port 3000 to be free." >&2
    echo "Stop the existing frontend before running this command." >&2
    exit 1
elif test_port_listening 3000; then
    echo "Frontend already listening on http://localhost:3000"
else
    FRONTEND_OUT="${LOGS}/frontend.out.log"
    FRONTEND_ERR="${LOGS}/frontend.err.log"
    FRONTEND_PID_FILE="${PIDS}/frontend.pid"

    (
        cd "${ROOT}/frontend"

        if [[ "$FRONTEND_PRODUCTION" == true ]]; then
            echo "Building frontend for production..."
            npm run build
            echo "Starting frontend from production build..."

            if [[ "$LAN" == true ]]; then
                nohup npm run start -- --hostname 0.0.0.0 \
                    >"$FRONTEND_OUT" \
                    2>"$FRONTEND_ERR" &
            else
                nohup npm run start \
                    >"$FRONTEND_OUT" \
                    2>"$FRONTEND_ERR" &
            fi
        elif [[ "$LAN" == true ]]; then
            echo "Starting frontend development server..."
            nohup npm run dev -- --hostname 0.0.0.0 \
                >"$FRONTEND_OUT" \
                2>"$FRONTEND_ERR" &
        else
            echo "Starting frontend development server..."
            nohup npm run dev \
                >"$FRONTEND_OUT" \
                2>"$FRONTEND_ERR" &
        fi

        echo "$!" >"$FRONTEND_PID_FILE"
    )

    echo "Frontend PID: $(cat "$FRONTEND_PID_FILE")"
fi

if ! wait_http_ready \
    "http://localhost:3000" \
    "Frontend" \
    90; then
    echo "Frontend logs:"
    echo "  ${LOGS}/frontend.out.log"
    echo "  ${LOGS}/frontend.err.log"
    exit 1
fi

echo
if [[ "$FRONTEND_PRODUCTION" == true ]]; then
    echo "Backend and production frontend are running."
else
    echo "Development servers are running."
fi
echo "Frontend: http://localhost:3000"
if [[ "$LAN" == true ]]; then
    LAN_IP="$(
        ip -4 -o addr show scope global \
            | awk '{print $4}' \
            | cut -d/ -f1 \
            | grep '^192\.168\.0\.' \
            | head -n 1 \
            || true
    )"

    if [[ -n "$LAN_IP" ]]; then
        echo "LAN:      http://${LAN_IP}:3000"
    else
        echo "LAN:      frontend is bound to 0.0.0.0, but no 192.168.0.* IP was found"
    fi
fi
echo "Backend:  http://localhost:8080"
echo "Logs:     ${LOGS}"
