UPDATE inbound_records
SET status = 'POTTING_PENDING',
    updated_at = CURRENT_TIMESTAMP
WHERE status = 'TEMP_STORED';
