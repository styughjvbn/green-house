-- Refresh query projections from their owners. V43 reconciliation evidence,
-- actual cash, original receipt responses, snapshots and Farm identities are unchanged.
-- Capture discrepancies at cutover without overwriting the original V43 evidence.
ALTER TABLE direct_sale_amount_reconciliations ADD COLUMN cutover_legacy_snapshot JSONB;
ALTER TABLE direct_sale_amount_reconciliations ADD COLUMN cutover_review_required BOOLEAN NOT NULL DEFAULT FALSE;
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM sales_slips document LEFT JOIN direct_sales sale ON sale.sales_slip_id=document.id
        WHERE (document.sales_type='DIRECT' OR document.sales_type IS NULL) AND sale.sales_slip_id IS NULL) THEN
        RAISE EXCEPTION 'Direct financial source is missing; resolve ownership before cutover';
    END IF;
END $$;
CREATE TEMP TABLE direct_cutover_facts ON COMMIT DROP AS
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

SELECT document.id, document.paid_amount, document.remaining_amount, document.payment_status,
    (SELECT COALESCE(SUM(item.amount::bigint),0) FROM sales_slip_items item WHERE item.sales_slip_id=document.id) AS item_sum,
    COALESCE(allocated.amount,0)::bigint AS allocated_amount,
    jsonb_build_object('totalAmount',document.total_amount,'paidAmount',document.paid_amount,
        'remainingAmount',document.remaining_amount,'paymentStatus',document.payment_status,
        'partnerId',document.partner_id,'saleDate',document.sale_date,
        'expectedPaymentDate',document.expected_payment_date,'paymentMethod',document.payment_method,
        'items',(SELECT jsonb_agg(jsonb_build_object('id',item.id,'quantity',item.quantity,
            'unitPrice',item.unit_price,'amount',item.amount) ORDER BY item.id)
            FROM sales_slip_items item WHERE item.sales_slip_id=document.id)) AS snapshot,
    document.total_amount IS DISTINCT FROM sale.total_amount
        OR document.paid_amount IS DISTINCT FROM COALESCE(allocated.amount,0)
        OR document.remaining_amount IS DISTINCT FROM GREATEST(0,sale.total_amount::numeric-COALESCE(allocated.amount,0))
        OR document.partner_id IS DISTINCT FROM sale.partner_id
        OR document.sale_date IS DISTINCT FROM sale.sale_date
        OR document.expected_payment_date IS DISTINCT FROM sale.expected_payment_date
        OR document.payment_method IS DISTINCT FROM sale.payment_method
        OR EXISTS (SELECT 1 FROM sales_slip_items item LEFT JOIN direct_sale_prices price ON price.sales_slip_item_id=item.id
            WHERE item.sales_slip_id=document.id AND (price.sales_slip_item_id IS NULL
                OR item.quantity IS DISTINCT FROM price.priced_quantity OR item.unit_price IS DISTINCT FROM price.unit_price
                OR item.amount IS DISTINCT FROM price.amount)) AS required
FROM direct_sales sale JOIN sales_slips document ON document.id=sale.sales_slip_id
LEFT JOIN allocated ON allocated.target_id=sale.sales_slip_id;

UPDATE direct_sale_amount_reconciliations evidence SET cutover_legacy_snapshot=fact.snapshot,cutover_review_required=TRUE
FROM direct_cutover_facts fact WHERE fact.id=evidence.sales_slip_id AND fact.required;
INSERT INTO direct_sale_amount_reconciliations (sales_slip_id,stored_paid_amount,stored_remaining_amount,
    stored_payment_status,stored_item_amount_sum,confirmed_allocation_amount,total_mismatch,price_mismatch,
    paid_mismatch,remaining_mismatch,ledger_review_required,signed_amount_review_required,
    cutover_legacy_snapshot,cutover_review_required)
SELECT id,paid_amount,remaining_amount,payment_status,item_sum,allocated_amount,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,
    snapshot,TRUE FROM direct_cutover_facts fact WHERE required
    AND NOT EXISTS (SELECT 1 FROM direct_sale_amount_reconciliations evidence WHERE evidence.sales_slip_id=fact.id);

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
