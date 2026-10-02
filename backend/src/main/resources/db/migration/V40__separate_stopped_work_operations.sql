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
