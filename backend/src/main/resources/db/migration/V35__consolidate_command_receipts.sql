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
    CONSTRAINT ck_auction_command_type CHECK (command_type IN ('RESULT', 'RETURN', 'FOLLOW_UP', 'ARRIVAL', 'ARRIVAL_CANCEL')),
    CONSTRAINT ck_auction_command_key CHECK (request_key ~ '[^[:space:]]'),
    CONSTRAINT ck_auction_command_fingerprint CHECK (request_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_auction_command_response CHECK (
  jsonb_typeof(response_snapshot) = 'object' AND (
    (command_type IN ('RESULT', 'RETURN') AND response_snapshot ? 'id'
      AND response_snapshot->'id' = to_jsonb(lot_id))
    OR
    (command_type IN ('FOLLOW_UP', 'ARRIVAL', 'ARRIVAL_CANCEL')
      AND response_snapshot ? 'followUp'
      AND jsonb_typeof(response_snapshot->'followUp') = 'object'
      AND response_snapshot->'followUp' ? 'lotId'
      AND response_snapshot->'followUp'->'lotId' = to_jsonb(lot_id)
      AND (command_type = 'FOLLOW_UP' OR (
        response_snapshot ? 'arrival'
        AND jsonb_typeof(response_snapshot->'arrival') = 'object'
        AND response_snapshot->'arrival' ? 'lotId'
        AND response_snapshot->'arrival'->'lotId' = to_jsonb(lot_id))))
  )
)
);

ALTER SEQUENCE auction_command_receipts_id_seq OWNED BY auction_command_receipts.id;

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

-- Existing receipt/match events remain the original cash and allocation evidence.
CREATE SEQUENCE payment_allocation_command_receipts_id_seq START WITH 1 INCREMENT BY 50;
CREATE TABLE payment_allocation_command_receipts (
    id BIGINT PRIMARY KEY DEFAULT nextval('payment_allocation_command_receipts_id_seq'),
    partner_id BIGINT NOT NULL REFERENCES business_partners(id),
    request_key VARCHAR(100) NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    result_json JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_payment_allocation_command_request UNIQUE (partner_id, request_key)
);
-- FK traversal is used for receipt reconciliation and allocation corrections.
CREATE INDEX idx_payment_events_parent ON partner_payment_events(parent_event_id);
ALTER TABLE partner_payment_events ADD CONSTRAINT ck_new_payment_allocation_fact CHECK (
    external_uid IS NULL OR
    (external_uid NOT LIKE 'ALLOCATION:%' AND external_uid NOT LIKE 'ALLOCATION_CANCEL:%') OR
    (amount > 0 AND parent_event_id IS NOT NULL AND target_id IS NOT NULL AND
     target_type IN ('SALES_SLIP', 'AUCTION_PROCEEDS') AND
     event_type IN ('PAYMENT_ALLOCATED', 'PAYMENT_UNLINKED'))
);

-- Creation grouping is separate from request replay. Preserve receipt keys and operation IDs.
ALTER TABLE work_command_receipt_memberships RENAME TO work_creation_batch_memberships;
ALTER TABLE work_creation_batch_memberships
    RENAME CONSTRAINT work_command_receipt_memberships_pkey TO work_creation_batch_memberships_pkey;
ALTER TABLE work_creation_batch_memberships
    RENAME CONSTRAINT fk_work_command_receipt_membership_receipt TO fk_work_creation_batch_receipt;
ALTER TABLE work_creation_batch_memberships
    RENAME CONSTRAINT fk_work_command_receipt_membership_operation TO fk_work_creation_batch_operation;
ALTER INDEX idx_work_command_receipt_memberships_operation RENAME TO idx_work_creation_batch_operation;
