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
