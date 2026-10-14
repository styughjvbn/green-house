-- Preserve historical signed returns without rewriting quantities, amounts or payments.
-- Enforce exact multiplication in BIGINT to reject both positive and negative INTEGER
-- overflow. The API retains its normal-sale rules; the future return model is separate.
ALTER TABLE sales_slip_items
    ADD CONSTRAINT ck_sales_slip_items_amount CHECK (
        quantity <> 0
        AND unit_price >= 0
        AND amount::bigint = quantity::bigint * unit_price::bigint
    ) NOT VALID;

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


-- Keep explicit legacy display labels while paid amounts come from valid allocations.
-- A label never creates a payment fact or determines payment eligibility.
ALTER TABLE direct_sales ADD COLUMN unpaid_payment_label VARCHAR(255) NOT NULL DEFAULT '미입금';
UPDATE direct_sales sale SET unpaid_payment_label = document.payment_status
FROM sales_slips document WHERE document.id = sale.sales_slip_id;

WITH eligible AS (
    SELECT matched.target_id, matched.parent_event_id, MAX(matched.amount) AS amount
    FROM partner_payment_events matched
    JOIN partner_payment_events received ON received.id = matched.parent_event_id
    JOIN direct_sales sale ON sale.sales_slip_id = matched.target_id
    WHERE matched.target_type = 'SALES_SLIP' AND matched.event_type = 'MANUAL_MATCH_CONFIRMED'
        AND matched.status = 'CONFIRMED' AND matched.amount > 0
        AND received.event_type = 'PAYMENT_RECEIVED' AND received.status = 'FULLY_APPLIED'
        AND received.unapplied_amount = 0 AND received.target_type = matched.target_type
        AND received.target_id = matched.target_id AND received.partner_id = sale.partner_id
        AND matched.partner_id = sale.partner_id AND received.amount = matched.amount
    GROUP BY matched.target_id, matched.parent_event_id
), allocated AS (
    SELECT target_id, SUM(amount) AS amount FROM eligible GROUP BY target_id
)
UPDATE sales_slips document SET
    total_amount = sale.total_amount,
    expected_payment_date = sale.expected_payment_date,
    payment_method = sale.payment_method,
    paid_amount = COALESCE(allocated.amount, 0)::bigint,
    remaining_amount = GREATEST(0, sale.total_amount::numeric - COALESCE(allocated.amount, 0))::bigint,
    payment_status = CASE
        WHEN COALESCE(allocated.amount, 0) = 0 THEN sale.unpaid_payment_label
        WHEN allocated.amount >= sale.total_amount THEN '입금 완료'
        ELSE '부분입금' END
FROM direct_sales sale LEFT JOIN allocated ON allocated.target_id = sale.sales_slip_id
WHERE document.id = sale.sales_slip_id;

-- Item values are compatibility projections of the original Direct pricing facts.
UPDATE sales_slip_items item SET unit_price = price.unit_price, amount = price.amount
FROM direct_sale_prices price WHERE price.sales_slip_item_id = item.id
    AND (item.unit_price IS DISTINCT FROM price.unit_price OR item.amount IS DISTINCT FROM price.amount);

UPDATE partner_balance_summaries balance SET receivable_balance = COALESCE((
    SELECT SUM(document.remaining_amount)
    FROM sales_slips document
    WHERE document.partner_id = balance.partner_id
        AND (document.sales_type = 'DIRECT' OR document.sales_type IS NULL) AND document.sales_status <> '취소'
), 0);
