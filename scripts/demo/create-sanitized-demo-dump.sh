#!/usr/bin/env bash
set -euo pipefail
umask 077

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OPERATION_LOCK_FILE="${GREENHOUSE_OPERATION_LOCK_FILE:-/tmp/green-house-operation.lock}"
PRODUCTION_DB_NAME="${PRODUCTION_DB_NAME:-greenhouse}"

fail() { echo "[ERROR] $*" >&2; exit 1; }
require_command() { command -v "$1" >/dev/null 2>&1 || fail "Command not found: $1"; }

main() {
  [[ $# -eq 1 ]] || fail "Usage: $0 <sanitized-output.dump>"
  require_command flock
  require_command install
  require_command mktemp
  require_command pg_dump
  require_command pg_restore
  require_command psql
  require_command sha256sum
  [[ "${PRODUCTION_DB_NAME}" == "greenhouse" ]] || fail "PRODUCTION_DB_NAME must be exactly greenhouse"
  [[ "${DEMO_SOURCE_DUMP_CONFIRM:-}" == "greenhouse" ]] || fail "Set DEMO_SOURCE_DUMP_CONFIRM=greenhouse"
  [[ -n "${PRODUCTION_DB_URL:-}" ]] || fail "PRODUCTION_DB_URL is required"
  [[ "$(psql "${PRODUCTION_DB_URL}" -Atqc "SELECT current_database()||':'||current_user")" == "greenhouse:greenhouse" ]] \
    || fail "PRODUCTION_DB_URL must target greenhouse as role greenhouse"

  if [[ "${GREENHOUSE_OPERATION_LOCK_HELD:-false}" != "true" ]]; then
    exec 9>"${OPERATION_LOCK_FILE}"
    flock -n 9 || fail "Another deployment or demo refresh is running: ${OPERATION_LOCK_FILE}"
  fi

  local output="$1"
  local staging_root staging_dir source_dir sanitized_dir raw_dump staged_output
  local partial_output partial_checksum published=false
  [[ "${output}" == *.dump ]] || fail "Output must use the .dump extension"
  [[ ! -e "${output}" && ! -e "${output}.sha256" ]] || fail "Output already exists: ${output}"
  mkdir -p "$(dirname "${output}")"

  : "${HOME:?HOME is required to select a Docker-accessible staging directory}"
  staging_root="${DEMO_DOCKER_STAGING_DIR:-${HOME}/green-house-demo-refresh-staging}"
  [[ "${staging_root}" == "${HOME}"/* ]] \
    || fail "DEMO_DOCKER_STAGING_DIR must be inside HOME so snap Docker can mount it"
  mkdir -p "${staging_root}"
  chmod 700 "${staging_root}"
  staging_dir="$(mktemp -d "${staging_root}/run.XXXXXX")"
  source_dir="${staging_dir}/source"
  sanitized_dir="${staging_dir}/sanitized"
  mkdir -m 700 "${source_dir}" "${sanitized_dir}"
  raw_dump="${source_dir}/greenhouse-production.dump"
  staged_output="${sanitized_dir}/$(basename "${output}")"
  partial_output="${output}.partial.$$"
  partial_checksum="${output}.sha256.partial.$$"
  cleanup() {
    rm -f -- \
      "${raw_dump}" \
      "${staged_output}" \
      "${staged_output}.sha256" \
      "${partial_output}" \
      "${partial_checksum}"
    if [[ "${published}" != "true" ]]; then
      rm -f -- "${output}" "${output}.sha256"
    fi
    rmdir "${source_dir}" "${sanitized_dir}" "${staging_dir}" 2>/dev/null || true
  }
  trap cleanup EXIT

  pg_dump "${PRODUCTION_DB_URL}" --format=custom --file="${raw_dump}"
  pg_restore --list "${raw_dump}" >/dev/null
  "${SCRIPT_DIR}/docker-sanitize.sh" "${raw_dump}" "${staged_output}"
  (
    cd "${sanitized_dir}"
    sha256sum --check "$(basename "${staged_output}").sha256"
  )

  install -m 600 "${staged_output}" "${partial_output}"
  install -m 600 "${staged_output}.sha256" "${partial_checksum}"
  mv -- "${partial_output}" "${output}"
  mv -- "${partial_checksum}" "${output}.sha256"
  published=true
  cleanup
  trap - EXIT
  echo "Production source dump removed after sanitization: ${raw_dump}"
  echo "Sanitized demo dump published: ${output}"
}

main "$@"
