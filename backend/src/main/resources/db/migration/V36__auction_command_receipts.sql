-- Legacy attempts/returns do not have request identities; preserve them without inventing receipts.
CREATE SEQUENCE auction_command_receipts_id_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE auction_command_receipts (
    id BIGINT PRIMARY KEY DEFAULT nextval('auction_command_receipts_id_seq'),
    lot_id BIGINT NOT NULL REFERENCES auction_shipment_lots(id) ON DELETE RESTRICT,
    command_type VARCHAR(16) NOT NULL,
    request_key VARCHAR(100) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    response_snapshot JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_auction_command_receipt UNIQUE (lot_id, command_type, request_key),
    CONSTRAINT ck_auction_command_type CHECK (command_type IN ('RESULT', 'RETURN')),
    CONSTRAINT ck_auction_command_key CHECK (request_key ~ '[^[:space:]]'),
    CONSTRAINT ck_auction_command_fingerprint CHECK (request_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_auction_command_response CHECK (
        jsonb_typeof(response_snapshot) = 'object'
        AND response_snapshot ? 'id'
        AND response_snapshot->'id' = to_jsonb(lot_id))
);

ALTER SEQUENCE auction_command_receipts_id_seq OWNED BY auction_command_receipts.id;
