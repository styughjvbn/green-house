-- Consolidated from V39__normalize_work_operation_titles.sql: preserve this stage's SQL order.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM work_operations operation
        JOIN work_types work_type
          ON work_type.id = operation.work_type_id
         AND work_type.code IN (
             'INBOUND', 'POTTING', 'MOVEMENT', 'REPOT', 'DIVIDE', 'MERGE',
             'DISCARD', 'RECONCILIATION'
         )
        LEFT JOIN work_operation_targets target
          ON target.work_operation_id = operation.id
        GROUP BY operation.id
        HAVING count(target.id) = 0
            OR count(DISTINCT COALESCE(
                target.variety_id_snapshot::TEXT,
                'name:' || NULLIF(btrim(target.variety_name_snapshot), '')
            )) <> 1
            OR count(DISTINCT NULLIF(btrim(target.variety_name_snapshot), '')) <> 1
            OR count(*) FILTER (
                WHERE target.variety_name_snapshot IS NULL
                   OR btrim(target.variety_name_snapshot) = ''
            ) > 0
    ) THEN
        RAISE EXCEPTION 'V39 cannot normalize a managed work operation without exactly one consistent variety';
    END IF;
END $$;

CREATE TEMP TABLE v39_managed_operation_titles ON COMMIT DROP AS
SELECT
    operation.id AS operation_id,
    min(btrim(target.variety_name_snapshot)) AS variety_name,
    CASE work_type.code
        WHEN 'INBOUND' THEN '입고'
        WHEN 'POTTING' THEN '포트 식재'
        WHEN 'MOVEMENT' THEN '자리 이동'
        WHEN 'REPOT' THEN '분갈이'
        WHEN 'DIVIDE' THEN '분주'
        WHEN 'MERGE' THEN '합식'
        WHEN 'DISCARD' THEN '폐기'
        WHEN 'RECONCILIATION' THEN '현장 상태 조정'
    END AS event_title
FROM work_operations operation
JOIN work_types work_type
  ON work_type.id = operation.work_type_id
 AND work_type.code IN (
     'INBOUND', 'POTTING', 'MOVEMENT', 'REPOT', 'DIVIDE', 'MERGE',
     'DISCARD', 'RECONCILIATION'
 )
JOIN work_operation_targets target
  ON target.work_operation_id = operation.id
GROUP BY operation.id, work_type.code;

UPDATE work_operations operation
SET title = left(
        mapping.variety_name,
        150 - char_length(' · ') - char_length(mapping.event_title)
    ) || ' · ' || mapping.event_title
FROM v39_managed_operation_titles mapping
WHERE operation.id = mapping.operation_id;

UPDATE work_operations operation
SET title = left(
        mapping.variety_name,
        150 - char_length(' · 자리 이동 후 폐기')
    ) || ' · 자리 이동 후 폐기'
FROM v39_managed_operation_titles mapping
WHERE operation.id = mapping.operation_id
  AND operation.relation_type = 'MOVEMENT_DISCARD';

UPDATE work_operations correction_operation
SET title = left(original_operation.title, 150 - char_length(' 보정')) || ' 보정'
FROM work_operation_corrections correction
JOIN work_operations original_operation
  ON original_operation.id = correction.original_work_operation_id
WHERE correction_operation.id = correction.correction_work_operation_id;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM work_operations operation
        JOIN v39_managed_operation_titles expected
          ON expected.operation_id = operation.id
        WHERE operation.title IS NULL
           OR btrim(operation.title) = ''
           OR char_length(operation.title) > 150
    ) OR EXISTS (
        SELECT 1
        FROM work_operation_corrections correction
        JOIN work_operations operation
          ON operation.id = correction.correction_work_operation_id
        WHERE operation.title IS NULL
           OR btrim(operation.title) = ''
           OR char_length(operation.title) > 150
    ) THEN
        RAISE EXCEPTION 'V39 produced an invalid work operation title';
    END IF;
END $$;

-- Consolidated from V40__separate_stopped_work_operations.sql: preserve this stage's SQL order.
ALTER TABLE work_operations DROP CONSTRAINT ck_work_operations_status;

ALTER TABLE work_operations
    ADD CONSTRAINT ck_work_operations_status CHECK (
        status IN ('PLANNED', 'IN_PROGRESS', 'PAUSED', 'COMPLETED', 'STOPPED', 'CANCELED', 'CORRECTED', 'VOIDED')
    );

UPDATE work_operations operation
SET status = 'STOPPED'
WHERE operation.status = 'CANCELED'
  AND (
      EXISTS (
          SELECT 1
          FROM work_target_executions execution
          JOIN work_operation_targets target ON target.id = execution.work_operation_target_id
          WHERE target.work_operation_id = operation.id
            AND execution.status IN ('COMPLETED', 'SKIPPED')
      )
      OR EXISTS (
          SELECT 1
          FROM work_applied_effects effect
          WHERE effect.work_operation_id = operation.id
            AND effect.canceled_at IS NULL
      )
  );
