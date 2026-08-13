UPDATE work_types
SET name = '자리 이동',
    updated_at = CURRENT_TIMESTAMP
WHERE code = 'MOVEMENT'
  AND name = '위치 이동';
