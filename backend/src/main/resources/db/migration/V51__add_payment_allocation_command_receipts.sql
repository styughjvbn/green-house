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
