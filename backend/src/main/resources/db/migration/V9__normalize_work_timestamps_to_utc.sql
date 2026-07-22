-- Work timestamps are stored as UTC wall time in timestamp-without-time-zone columns.
-- V8 generated legacy history at Korean local noon, so only those synthetic values
-- and application-managed KST timestamps are shifted. Audit timestamps were already UTC.

UPDATE work_operations
SET actual_start_at = actual_start_at - interval '9 hours',
    actual_end_at = CASE
        WHEN actual_end_at = planned_start_date::timestamp + time '12:00'
            THEN actual_end_at - interval '9 hours'
        ELSE actual_end_at
    END,
    target_snapshot_at = target_snapshot_at - interval '9 hours'
WHERE source_condition_snapshot ->> 'migrationSource' = 'LEGACY_WORK_RECORD';

UPDATE work_operation_targets target
SET included_at = target.included_at - interval '9 hours'
FROM work_operations operation
WHERE target.work_operation_id = operation.id
  AND operation.source_condition_snapshot ->> 'migrationSource' = 'LEGACY_WORK_RECORD';

UPDATE work_target_executions execution
SET started_at = CASE
        WHEN execution.started_at = operation.planned_start_date::timestamp + time '12:00'
            THEN execution.started_at - interval '9 hours'
        ELSE execution.started_at
    END,
    completed_at = CASE
        WHEN execution.completed_at = operation.planned_start_date::timestamp + time '12:00'
            THEN execution.completed_at - interval '9 hours'
        ELSE execution.completed_at
    END,
    effect_applied_at = CASE
        WHEN execution.effect_applied_at = operation.planned_start_date::timestamp + time '12:00'
            THEN execution.effect_applied_at - interval '9 hours'
        ELSE execution.effect_applied_at
    END
FROM work_operation_targets target
JOIN work_operations operation ON operation.id = target.work_operation_id
WHERE execution.work_operation_target_id = target.id
  AND operation.source_condition_snapshot ->> 'migrationSource' = 'LEGACY_WORK_RECORD';

-- Native operations completed through the old farm clock persisted Korean wall time.
UPDATE work_operations operation
SET actual_start_at = CASE
        WHEN operation.actual_start_at = operation.actual_end_at
            THEN operation.actual_start_at - interval '9 hours'
        ELSE operation.actual_start_at
    END,
    actual_end_at = operation.actual_end_at - interval '9 hours'
WHERE COALESCE(operation.source_condition_snapshot ->> 'migrationSource', '') <> 'LEGACY_WORK_RECORD'
  AND operation.actual_end_at IS NOT NULL
  AND (
      operation.status IN ('COMPLETED', 'CORRECTED')
      OR EXISTS (
          SELECT 1
          FROM work_applied_effects effect
          WHERE effect.work_operation_id = operation.id
      )
  );

UPDATE work_target_executions execution
SET started_at = CASE
        WHEN execution.started_at = execution.effect_applied_at
          OR execution.started_at = execution.completed_at
            THEN execution.started_at - interval '9 hours'
        ELSE execution.started_at
    END,
    completed_at = CASE
        WHEN execution.status = 'COMPLETED'
            THEN execution.completed_at - interval '9 hours'
        ELSE execution.completed_at
    END,
    effect_applied_at = execution.effect_applied_at - interval '9 hours'
FROM work_operation_targets target
JOIN work_operations operation ON operation.id = target.work_operation_id
WHERE execution.work_operation_target_id = target.id
  AND COALESCE(operation.source_condition_snapshot ->> 'migrationSource', '') <> 'LEGACY_WORK_RECORD'
  AND execution.effect_applied_at IS NOT NULL;

UPDATE work_applied_effects effect
SET applied_at = effect.applied_at - interval '9 hours'
FROM work_operations operation
WHERE effect.work_operation_id = operation.id
  AND COALESCE(operation.source_condition_snapshot ->> 'migrationSource', '') <> 'LEGACY_WORK_RECORD';

-- Snapshot/start values changed from UTC to KST only while the farm-clock version was deployed.
-- Their audit timestamp provides a safe discriminator for that short-lived format.
UPDATE work_operations
SET target_snapshot_at = target_snapshot_at - interval '9 hours'
WHERE COALESCE(source_condition_snapshot ->> 'migrationSource', '') <> 'LEGACY_WORK_RECORD'
  AND target_snapshot_at - created_at BETWEEN interval '8 hours 55 minutes' AND interval '9 hours 5 minutes';

UPDATE work_operation_targets target
SET included_at = target.included_at - interval '9 hours'
FROM work_operations operation
WHERE target.work_operation_id = operation.id
  AND COALESCE(operation.source_condition_snapshot ->> 'migrationSource', '') <> 'LEGACY_WORK_RECORD'
  AND target.included_at - target.created_at BETWEEN interval '8 hours 55 minutes' AND interval '9 hours 5 minutes';
