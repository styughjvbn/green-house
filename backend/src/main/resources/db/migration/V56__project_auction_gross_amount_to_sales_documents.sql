-- Current query projection only. Do not change receivables, payments, historical snapshots,
-- receipt responses, inventory, or item prices. Deploy with application writers stopped.
LOCK TABLE sales_slips, sales_slip_items, auction_attempts, auction_result_lines IN SHARE ROW EXCLUSIVE MODE;
WITH gross AS (
    SELECT s.id, COALESCE(SUM(r.amount::bigint), 0) AS amount
    FROM sales_slips s
    LEFT JOIN sales_slip_items i ON i.sales_slip_id = s.id
    LEFT JOIN auction_attempts a ON a.shipment_lot_id = i.auction_shipment_lot_id
    LEFT JOIN auction_result_lines r ON r.auction_attempt_id = a.id
    WHERE s.sales_type = 'AUCTION'
    GROUP BY s.id
)
UPDATE sales_slips s SET total_amount = g.amount::integer
FROM gross g WHERE s.id = g.id AND s.total_amount IS DISTINCT FROM g.amount;
COMMENT ON COLUMN sales_slips.total_amount IS
    'Current sales query projection: DIRECT agreed amount; AUCTION sum of linked lot result amounts. Not auction receivables or receipts.';
