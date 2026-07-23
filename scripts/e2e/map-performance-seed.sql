\set ON_ERROR_STOP on

BEGIN;

CREATE TABLE map_performance_seed_manifest (
    metric_key VARCHAR(80) PRIMARY KEY,
    metric_value JSONB NOT NULL
);

INSERT INTO varieties (
    created_at,
    updated_at,
    code,
    genus,
    name,
    default_pot_size,
    sale_enabled,
    is_active
) VALUES (
    TIMESTAMP '2026-01-01 00:00:00',
    TIMESTAMP '2026-01-01 00:00:00',
    'MAP-E2E-001',
    '팔레놉시스',
    '맵 성능 기준 난',
    '3.5치',
    TRUE,
    TRUE
);

INSERT INTO orchid_groups (
    created_at,
    updated_at,
    age_year,
    genus,
    placement_type,
    pot_size,
    pot_size_code,
    quantity,
    sort_order,
    status,
    variety_name,
    bed_zone_id,
    split_placement_allowed,
    variety_id,
    start_position,
    end_position,
    reserved_quantity
)
SELECT
    TIMESTAMP '2026-01-01 00:00:00',
    TIMESTAMP '2026-01-01 00:00:00',
    2,
    '팔레놉시스',
    'POT',
    '3.5치',
    'POT_3_5',
    1,
    cell_number,
    '정상',
    '맵 기준 난 H' || houses.number || '-B' || physical_beds.number ||
        '-Z' || bed_zones.sort_order || '-C' || LPAD(cell_number::text, 2, '0'),
    bed_zones.id,
    FALSE,
    varieties.id,
    cell_number - 1,
    cell_number,
    0
FROM houses
JOIN physical_beds ON physical_beds.house_id = houses.id
JOIN bed_zones ON bed_zones.physical_bed_id = physical_beds.id
CROSS JOIN LATERAL generate_series(
    1,
    FLOOR(physical_beds.position_unit_count)::integer
) AS cells(cell_number)
CROSS JOIN varieties
WHERE varieties.code = 'MAP-E2E-001'
ORDER BY houses.number, physical_beds.number, bed_zones.sort_order, cell_number;

WITH pesticide AS (
    SELECT id FROM work_types WHERE code = 'PESTICIDE'
)
INSERT INTO work_operations (
    work_type_id,
    title,
    status,
    planned_start_date,
    actual_start_at,
    actual_end_at,
    source_scope_type,
    source_scope_id,
    source_condition_snapshot,
    target_snapshot_at,
    details,
    worker,
    memo,
    request_key,
    version,
    created_at,
    updated_at
)
SELECT
    pesticide.id,
    'E2E-DIRECT-G' || orchid_groups.id || '-W' || LPAD(sequence_no::text, 3, '0'),
    'COMPLETED',
    DATE '2026-04-01' + (sequence_no - 1),
    (DATE '2026-04-01' + (sequence_no - 1))::timestamp + INTERVAL '9 hours',
    (DATE '2026-04-01' + (sequence_no - 1))::timestamp + INTERVAL '10 hours',
    'ORCHID_GROUP',
    orchid_groups.id,
    jsonb_build_object('seedScope', 'DIRECT', 'sequence', sequence_no),
    TIMESTAMP '2026-06-01 00:00:00',
    jsonb_build_object('seedScope', 'DIRECT', 'sequence', sequence_no),
    'map-e2e',
    '난 묶음 직접 작업 기준 데이터',
    'MAP-E2E-DIRECT-' || orchid_groups.id || '-' || sequence_no,
    0,
    TIMESTAMP '2026-06-01 00:00:00',
    TIMESTAMP '2026-06-01 00:00:00'
FROM orchid_groups
CROSS JOIN generate_series(1, 60) AS work_sequence(sequence_no)
CROSS JOIN pesticide;

WITH pesticide AS (
    SELECT id FROM work_types WHERE code = 'PESTICIDE'
)
INSERT INTO work_operations (
    work_type_id, title, status, planned_start_date, actual_start_at, actual_end_at,
    source_scope_type, source_scope_id, source_condition_snapshot,
    target_snapshot_at, details, worker, memo, request_key, version, created_at, updated_at
)
SELECT
    pesticide.id,
    'E2E-ZONE-Z' || bed_zones.id || '-W' || LPAD(sequence_no::text, 3, '0'),
    'COMPLETED',
    DATE '2026-03-01' + (sequence_no - 1),
    (DATE '2026-03-01' + (sequence_no - 1))::timestamp + INTERVAL '9 hours',
    (DATE '2026-03-01' + (sequence_no - 1))::timestamp + INTERVAL '10 hours',
    'BED_ZONE', bed_zones.id,
    jsonb_build_object('seedScope', 'BED_ZONE', 'sequence', sequence_no),
    TIMESTAMP '2026-06-01 00:00:00',
    jsonb_build_object('seedScope', 'BED_ZONE', 'sequence', sequence_no),
    'map-e2e', '구역 작업 기준 데이터',
    'MAP-E2E-ZONE-' || bed_zones.id || '-' || sequence_no,
    0, TIMESTAMP '2026-06-01 00:00:00', TIMESTAMP '2026-06-01 00:00:00'
FROM bed_zones
CROSS JOIN generate_series(1, 20) AS work_sequence(sequence_no)
CROSS JOIN pesticide;

WITH pesticide AS (
    SELECT id FROM work_types WHERE code = 'PESTICIDE'
)
INSERT INTO work_operations (
    work_type_id, title, status, planned_start_date, actual_start_at, actual_end_at,
    source_scope_type, source_scope_id, source_condition_snapshot,
    target_snapshot_at, details, worker, memo, request_key, version, created_at, updated_at
)
SELECT
    pesticide.id,
    'E2E-BED-B' || physical_beds.id || '-W' || LPAD(sequence_no::text, 3, '0'),
    'COMPLETED',
    DATE '2026-02-01' + (sequence_no - 1),
    (DATE '2026-02-01' + (sequence_no - 1))::timestamp + INTERVAL '9 hours',
    (DATE '2026-02-01' + (sequence_no - 1))::timestamp + INTERVAL '10 hours',
    'PHYSICAL_BED', physical_beds.id,
    jsonb_build_object('seedScope', 'PHYSICAL_BED', 'sequence', sequence_no),
    TIMESTAMP '2026-06-01 00:00:00',
    jsonb_build_object('seedScope', 'PHYSICAL_BED', 'sequence', sequence_no),
    'map-e2e', '다이 작업 기준 데이터',
    'MAP-E2E-BED-' || physical_beds.id || '-' || sequence_no,
    0, TIMESTAMP '2026-06-01 00:00:00', TIMESTAMP '2026-06-01 00:00:00'
FROM physical_beds
CROSS JOIN generate_series(1, 10) AS work_sequence(sequence_no)
CROSS JOIN pesticide;

WITH pesticide AS (
    SELECT id FROM work_types WHERE code = 'PESTICIDE'
)
INSERT INTO work_operations (
    work_type_id, title, status, planned_start_date, actual_start_at, actual_end_at,
    source_scope_type, source_scope_id, source_condition_snapshot,
    target_snapshot_at, details, worker, memo, request_key, version, created_at, updated_at
)
SELECT
    pesticide.id,
    'E2E-HOUSE-H' || houses.id || '-W' || LPAD(sequence_no::text, 3, '0'),
    'COMPLETED',
    DATE '2026-01-01' + (sequence_no - 1),
    (DATE '2026-01-01' + (sequence_no - 1))::timestamp + INTERVAL '9 hours',
    (DATE '2026-01-01' + (sequence_no - 1))::timestamp + INTERVAL '10 hours',
    'HOUSE', houses.id,
    jsonb_build_object('seedScope', 'HOUSE', 'sequence', sequence_no),
    TIMESTAMP '2026-06-01 00:00:00',
    jsonb_build_object('seedScope', 'HOUSE', 'sequence', sequence_no),
    'map-e2e', '동 작업 기준 데이터',
    'MAP-E2E-HOUSE-' || houses.id || '-' || sequence_no,
    0, TIMESTAMP '2026-06-01 00:00:00', TIMESTAMP '2026-06-01 00:00:00'
FROM houses
CROSS JOIN generate_series(1, 10) AS work_sequence(sequence_no)
CROSS JOIN pesticide;

INSERT INTO work_operation_targets (
    work_operation_id,
    orchid_group_id,
    target_reference_type,
    inclusion_source,
    source_reference_id,
    included_at,
    variety_id_snapshot,
    variety_name_snapshot,
    age_year_snapshot,
    pot_size_code_snapshot,
    pot_size_snapshot,
    quantity_snapshot,
    location_snapshot,
    created_at
)
SELECT
    work_operations.id,
    orchid_groups.id,
    'ORCHID_GROUP',
    'DIRECT',
    work_operations.source_scope_id,
    TIMESTAMP '2026-06-01 00:00:00',
    orchid_groups.variety_id,
    orchid_groups.variety_name,
    orchid_groups.age_year,
    orchid_groups.pot_size_code,
    orchid_groups.pot_size,
    orchid_groups.quantity,
    jsonb_build_object(
        'houseId', houses.id,
        'houseNumber', houses.number,
        'physicalBedId', physical_beds.id,
        'physicalBedNumber', physical_beds.number,
        'bedZoneId', bed_zones.id,
        'bedZoneName', bed_zones.name
    ),
    TIMESTAMP '2026-06-01 00:00:00'
FROM work_operations
JOIN orchid_groups ON orchid_groups.id = work_operations.source_scope_id
JOIN bed_zones ON bed_zones.id = orchid_groups.bed_zone_id
JOIN physical_beds ON physical_beds.id = bed_zones.physical_bed_id
JOIN houses ON houses.id = physical_beds.house_id
WHERE work_operations.request_key LIKE 'MAP-E2E-DIRECT-%'
UNION ALL
SELECT
    work_operations.id,
    orchid_groups.id,
    'ORCHID_GROUP',
    'BED_ZONE',
    work_operations.source_scope_id,
    TIMESTAMP '2026-06-01 00:00:00',
    orchid_groups.variety_id,
    orchid_groups.variety_name,
    orchid_groups.age_year,
    orchid_groups.pot_size_code,
    orchid_groups.pot_size,
    orchid_groups.quantity,
    jsonb_build_object(
        'houseId', houses.id,
        'houseNumber', houses.number,
        'physicalBedId', physical_beds.id,
        'physicalBedNumber', physical_beds.number,
        'bedZoneId', bed_zones.id,
        'bedZoneName', bed_zones.name
    ),
    TIMESTAMP '2026-06-01 00:00:00'
FROM work_operations
JOIN bed_zones ON bed_zones.id = work_operations.source_scope_id
JOIN orchid_groups ON orchid_groups.bed_zone_id = bed_zones.id
JOIN physical_beds ON physical_beds.id = bed_zones.physical_bed_id
JOIN houses ON houses.id = physical_beds.house_id
WHERE work_operations.request_key LIKE 'MAP-E2E-ZONE-%'
UNION ALL
SELECT
    work_operations.id,
    orchid_groups.id,
    'ORCHID_GROUP',
    work_operations.source_scope_type,
    work_operations.source_scope_id,
    TIMESTAMP '2026-06-01 00:00:00',
    orchid_groups.variety_id,
    orchid_groups.variety_name,
    orchid_groups.age_year,
    orchid_groups.pot_size_code,
    orchid_groups.pot_size,
    orchid_groups.quantity,
    jsonb_build_object(
        'houseId', houses.id,
        'houseNumber', houses.number,
        'physicalBedId', physical_beds.id,
        'physicalBedNumber', physical_beds.number,
        'bedZoneId', bed_zones.id,
        'bedZoneName', bed_zones.name
    ),
    TIMESTAMP '2026-06-01 00:00:00'
FROM work_operations
JOIN houses ON work_operations.source_scope_type = 'HOUSE'
    AND houses.id = work_operations.source_scope_id
JOIN physical_beds ON physical_beds.house_id = houses.id
JOIN bed_zones ON bed_zones.physical_bed_id = physical_beds.id
JOIN orchid_groups ON orchid_groups.bed_zone_id = bed_zones.id
WHERE work_operations.request_key LIKE 'MAP-E2E-HOUSE-%'
UNION ALL
SELECT
    work_operations.id,
    orchid_groups.id,
    'ORCHID_GROUP',
    work_operations.source_scope_type,
    work_operations.source_scope_id,
    TIMESTAMP '2026-06-01 00:00:00',
    orchid_groups.variety_id,
    orchid_groups.variety_name,
    orchid_groups.age_year,
    orchid_groups.pot_size_code,
    orchid_groups.pot_size,
    orchid_groups.quantity,
    jsonb_build_object(
        'houseId', houses.id,
        'houseNumber', houses.number,
        'physicalBedId', physical_beds.id,
        'physicalBedNumber', physical_beds.number,
        'bedZoneId', bed_zones.id,
        'bedZoneName', bed_zones.name
    ),
    TIMESTAMP '2026-06-01 00:00:00'
FROM work_operations
JOIN physical_beds ON work_operations.source_scope_type = 'PHYSICAL_BED'
    AND physical_beds.id = work_operations.source_scope_id
JOIN houses ON houses.id = physical_beds.house_id
JOIN bed_zones ON bed_zones.physical_bed_id = physical_beds.id
JOIN orchid_groups ON orchid_groups.bed_zone_id = bed_zones.id
WHERE work_operations.request_key LIKE 'MAP-E2E-BED-%';

INSERT INTO work_target_executions (
    work_operation_target_id,
    status,
    started_at,
    completed_at,
    worker,
    processed_quantity,
    version,
    created_at,
    updated_at
)
SELECT
    work_operation_targets.id,
    'COMPLETED',
    work_operations.actual_start_at,
    work_operations.actual_end_at,
    'map-e2e',
    work_operation_targets.quantity_snapshot,
    0,
    TIMESTAMP '2026-06-01 00:00:00',
    TIMESTAMP '2026-06-01 00:00:00'
FROM work_operation_targets
JOIN work_operations ON work_operations.id = work_operation_targets.work_operation_id
WHERE work_operations.request_key LIKE 'MAP-E2E-%';

INSERT INTO map_performance_seed_manifest(metric_key, metric_value)
SELECT 'farm_layout', jsonb_build_object(
    'houseCount', (SELECT COUNT(*) FROM houses),
    'physicalBedCount', (SELECT COUNT(*) FROM physical_beds),
    'bedZoneCount', (SELECT COUNT(*) FROM bed_zones),
    'orchidGroupCount', (SELECT COUNT(*) FROM orchid_groups),
    'positionUnitCountSum', (SELECT SUM(position_unit_count) FROM physical_beds)
);

INSERT INTO map_performance_seed_manifest(metric_key, metric_value)
WITH history_totals AS (
    SELECT orchid_group_id, COUNT(*) AS history_count
    FROM work_operation_targets
    GROUP BY orchid_group_id
), operation_totals AS (
    SELECT
        COUNT(DISTINCT work_operations.id) AS operation_count,
        COUNT(work_operation_targets.id) AS target_count
    FROM work_operations
    JOIN work_operation_targets
        ON work_operation_targets.work_operation_id = work_operations.id
    WHERE work_operations.request_key LIKE 'MAP-E2E-%'
)
SELECT 'work_history', jsonb_build_object(
    'operationCount', operation_totals.operation_count,
    'targetCount', operation_totals.target_count,
    'historyPerOrchidGroupMin', MIN(history_totals.history_count),
    'historyPerOrchidGroupMax', MAX(history_totals.history_count)
)
FROM history_totals
CROSS JOIN operation_totals
GROUP BY operation_totals.operation_count, operation_totals.target_count;

COMMIT;

ANALYZE houses;
ANALYZE physical_beds;
ANALYZE bed_zones;
ANALYZE orchid_groups;
ANALYZE work_operations;
ANALYZE work_operation_targets;
ANALYZE work_target_executions;
