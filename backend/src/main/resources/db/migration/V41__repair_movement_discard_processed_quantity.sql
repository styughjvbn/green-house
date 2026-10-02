-- V35 normalized legacy movement-discard targets to the residual quantity,
-- but their completed execution usage still retained the pre-movement source
-- quantity. Align completed usage with the normalized target snapshot.
UPDATE work_target_executions execution
SET processed_quantity = target.quantity_snapshot,
    version = execution.version + 1,
    updated_at = CURRENT_TIMESTAMP
FROM work_operation_targets target
JOIN work_operations operation
  ON operation.id = target.work_operation_id
WHERE execution.work_operation_target_id = target.id
  AND operation.relation_type = 'MOVEMENT_DISCARD'
  AND operation.details ->> 'allocationMethod' = 'LEGACY_RECORDED_SOURCE_ALLOCATION'
  AND execution.status = 'COMPLETED'
  AND execution.processed_quantity IS DISTINCT FROM target.quantity_snapshot;
