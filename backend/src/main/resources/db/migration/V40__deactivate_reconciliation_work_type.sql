-- TODO: 보류 - 현장 상태 동기화 정책을 재검토한 뒤 작업 유형을 다시 활성화한다.
UPDATE work_types
SET is_active = FALSE,
    updated_at = CURRENT_TIMESTAMP
WHERE code = 'RECONCILIATION';
