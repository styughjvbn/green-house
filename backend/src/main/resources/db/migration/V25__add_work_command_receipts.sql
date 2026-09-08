CREATE TABLE work_command_receipts (
    receipt_key VARCHAR(255) PRIMARY KEY,
    request_fingerprint VARCHAR(64),
    result_operation_ids JSONB,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT ck_work_receipt_fingerprint
        CHECK (request_fingerprint IS NULL OR request_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_work_receipt_results
        CHECK (result_operation_ids IS NULL OR jsonb_typeof(result_operation_ids) = 'array')
);

-- Preserve known request identities and result IDs. Old immediate commands did
-- not persist their complete input; do not invent a fingerprint from current data.
INSERT INTO work_command_receipts (receipt_key, result_operation_ids, created_at)
SELECT 'IMMEDIATE:' || request_key, jsonb_build_array(id), created_at
FROM work_operations WHERE request_key IS NOT NULL;
