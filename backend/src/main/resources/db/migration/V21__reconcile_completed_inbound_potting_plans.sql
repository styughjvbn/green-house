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
