-- Preserve legacy inbound facts; their original request identities are unknown.
CREATE TABLE inbound_creation_receipts (
    request_key VARCHAR(100) PRIMARY KEY,
    request_fingerprint VARCHAR(64) NOT NULL,
    inbound_record_id BIGINT REFERENCES inbound_records(id) ON DELETE RESTRICT,
    response_snapshot JSONB,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT ck_inbound_creation_key CHECK (request_key ~ '[^[:space:]]'),
    CONSTRAINT ck_inbound_creation_fingerprint CHECK (request_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_inbound_creation_response CHECK (
        (inbound_record_id IS NULL AND response_snapshot IS NULL)
        OR (inbound_record_id IS NOT NULL AND response_snapshot IS NOT NULL
            AND jsonb_typeof(response_snapshot) = 'object'
            AND response_snapshot ? 'id'
            AND response_snapshot->'id' = to_jsonb(inbound_record_id)))
);
