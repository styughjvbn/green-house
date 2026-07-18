UPDATE orchid_groups
SET pot_size_code = CASE
    WHEN pot_size IS NULL OR btrim(pot_size) = '' THEN 'UNSPECIFIED'
    WHEN lower(btrim(pot_size)) IN ('2', '2"', '2치', '2인치') THEN 'POT_2'
    WHEN lower(btrim(pot_size)) IN ('2.5', '2.5"', '2.5치', '2.5인치') THEN 'POT_2_5'
    WHEN lower(btrim(pot_size)) IN ('3', '3"', '3치', '3인치') THEN 'POT_3'
    WHEN lower(btrim(pot_size)) IN ('3.5', '3.5"', '3.5치', '3.5인치') THEN 'POT_3_5'
    WHEN lower(btrim(pot_size)) IN ('4', '4"', '4치', '4인치') THEN 'POT_4'
    WHEN lower(btrim(pot_size)) IN ('4.5', '4.5"', '4.5치', '4.5인치') THEN 'POT_4_5'
    WHEN lower(btrim(pot_size)) IN ('5', '5"', '5치', '5인치') THEN 'POT_5'
    WHEN lower(btrim(pot_size)) IN ('6', '6"', '6치', '6인치') THEN 'POT_6'
    WHEN lower(btrim(pot_size)) IN ('행잉', 'hanging') THEN 'HANGING'
    WHEN lower(btrim(pot_size)) IN ('기타', 'etc') THEN 'ETC'
    ELSE 'UNMAPPED'
END;

INSERT INTO work_types (
    code, name, template, is_active, is_system, is_default, sort_order, created_at, updated_at
) VALUES
    ('MULTI_CREATE', '난 묶음 다중 생성', 'MULTI_CREATE', TRUE, TRUE, TRUE, 11, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('CORRECTION', '구조 변경 보정', 'CORRECTION', TRUE, TRUE, TRUE, 12, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('DIVIDE', '분주', 'REPOT', TRUE, TRUE, TRUE, 11, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('MERGE', '합식', 'REPOT', TRUE, TRUE, TRUE, 12, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('DISCARD', '폐기', 'DISCARD', TRUE, TRUE, TRUE, 13, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    template = EXCLUDED.template,
    is_active = EXCLUDED.is_active,
    is_system = EXCLUDED.is_system,
    is_default = EXCLUDED.is_default,
    sort_order = EXCLUDED.sort_order,
    updated_at = CURRENT_TIMESTAMP;

UPDATE work_types
SET is_system = TRUE,
    updated_at = CURRENT_TIMESTAMP
WHERE code = 'REPOT';

-- Preserve work_records as the immutable migration source.
-- request_key makes the conversion idempotent and traces each V2 operation
-- back to the legacy row that produced it.
INSERT INTO work_operations (
    work_type_id,
    title,
    status,
    planned_start_date,
    planned_end_date,
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
    wr.work_type_id,
    CASE
        WHEN wr.material_name IS NOT NULL AND btrim(wr.material_name) <> ''
            THEN wr.work_type || ' - ' || wr.material_name
        ELSE wr.work_type || ' 작업'
    END,
    CASE WHEN wr.status = 'CANCELED' THEN 'CANCELED' ELSE 'COMPLETED' END,
    wr.work_date,
    wr.work_date,
    wr.work_date::timestamp + time '12:00',
    CASE
        WHEN wr.status = 'CANCELED'
            THEN COALESCE(wr.canceled_at, wr.work_date::timestamp + time '12:00')
        ELSE wr.work_date::timestamp + time '12:00'
    END,
    CASE
        WHEN wr.details ->> 'inboundRecordId' IS NOT NULL THEN 'INBOUND_RECORD_SELECTION'
        WHEN wr.target_type IN ('FARM', 'HOUSE', 'PHYSICAL_BED', 'BED_ZONE', 'ORCHID_GROUP')
            THEN wr.target_type
        ELSE 'NONE'
    END,
    CASE
        WHEN wr.details ->> 'inboundRecordId' IS NOT NULL THEN NULL
        WHEN wr.target_type = 'FARM' THEN NULL
        ELSE wr.target_id
    END,
    jsonb_strip_nulls(jsonb_build_object(
        'migrationSource', 'LEGACY_WORK_RECORD',
        'legacyWorkRecordId', wr.id,
        'legacyTargetType', wr.target_type,
        'legacyTargetId', wr.target_id,
        'inboundRecordIds', CASE
            WHEN wr.details ->> 'inboundRecordId' IS NOT NULL
                THEN jsonb_build_array((wr.details ->> 'inboundRecordId')::bigint)
            ELSE NULL
        END
    )),
    wr.work_date::timestamp + time '12:00',
    jsonb_strip_nulls(COALESCE(wr.details, '{}'::jsonb) || jsonb_build_object(
        'migrationSource', 'LEGACY_WORK_RECORD',
        'legacyWorkRecordId', wr.id,
        'materialName', wr.material_name,
        'dilutionRatio', wr.dilution_ratio,
        'quantity', wr.quantity,
        'fromBedZoneId', wr.from_bed_zone_id,
        'toBedZoneId', wr.to_bed_zone_id,
        'legacyStatus', wr.status,
        'cancelReason', wr.cancel_reason
    )),
    wr.worker,
    wr.memo,
    'LEGACY_WORK_RECORD:' || wr.id,
    0,
    wr.created_at,
    wr.updated_at
FROM work_records wr
WHERE wr.work_type_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM work_operations operation
      WHERE operation.request_key = 'LEGACY_WORK_RECORD:' || wr.id
  );

-- Inbound history points at the real inbound record instead of expanding the
-- legacy FARM target to every orchid group.
INSERT INTO work_operation_targets (
    work_operation_id,
    orchid_group_id,
    target_reference_type,
    inbound_record_id,
    inclusion_source,
    source_reference_id,
    included_at,
    excluded_at,
    exclusion_reason,
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
    operation.id,
    NULL,
    'INBOUND_RECORD',
    inbound.id,
    'INBOUND_RECORD',
    inbound.id,
    wr.work_date::timestamp + time '12:00',
    NULL,
    NULL,
    inbound.variety_id,
    COALESCE(variety.name, wr.details ->> 'varietyName', wr.material_name, '품종 미상'),
    inbound.age_year,
    NULL,
    inbound.pot_size,
    GREATEST(COALESCE(
        inbound.actual_quantity,
        inbound.estimated_quantity,
        NULLIF(wr.details ->> 'actualQuantity', '')::integer,
        NULLIF(wr.details ->> 'estimatedQuantity', '')::integer,
        0
    ), 0),
    jsonb_strip_nulls(jsonb_build_object(
        'tempLocation', inbound.temp_location,
        'pottingDueDate', inbound.potting_due_date,
        'bedZoneId', inbound.bed_zone_id
    )),
    wr.created_at
FROM work_records wr
JOIN work_operations operation
  ON operation.request_key = 'LEGACY_WORK_RECORD:' || wr.id
JOIN inbound_records inbound
  ON wr.details ->> 'inboundRecordId' ~ '^[0-9]+$'
 AND inbound.id = (wr.details ->> 'inboundRecordId')::bigint
LEFT JOIN varieties variety ON variety.id = inbound.variety_id
WHERE NOT EXISTS (
    SELECT 1
    FROM work_operation_targets target
    WHERE target.work_operation_id = operation.id
      AND target.target_reference_type = 'INBOUND_RECORD'
      AND target.inbound_record_id = inbound.id
);

-- Location and direct-group legacy records are expanded using the same active
-- target rule as the V2 target resolver. These are historical snapshots only;
-- no domain effect is replayed.
WITH legacy_group_targets AS (
    SELECT wr.id AS work_record_id, orchid_group.id AS orchid_group_id
    FROM work_records wr
    JOIN orchid_groups orchid_group
      ON wr.details ->> 'inboundRecordId' IS NULL
     AND orchid_group.quantity > 0
     AND orchid_group.status NOT IN ('종료', '폐기', '판매 완료')
    JOIN bed_zones zone ON zone.id = orchid_group.bed_zone_id
    JOIN physical_beds bed ON bed.id = zone.physical_bed_id
    WHERE (wr.target_type = 'FARM')
       OR (wr.target_type = 'HOUSE' AND bed.house_id = wr.target_id)
       OR (wr.target_type = 'PHYSICAL_BED' AND bed.id = wr.target_id)
       OR (wr.target_type = 'BED_ZONE' AND zone.id = wr.target_id)
       OR (wr.target_type = 'ORCHID_GROUP' AND orchid_group.id = wr.target_id)
)
INSERT INTO work_operation_targets (
    work_operation_id,
    orchid_group_id,
    target_reference_type,
    inbound_record_id,
    inclusion_source,
    source_reference_id,
    included_at,
    excluded_at,
    exclusion_reason,
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
    operation.id,
    orchid_group.id,
    'ORCHID_GROUP',
    NULL,
    CASE wr.target_type
        WHEN 'FARM' THEN 'FARM'
        WHEN 'HOUSE' THEN 'HOUSE'
        WHEN 'PHYSICAL_BED' THEN 'PHYSICAL_BED'
        WHEN 'BED_ZONE' THEN 'BED_ZONE'
        ELSE 'DIRECT'
    END,
    wr.target_id,
    wr.work_date::timestamp + time '12:00',
    NULL,
    NULL,
    orchid_group.variety_id,
    orchid_group.variety_name,
    orchid_group.age_year,
    NULL,
    orchid_group.pot_size,
    orchid_group.quantity,
    jsonb_build_object(
        'houseId', house.id,
        'houseNumber', house.number,
        'physicalBedId', bed.id,
        'physicalBedNumber', bed.number,
        'bedZoneId', zone.id,
        'bedZoneName', zone.name
    ),
    wr.created_at
FROM legacy_group_targets migration_target
JOIN work_records wr ON wr.id = migration_target.work_record_id
JOIN work_operations operation
  ON operation.request_key = 'LEGACY_WORK_RECORD:' || wr.id
JOIN orchid_groups orchid_group ON orchid_group.id = migration_target.orchid_group_id
JOIN bed_zones zone ON zone.id = orchid_group.bed_zone_id
JOIN physical_beds bed ON bed.id = zone.physical_bed_id
JOIN houses house ON house.id = bed.house_id
WHERE NOT EXISTS (
    SELECT 1
    FROM work_operation_targets target
    WHERE target.work_operation_id = operation.id
      AND target.target_reference_type = 'ORCHID_GROUP'
      AND target.orchid_group_id = orchid_group.id
);

INSERT INTO work_target_executions (
    work_operation_target_id,
    status,
    started_at,
    completed_at,
    worker,
    result_details,
    effect_applied_at,
    processed_quantity,
    version,
    created_at,
    updated_at
)
SELECT
    target.id,
    CASE WHEN wr.status = 'CANCELED' THEN 'CANCELED' ELSE 'COMPLETED' END,
    wr.work_date::timestamp + time '12:00',
    CASE
        WHEN wr.status = 'CANCELED'
            THEN COALESCE(wr.canceled_at, wr.work_date::timestamp + time '12:00')
        ELSE wr.work_date::timestamp + time '12:00'
    END,
    wr.worker,
    jsonb_strip_nulls(COALESCE(wr.details, '{}'::jsonb) || jsonb_build_object(
        'migrationSource', 'LEGACY_WORK_RECORD',
        'legacyWorkRecordId', wr.id,
        'materialName', wr.material_name,
        'dilutionRatio', wr.dilution_ratio,
        'quantity', wr.quantity,
        'fromBedZoneId', wr.from_bed_zone_id,
        'toBedZoneId', wr.to_bed_zone_id,
        'legacyStatus', wr.status,
        'cancelReason', wr.cancel_reason
    )),
    CASE
        WHEN wr.status = 'CANCELED' THEN NULL
        ELSE wr.work_date::timestamp + time '12:00'
    END,
    CASE WHEN wr.status = 'CANCELED' THEN 0 ELSE target.quantity_snapshot END,
    0,
    wr.created_at,
    wr.updated_at
FROM work_operations operation
JOIN work_records wr
  ON operation.request_key = 'LEGACY_WORK_RECORD:' || wr.id
JOIN work_operation_targets target ON target.work_operation_id = operation.id
WHERE NOT EXISTS (
    SELECT 1
    FROM work_target_executions execution
    WHERE execution.work_operation_target_id = target.id
);

-- Close legacy active potting plans whose inbound records already produced
-- orchid groups before the V2 lifecycle became authoritative.
UPDATE work_target_executions execution
SET status = 'SKIPPED',
    started_at = COALESCE(execution.started_at, CURRENT_TIMESTAMP),
    completed_at = COALESCE(execution.completed_at, CURRENT_TIMESTAMP),
    result_details = COALESCE(execution.result_details, '{}'::jsonb)
        || jsonb_build_object('skipReason', '입고 관리에서 이미 완료된 포트 작업')
FROM work_operation_targets target
JOIN work_operations operation ON operation.id = target.work_operation_id
JOIN work_types work_type ON work_type.id = operation.work_type_id
JOIN inbound_records inbound_record ON inbound_record.id = target.inbound_record_id
WHERE execution.work_operation_target_id = target.id
  AND work_type.code = 'POTTING'
  AND operation.status IN ('PLANNED', 'IN_PROGRESS', 'PAUSED')
  AND execution.status IN ('PENDING', 'IN_PROGRESS', 'PARTIALLY_COMPLETED')
  AND inbound_record.created_orchid_group_id IS NOT NULL;

UPDATE work_operations operation
SET status = 'COMPLETED',
    actual_start_at = COALESCE(operation.actual_start_at, CURRENT_TIMESTAMP),
    actual_end_at = COALESCE(operation.actual_end_at, CURRENT_TIMESTAMP),
    updated_at = CURRENT_TIMESTAMP
FROM work_types work_type
WHERE operation.work_type_id = work_type.id
  AND work_type.code = 'POTTING'
  AND operation.status IN ('PLANNED', 'IN_PROGRESS', 'PAUSED')
  AND EXISTS (
      SELECT 1
      FROM work_operation_targets target
      JOIN work_target_executions execution
        ON execution.work_operation_target_id = target.id
      WHERE target.work_operation_id = operation.id
  )
  AND NOT EXISTS (
      SELECT 1
      FROM work_operation_targets target
      JOIN work_target_executions execution
        ON execution.work_operation_target_id = target.id
      WHERE target.work_operation_id = operation.id
        AND execution.status NOT IN ('COMPLETED', 'SKIPPED')
  );
