UPDATE work_types
SET is_system = TRUE,
    updated_at = CURRENT_TIMESTAMP
WHERE code = 'REPOT';
