#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
PIDS="${ROOT}/.dev-pids"

stop_process_tree() {
    local pid="$1"

    if ! kill -0 "$pid" 2>/dev/null; then
        return 0
    fi

    local child_pid

    while read -r child_pid; do
        if [[ -n "$child_pid" ]]; then
            stop_process_tree "$child_pid"
        fi
    done < <(pgrep -P "$pid" 2>/dev/null || true)

    kill "$pid" 2>/dev/null || true

    for _ in {1..10}; do
        if ! kill -0 "$pid" 2>/dev/null; then
            return 0
        fi

        sleep 0.5
    done

    kill -9 "$pid" 2>/dev/null || true
}

stop_process_from_pid_file() {
    local name="$1"
    local pid_file="$2"

    if [[ ! -f "$pid_file" ]]; then
        return 0
    fi

    local pid
    pid="$(cat "$pid_file" 2>/dev/null || true)"

    if [[ "$pid" =~ ^[0-9]+$ ]] && kill -0 "$pid" 2>/dev/null; then
        stop_process_tree "$pid"
        echo "Stopped ${name} process ${pid}"
    fi

    rm -f "$pid_file"
}

stop_process_by_port() {
    local port="$1"
    local pids

    pids="$(
        ss -ltnp "sport = :${port}" 2>/dev/null |
            sed -n 's/.*pid=\([0-9]\+\).*/\1/p' |
            sort -u
    )"

    if [[ -z "$pids" ]] && command -v fuser >/dev/null 2>&1; then
        pids="$(fuser "${port}/tcp" 2>/dev/null || true)"
    fi

    local pid

    for pid in $pids; do
        if [[ "$pid" =~ ^[0-9]+$ ]] && kill -0 "$pid" 2>/dev/null; then
            stop_process_tree "$pid"
            echo "Stopped process ${pid} on port ${port}"
        fi
    done
}

stop_matching_processes() {
    local patterns=(
        "${ROOT}/frontend.*next (dev|start)"
        "${ROOT}/backend.*bootRun"
        "com\.greenhouse\.backend\.BackendApplication"
    )

    local pattern
    local pid

    for pattern in "${patterns[@]}"; do
        while read -r pid; do
            if [[ -z "$pid" || "$pid" == "$$" || "$pid" == "$PPID" ]]; then
                continue
            fi

            if kill -0 "$pid" 2>/dev/null; then
                stop_process_tree "$pid"
                echo "Stopped matching process ${pid}"
            fi
        done < <(pgrep -f "$pattern" 2>/dev/null || true)
    done
}

cd "$ROOT"

stop_process_from_pid_file \
    "frontend" \
    "${PIDS}/frontend.pid"

stop_process_from_pid_file \
    "backend" \
    "${PIDS}/backend.pid"

# PID 파일과 실제 서버 프로세스가 달라졌을 때를 대비한 보조 정리
stop_process_by_port 3000
stop_process_by_port 8080
stop_matching_processes

docker compose stop db

rmdir "$PIDS" 2>/dev/null || true

echo "Development servers stopped."
