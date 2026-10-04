-- Existing sales have no creation request identities; do not invent or deduplicate them.
CREATE TABLE sales_creation_receipts (
    request_key VARCHAR(100) PRIMARY KEY,
    request_fingerprint VARCHAR(64) NOT NULL,
    sales_slip_id BIGINT REFERENCES sales_slips(id) ON DELETE RESTRICT,
    response_snapshot JSONB,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT ck_sales_creation_key CHECK (request_key ~ '[^[:space:]]'),
    CONSTRAINT ck_sales_creation_fingerprint CHECK (request_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_sales_creation_response CHECK (
        (sales_slip_id IS NULL AND response_snapshot IS NULL)
        OR (sales_slip_id IS NOT NULL AND response_snapshot IS NOT NULL
            AND jsonb_typeof(response_snapshot) = 'object'
            AND response_snapshot ? 'id'
            AND response_snapshot->'id' = to_jsonb(sales_slip_id)))
);
