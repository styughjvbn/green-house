#!/usr/bin/env bash
set -euo pipefail

REHEARSAL_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
REHEARSAL_BACKUP="${1:-}"

if [[ -z "${REHEARSAL_BACKUP}" || ! -f "${REHEARSAL_BACKUP}" ]]; then
    echo "Usage: $0 /absolute/path/to/green-house_20260914_030001.dump.gz" >&2
    exit 64
fi

case "${REHEARSAL_BACKUP}" in
    *.dump|*.dump.gz) ;;
    *) echo "Backup must be a PostgreSQL custom dump (.dump or .dump.gz)." >&2; exit 64 ;;
esac

for REHEARSAL_COMMAND in docker psql pg_restore gzip sha256sum; do
    command -v "${REHEARSAL_COMMAND}" >/dev/null || {
        echo "Required command is missing: ${REHEARSAL_COMMAND}" >&2
        exit 69
    }
done

REHEARSAL_CONTAINER="greenhouse-audit-v27-$PPID-$$"
REHEARSAL_PASSWORD="orchid-audit-rehearsal"
REHEARSAL_DATABASE="greenhouse_rehearsal"

cleanup_rehearsal() {
    docker rm -f "${REHEARSAL_CONTAINER}" >/dev/null 2>&1 || true
}
trap cleanup_rehearsal EXIT

docker run --rm -d --name "${REHEARSAL_CONTAINER}" \
    -e POSTGRES_PASSWORD="${REHEARSAL_PASSWORD}" \
    -e POSTGRES_DB="${REHEARSAL_DATABASE}" \
    -p 127.0.0.1::5432 postgres:14-alpine >/dev/null

REHEARSAL_PORT="$(docker port "${REHEARSAL_CONTAINER}" 5432/tcp | sed 's/.*://')"
until PGPASSWORD="${REHEARSAL_PASSWORD}" pg_isready \
    -h 127.0.0.1 -p "${REHEARSAL_PORT}" -U postgres -d "${REHEARSAL_DATABASE}" >/dev/null; do
    sleep 1
done

if [[ "${REHEARSAL_BACKUP}" == *.gz ]]; then
    gzip -cd "${REHEARSAL_BACKUP}" | PGPASSWORD="${REHEARSAL_PASSWORD}" pg_restore \
        --no-owner --no-privileges -h 127.0.0.1 -p "${REHEARSAL_PORT}" \
        -U postgres -d "${REHEARSAL_DATABASE}"
else
    PGPASSWORD="${REHEARSAL_PASSWORD}" pg_restore --no-owner --no-privileges \
        -h 127.0.0.1 -p "${REHEARSAL_PORT}" -U postgres \
        -d "${REHEARSAL_DATABASE}" "${REHEARSAL_BACKUP}"
fi

canonical_snapshot_sha() {
    PGPASSWORD="${REHEARSAL_PASSWORD}" psql -XAt \
        -h 127.0.0.1 -p "${REHEARSAL_PORT}" -U postgres -d "${REHEARSAL_DATABASE}" \
        -c "SELECT jsonb_build_object(
              'id', id, 'quantity', quantity, 'reservedQuantity', reserved_quantity,
              'status', status, 'bedZoneId', bed_zone_id, 'sortOrder', sort_order,
              'startPosition', start_position, 'endPosition', end_position,
              'varietyId', variety_id, 'genus', genus, 'varietyName', variety_name,
              'ageYear', age_year, 'potSizeCode', pot_size_code,
              'placementType', placement_type, 'trayCount', tray_count,
              'splitPlacementAllowed', split_placement_allowed,
              'inboundRecordId', inbound_record_id, 'memo', memo
            )::text FROM orchid_groups ORDER BY id" | sha256sum | awk '{print $1}'
}

REHEARSAL_BEFORE_SHA="$(canonical_snapshot_sha)"

(
    cd "${REHEARSAL_ROOT}/backend"
    DATABASE_URL="jdbc:postgresql://127.0.0.1:${REHEARSAL_PORT}/${REHEARSAL_DATABASE}" \
    DATABASE_USERNAME=postgres \
    DATABASE_PASSWORD="${REHEARSAL_PASSWORD}" \
    ./gradlew bootRun --no-daemon --args='--spring.main.web-application-type=none --app.orchid-ledger.startup-guard-enabled=false --app.settlement.rebuild-on-startup=false'
)

REHEARSAL_AFTER_SHA="$(canonical_snapshot_sha)"
if [[ "${REHEARSAL_BEFORE_SHA}" != "${REHEARSAL_AFTER_SHA}" ]]; then
    echo "Canonical OrchidGroup snapshot changed: before=${REHEARSAL_BEFORE_SHA} after=${REHEARSAL_AFTER_SHA}" >&2
    exit 1
fi

PGPASSWORD="${REHEARSAL_PASSWORD}" psql -X -v ON_ERROR_STOP=1 \
    -h 127.0.0.1 -p "${REHEARSAL_PORT}" -U postgres -d "${REHEARSAL_DATABASE}" \
    -f "${REHEARSAL_ROOT}/scripts/data-audit/verify-orchid-audit-provenance.sql"

(
    cd "${REHEARSAL_ROOT}/backend"
    DATABASE_URL="jdbc:postgresql://127.0.0.1:${REHEARSAL_PORT}/${REHEARSAL_DATABASE}" \
    DATABASE_USERNAME=postgres \
    DATABASE_PASSWORD="${REHEARSAL_PASSWORD}" \
    ./gradlew orchidLedgerReconcile --no-daemon --args='--debug=false --logging.level.org.hibernate.SQL=OFF'
)

echo "Canonical OrchidGroup snapshot unchanged: ${REHEARSAL_AFTER_SHA}"
