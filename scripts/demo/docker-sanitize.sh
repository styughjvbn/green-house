#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="${SCRIPT_DIR}/docker-compose.sanitize.yml"
PROJECT_NAME="${SANITIZE_COMPOSE_PROJECT_NAME:-green-house-sanitize}"

fail() {
  echo "[ERROR] $*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Command not found: $1"
}

absolute_path() {
  local input="$1"
  local directory
  directory="$(cd "$(dirname "${input}")" && pwd -P)"
  printf '%s/%s\n' "${directory}" "$(basename "${input}")"
}

compose() {
  docker compose --project-name "${PROJECT_NAME}" --file "${COMPOSE_FILE}" "$@"
}

validate_environment() {
  DEMO_ANONYMIZATION_KEY="${DEMO_ANONYMIZATION_KEY:-}"
  [[ ${#DEMO_ANONYMIZATION_KEY} -ge 32 ]] \
    || fail "DEMO_ANONYMIZATION_KEY must contain at least 32 characters"
  [[ "${DEMO_DATE_SHIFT_DAYS:-}" =~ ^-?[0-9]+$ && "${DEMO_DATE_SHIFT_DAYS}" != "0" ]] \
    || fail "DEMO_DATE_SHIFT_DAYS must be a non-zero integer"
  [[ "${DEMO_QUANTITY_FACTOR:-}" =~ ^[2-9]$ ]] \
    || fail "DEMO_QUANTITY_FACTOR must be an integer from 2 to 9"
  [[ "${DEMO_PRICE_FACTOR:-}" =~ ^[2-9]$ ]] \
    || fail "DEMO_PRICE_FACTOR must be an integer from 2 to 9"
}

main() {
  [[ $# -eq 2 ]] || fail "Usage: $0 <production.dump|production.dump.gz> <greenhouse_demo_sanitized.dump>"

  require_command docker
  require_command openssl
  docker compose version >/dev/null 2>&1 || fail "Docker Compose v2 is required"
  validate_environment

  local source_dump output_dump
  source_dump="$(absolute_path "$1")"
  output_dump="$(absolute_path "$2")"
  [[ -f "${source_dump}" ]] || fail "Backup not found: ${source_dump}"
  [[ "${source_dump}" == *.dump || "${source_dump}" == *.dump.gz ]] \
    || fail "Input must use the .dump or .dump.gz extension"
  [[ "${output_dump}" == *.dump ]] || fail "Output must use the .dump extension"
  [[ ! -e "${output_dump}" ]] || fail "Output already exists: ${output_dump}"
  [[ ! -e "${output_dump}.sha256" ]] || fail "Checksum already exists: ${output_dump}.sha256"

  export SANITIZE_INPUT_DIR="$(dirname "${source_dump}")"
  export SANITIZE_OUTPUT_DIR="$(dirname "${output_dump}")"
  export SANITIZE_POSTGRES_PASSWORD="${SANITIZE_POSTGRES_PASSWORD:-$(openssl rand -hex 32)}"

  cleanup() {
    compose down --volumes --remove-orphans >/dev/null 2>&1 || true
  }
  trap cleanup EXIT

  compose build runner
  compose up --detach db

  local attempt database_ready=false
  for attempt in $(seq 1 60); do
    if compose exec --no-TTY db pg_isready -U sanitize_admin -d postgres >/dev/null 2>&1; then
      database_ready=true
      break
    fi
    sleep 1
  done
  [[ "${database_ready}" == "true" ]] || fail "Temporary PostgreSQL did not become ready"

  compose run --rm runner ./scripts/demo/restore-temp-db.sh "/input/$(basename "${source_dump}")"
  compose run --rm runner ./scripts/demo/create-demo-dump.sh "/output/$(basename "${output_dump}")"

  echo "Docker sanitization completed: ${output_dump}"
  echo "Checksum completed: ${output_dump}.sha256"
  echo "Temporary PostgreSQL volume will be removed now."
}

main "$@"
