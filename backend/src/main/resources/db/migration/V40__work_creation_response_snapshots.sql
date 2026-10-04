ALTER TABLE work_command_receipts ADD COLUMN response_snapshot JSONB;

-- Older receipts retain their ID-only replay contract. Never invent past responses.
ALTER TABLE work_command_receipts ADD CONSTRAINT ck_work_creation_response
CHECK (
    response_snapshot IS NULL OR CASE WHEN jsonb_typeof(response_snapshot) = 'array' THEN (
        request_fingerprint IS NOT NULL
        AND result_operation_ids IS NOT NULL
        AND jsonb_array_length(response_snapshot) > 0
        AND jsonb_path_query_array(response_snapshot, '$[*].id') = result_operation_ids
        AND NOT jsonb_path_exists(response_snapshot,
            '$[*] ? (@.type() != "object" || !exists(@.id) || @.id.type() != "number" || @.id <= 0)')
    ) ELSE FALSE END
);

ALTER TABLE work_command_receipts ADD CONSTRAINT ck_general_work_creation_complete
CHECK (
    split_part(receipt_key, ':', 1) NOT IN ('GENERAL_PLAN', 'GENERAL_PLAN_BATCH', 'GENERAL_RECORD')
    OR ((result_operation_ids IS NULL AND response_snapshot IS NULL)
        OR (result_operation_ids IS NOT NULL AND response_snapshot IS NOT NULL))
);
