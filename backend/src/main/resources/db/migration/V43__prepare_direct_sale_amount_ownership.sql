-- Expand before switching writers/readers. Preserve stored monetary facts verbatim;
-- never reconstruct historical sales from current prices or manufacture payments.
CREATE TABLE direct_sales (
    sales_slip_id BIGINT PRIMARY KEY REFERENCES sales_slips(id) ON DELETE RESTRICT,
    version BIGINT NOT NULL DEFAULT 0,
    partner_id BIGINT NOT NULL REFERENCES business_partners(id) ON DELETE RESTRICT,
    sale_date DATE NOT NULL,
    total_amount INTEGER NOT NULL,
    expected_payment_date DATE,
    payment_method VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_direct_sales_partner_date ON direct_sales(partner_id, sale_date DESC, sales_slip_id DESC);

-- The price basis is a financial fact, not a second physical item/allocation.
ALTER TABLE sales_slip_items ADD CONSTRAINT uq_sales_item_document UNIQUE (sales_slip_id, id);
CREATE TABLE direct_sale_prices (
    sales_slip_item_id BIGINT PRIMARY KEY,
    sales_slip_id BIGINT NOT NULL REFERENCES direct_sales(sales_slip_id) ON DELETE RESTRICT,
    priced_quantity INTEGER NOT NULL,
    unit_price INTEGER NOT NULL,
    amount INTEGER NOT NULL,
    CONSTRAINT fk_direct_price_document_item FOREIGN KEY (sales_slip_id, sales_slip_item_id)
        REFERENCES sales_slip_items(sales_slip_id, id) ON DELETE RESTRICT
);
CREATE INDEX idx_direct_sale_prices_document ON direct_sale_prices(sales_slip_id, sales_slip_item_id);

INSERT INTO direct_sales (sales_slip_id, partner_id, sale_date, total_amount,
    expected_payment_date, payment_method, created_at, updated_at)
SELECT id, partner_id, sale_date, total_amount, expected_payment_date,
    payment_method, created_at, updated_at
FROM sales_slips WHERE sales_type = 'DIRECT' OR sales_type IS NULL;

INSERT INTO direct_sale_prices (sales_slip_item_id, sales_slip_id, priced_quantity, unit_price, amount)
SELECT item.id, item.sales_slip_id, item.quantity, item.unit_price, item.amount
FROM sales_slip_items item JOIN direct_sales sale ON sale.sales_slip_id = item.sales_slip_id;

-- Immutable migration evidence. Legacy summaries are retained for review, not
-- promoted to payment/allocation facts or used as an independent amount source.
CREATE TABLE direct_sale_amount_reconciliations (
    sales_slip_id BIGINT PRIMARY KEY REFERENCES direct_sales(sales_slip_id) ON DELETE RESTRICT,
    stored_paid_amount BIGINT,
    stored_remaining_amount BIGINT,
    stored_payment_status VARCHAR(255) NOT NULL,
    stored_item_amount_sum BIGINT NOT NULL,
    confirmed_allocation_amount BIGINT NOT NULL,
    total_mismatch BOOLEAN NOT NULL,
    price_mismatch BOOLEAN NOT NULL,
    paid_mismatch BOOLEAN NOT NULL,
    remaining_mismatch BOOLEAN NOT NULL,
    ledger_review_required BOOLEAN NOT NULL,
    signed_amount_review_required BOOLEAN NOT NULL
);

WITH item_facts AS (
    SELECT sales_slip_id, SUM(amount::bigint) AS amount,
        BOOL_OR(priced_quantity = 0 OR unit_price < 0
            OR amount::bigint <> priced_quantity::bigint * unit_price::bigint) AS price_mismatch,
        BOOL_OR(priced_quantity < 0 OR amount < 0) AS signed_amount
    FROM direct_sale_prices GROUP BY sales_slip_id
), eligible_matches AS (
    SELECT matched.id, matched.target_id, matched.parent_event_id, matched.amount
    FROM partner_payment_events matched
    JOIN partner_payment_events received ON received.id = matched.parent_event_id
    JOIN direct_sales sale ON sale.sales_slip_id = matched.target_id
    WHERE matched.target_type = 'SALES_SLIP' AND matched.event_type = 'MANUAL_MATCH_CONFIRMED'
        AND matched.status = 'CONFIRMED' AND matched.amount > 0
        AND received.event_type = 'PAYMENT_RECEIVED' AND received.status = 'FULLY_APPLIED'
        AND received.unapplied_amount = 0 AND received.target_type = matched.target_type
        AND received.target_id = matched.target_id AND received.partner_id = sale.partner_id
        AND matched.partner_id = sale.partner_id AND received.amount = matched.amount
), valid_matches AS (
    SELECT target_id, parent_event_id, MAX(amount) AS amount, COUNT(*) AS matches
    FROM eligible_matches GROUP BY target_id, parent_event_id
), allocations AS (
    SELECT target_id, SUM(amount)::bigint AS amount, BOOL_OR(matches <> 1) AS duplicate_match
    FROM valid_matches GROUP BY target_id
), ledger_review AS (
    SELECT event.target_id, BOOL_OR(
        CASE WHEN event.event_type = 'PAYMENT_RECEIVED' THEN NOT EXISTS (
            SELECT 1 FROM valid_matches valid
            WHERE valid.target_id = event.target_id AND valid.parent_event_id = event.id)
        WHEN event.event_type = 'MANUAL_MATCH_CONFIRMED' THEN NOT EXISTS (
            SELECT 1 FROM eligible_matches valid WHERE valid.id = event.id)
        ELSE TRUE END) AS required
    FROM partner_payment_events event WHERE event.target_type = 'SALES_SLIP'
    GROUP BY event.target_id
)
INSERT INTO direct_sale_amount_reconciliations
SELECT sale.sales_slip_id, slip.paid_amount, slip.remaining_amount, slip.payment_status,
    COALESCE(items.amount, 0), COALESCE(allocations.amount, 0),
    sale.total_amount::bigint <> COALESCE(items.amount, 0), COALESCE(items.price_mismatch, FALSE),
    slip.paid_amount IS DISTINCT FROM COALESCE(allocations.amount, 0),
    slip.remaining_amount IS DISTINCT FROM GREATEST(0::bigint, sale.total_amount::bigint - COALESCE(allocations.amount, 0)),
    COALESCE(review.required, FALSE) OR COALESCE(allocations.duplicate_match, FALSE),
    sale.total_amount < 0 OR COALESCE(items.signed_amount, FALSE) OR COALESCE(slip.paid_amount < 0, FALSE)
FROM direct_sales sale JOIN sales_slips slip ON slip.id = sale.sales_slip_id
LEFT JOIN item_facts items ON items.sales_slip_id = sale.sales_slip_id
LEFT JOIN allocations ON allocations.target_id = sale.sales_slip_id
LEFT JOIN ledger_review review ON review.target_id = sale.sales_slip_id;
