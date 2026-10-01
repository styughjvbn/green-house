CREATE TEMP TABLE v38_multi_variety_discard_operations ON COMMIT DROP AS
SELECT operation.id AS operation_id
FROM work_operations operation
JOIN work_types work_type
  ON work_type.id = operation.work_type_id
 AND work_type.code = 'DISCARD'
JOIN work_operation_targets target
  ON target.work_operation_id = operation.id
GROUP BY operation.id
HAVING count(DISTINCT COALESCE(
    target.variety_id_snapshot::TEXT,
    'name:' || target.variety_name_snapshot
)) > 1;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM work_operation_targets target
        JOIN v38_multi_variety_discard_operations candidate
          ON candidate.operation_id = target.work_operation_id
        WHERE target.variety_id_snapshot IS NULL
    ) THEN
        RAISE EXCEPTION 'V38 cannot split a multi-variety discard with an unidentified variety';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM work_operation_targets target
        JOIN v38_multi_variety_discard_operations candidate
          ON candidate.operation_id = target.work_operation_id
        GROUP BY target.work_operation_id, target.variety_id_snapshot
        HAVING count(DISTINCT target.variety_name_snapshot) <> 1
    ) THEN
        RAISE EXCEPTION 'V38 found inconsistent variety names in a discard operation';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM work_operations operation
        JOIN v38_multi_variety_discard_operations candidate
          ON candidate.operation_id = operation.id
        WHERE operation.request_key IS NOT NULL
           OR operation.parent_operation_id IS NOT NULL
           OR operation.relation_type IS NOT NULL
           OR operation.void_request_key IS NOT NULL
           OR operation.void_mutation_id IS NOT NULL
           OR operation.voided_at IS NOT NULL
           OR operation.details ? 'sourceInputQuantities'
    ) THEN
        RAISE EXCEPTION 'V38 found a multi-variety discard with unsupported operation metadata';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM work_operations child
        JOIN v38_multi_variety_discard_operations candidate
          ON candidate.operation_id = child.parent_operation_id
    ) OR EXISTS (
        SELECT 1
        FROM work_operation_corrections correction
        JOIN v38_multi_variety_discard_operations candidate
          ON candidate.operation_id IN (
              correction.original_work_operation_id,
              correction.correction_work_operation_id
          )
    ) OR EXISTS (
        SELECT 1
        FROM work_command_receipt_memberships membership
        JOIN v38_multi_variety_discard_operations candidate
          ON candidate.operation_id = membership.operation_id
    ) OR EXISTS (
        SELECT 1
        FROM orchid_group_lineage lineage
        JOIN v38_multi_variety_discard_operations candidate
          ON candidate.operation_id = lineage.work_operation_id
    ) OR EXISTS (
        SELECT 1
        FROM orchid_group_identity_migrations identity_migration
        JOIN v38_multi_variety_discard_operations candidate
          ON candidate.operation_id = identity_migration.work_operation_id
    ) THEN
        RAISE EXCEPTION 'V38 found a multi-variety discard with unsupported work links';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM work_applied_effects effect
        JOIN v38_multi_variety_discard_operations candidate
          ON candidate.operation_id = effect.work_operation_id
        LEFT JOIN work_operation_targets target
          ON target.id = effect.work_operation_target_id
         AND target.work_operation_id = candidate.operation_id
        WHERE target.id IS NULL
    ) THEN
        RAISE EXCEPTION 'V38 found a discard effect without an operation target';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM work_applied_effects effect
        JOIN work_operation_targets target
          ON target.id = effect.work_operation_target_id
        JOIN v38_multi_variety_discard_operations candidate
          ON candidate.operation_id = effect.work_operation_id
        WHERE effect.mutation_id IS NOT NULL
        GROUP BY effect.mutation_id
        HAVING count(DISTINCT target.variety_id_snapshot) > 1
    ) THEN
        RAISE EXCEPTION 'V38 found a Mutation shared by multiple discard varieties';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM work_operations operation
        JOIN v38_multi_variety_discard_operations candidate
          ON candidate.operation_id = operation.id
        JOIN work_operation_targets target
          ON target.work_operation_id = operation.id
        GROUP BY operation.id, operation.title, target.variety_id_snapshot
        HAVING char_length(operation.title) + 3 + char_length(min(target.variety_name_snapshot)) > 150
    ) THEN
        RAISE EXCEPTION 'V38 cannot append a variety name to a discard title longer than 150 characters';
    END IF;
END $$;

CREATE TEMP TABLE v38_discard_variety_operations ON COMMIT DROP AS
SELECT
    grouped.operation_id AS original_operation_id,
    grouped.variety_id,
    grouped.variety_name,
    row_number() OVER (
        PARTITION BY grouped.operation_id
        ORDER BY grouped.first_target_id
    ) AS variety_ordinal,
    NULL::BIGINT AS split_operation_id,
    NULL::UUID AS split_correlation_id
FROM (
    SELECT
        target.work_operation_id AS operation_id,
        target.variety_id_snapshot AS variety_id,
        min(target.variety_name_snapshot) AS variety_name,
        min(target.id) AS first_target_id
    FROM work_operation_targets target
    JOIN v38_multi_variety_discard_operations candidate
      ON candidate.operation_id = target.work_operation_id
    GROUP BY target.work_operation_id, target.variety_id_snapshot
) grouped;

UPDATE v38_discard_variety_operations mapping
SET split_operation_id = CASE
        WHEN mapping.variety_ordinal = 1 THEN mapping.original_operation_id
        ELSE nextval('work_operations_id_seq')
    END;

WITH operation_hashes AS (
    SELECT
        mapping.original_operation_id,
        mapping.variety_id,
        md5('WORK_OPERATION:' || mapping.split_operation_id) AS hash
    FROM v38_discard_variety_operations mapping
)
UPDATE v38_discard_variety_operations mapping
SET split_correlation_id = (
        substring(hash.hash, 1, 8) || '-' ||
        substring(hash.hash, 9, 4) || '-3' ||
        substring(hash.hash, 14, 3) || '-' ||
        to_hex(8 + ((get_byte(decode(hash.hash, 'hex'), 8) >> 4) & 3)) ||
        substring(hash.hash, 18, 3) || '-' ||
        substring(hash.hash, 21, 12)
    )::UUID
FROM operation_hashes hash
WHERE hash.original_operation_id = mapping.original_operation_id
  AND hash.variety_id = mapping.variety_id;

INSERT INTO work_operations (
    id, work_type_id, title, status, planned_start_date, planned_end_date,
    actual_start_at, actual_end_at, source_scope_type, source_scope_id,
    source_condition_snapshot, target_snapshot_at, details, worker, memo,
    request_key, version, created_at, updated_at, voided_at, void_reason,
    void_request_key, void_mutation_id, parent_operation_id, relation_type
)
SELECT
    mapping.split_operation_id,
    original.work_type_id,
    original.title || ' - ' || mapping.variety_name,
    original.status,
    original.planned_start_date,
    original.planned_end_date,
    original.actual_start_at,
    original.actual_end_at,
    original.source_scope_type,
    original.source_scope_id,
    CASE
        WHEN original.source_condition_snapshot ? 'orchidGroupIds' THEN
            jsonb_set(
                original.source_condition_snapshot,
                '{orchidGroupIds}',
                (
                    SELECT jsonb_agg(target.orchid_group_id ORDER BY target.id)
                    FROM work_operation_targets target
                    WHERE target.work_operation_id = mapping.original_operation_id
                      AND target.variety_id_snapshot = mapping.variety_id
                ),
                FALSE
            )
        ELSE original.source_condition_snapshot
    END,
    original.target_snapshot_at,
    original.details,
    original.worker,
    original.memo,
    NULL,
    original.version,
    original.created_at,
    original.updated_at,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
FROM v38_discard_variety_operations mapping
JOIN work_operations original
  ON original.id = mapping.original_operation_id
WHERE mapping.variety_ordinal > 1;

UPDATE work_operations original
SET title = original.title || ' - ' || mapping.variety_name,
    source_condition_snapshot = CASE
        WHEN original.source_condition_snapshot ? 'orchidGroupIds' THEN
            jsonb_set(
                original.source_condition_snapshot,
                '{orchidGroupIds}',
                (
                    SELECT jsonb_agg(target.orchid_group_id ORDER BY target.id)
                    FROM work_operation_targets target
                    WHERE target.work_operation_id = mapping.original_operation_id
                      AND target.variety_id_snapshot = mapping.variety_id
                ),
                FALSE
            )
        ELSE original.source_condition_snapshot
    END
FROM v38_discard_variety_operations mapping
WHERE mapping.variety_ordinal = 1
  AND original.id = mapping.original_operation_id;

UPDATE work_operation_targets target
SET work_operation_id = mapping.split_operation_id
FROM v38_discard_variety_operations mapping
WHERE mapping.variety_ordinal > 1
  AND target.work_operation_id = mapping.original_operation_id
  AND target.variety_id_snapshot = mapping.variety_id;

UPDATE work_applied_effects effect
SET work_operation_id = mapping.split_operation_id
FROM work_operation_targets target,
     v38_discard_variety_operations mapping
WHERE effect.work_operation_target_id = target.id
  AND effect.work_operation_id = mapping.original_operation_id
  AND target.work_operation_id = mapping.split_operation_id
  AND target.variety_id_snapshot = mapping.variety_id
  AND effect.work_operation_id <> mapping.split_operation_id;

UPDATE work_applied_effects effect
SET correlation_id = CASE
        WHEN effect.mutation_id IS NULL AND effect.correlation_id IS NULL THEN NULL
        ELSE mapping.split_correlation_id
    END
FROM v38_discard_variety_operations mapping
WHERE effect.work_operation_id = mapping.split_operation_id;

UPDATE orchid_group_mutations mutation
SET source_reference_id = effect.work_operation_id::TEXT,
    correlation_id = mapping.split_correlation_id
FROM work_applied_effects effect
JOIN v38_discard_variety_operations mapping
  ON mapping.split_operation_id = effect.work_operation_id
WHERE mutation.id = effect.mutation_id
  AND mutation.source_domain = 'WORK'
  AND mutation.source_type = 'WORK_EFFECT';

UPDATE work_operations operation
SET details = jsonb_set(
        operation.details,
        '{totalDiscardQuantity}',
        to_jsonb((
            SELECT sum((effect.result_details ->> 'discardedQuantity')::INTEGER)
            FROM work_applied_effects effect
            WHERE effect.work_operation_id = operation.id
        )),
        FALSE
    )
WHERE operation.id IN (
    SELECT split_operation_id FROM v38_discard_variety_operations
)
  AND operation.details ? 'totalDiscardQuantity';

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM v38_discard_variety_operations mapping
        JOIN work_operation_targets target
          ON target.work_operation_id = mapping.split_operation_id
        WHERE target.variety_id_snapshot <> mapping.variety_id
    ) OR EXISTS (
        SELECT 1
        FROM v38_discard_variety_operations mapping
        LEFT JOIN work_operation_targets target
          ON target.work_operation_id = mapping.split_operation_id
        GROUP BY mapping.original_operation_id, mapping.variety_id
        HAVING count(target.id) = 0
    ) OR EXISTS (
        SELECT 1
        FROM work_applied_effects effect
        JOIN work_operation_targets target
          ON target.id = effect.work_operation_target_id
        JOIN v38_discard_variety_operations mapping
          ON mapping.split_operation_id = target.work_operation_id
        WHERE effect.work_operation_id <> target.work_operation_id
    ) OR EXISTS (
        SELECT 1
        FROM orchid_group_mutations mutation
        JOIN work_applied_effects effect
          ON effect.mutation_id = mutation.id
        JOIN v38_discard_variety_operations mapping
          ON mapping.split_operation_id = effect.work_operation_id
        WHERE mutation.source_reference_id IS DISTINCT FROM effect.work_operation_id::TEXT
           OR mutation.correlation_id IS DISTINCT FROM mapping.split_correlation_id
           OR effect.correlation_id IS DISTINCT FROM mapping.split_correlation_id
    ) THEN
        RAISE EXCEPTION 'V38 failed to normalize multi-variety discard operations';
    END IF;
END $$;
