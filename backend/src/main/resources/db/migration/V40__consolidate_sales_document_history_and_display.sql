-- Documents are identified by their existing primary keys. Keep historical receipt/audit JSON intact.
ALTER TABLE sales_slips DROP COLUMN slip_number;
DROP TABLE sales_slip_daily_sequences;

ALTER TABLE sales_slip_items
    ADD COLUMN unit varchar(30),
    ADD COLUMN boxes integer;


ALTER TABLE partner_settlement_settings
    ADD COLUMN print_template VARCHAR(50) NOT NULL DEFAULT 'STANDARD_A5',
    ADD COLUMN shipper_code VARCHAR(50),
    ADD COLUMN shipper_name VARCHAR(100);

ALTER TABLE partner_settlement_settings
    ADD CONSTRAINT ck_partner_print_template
        CHECK (print_template IN ('STANDARD_A5', 'YANGJAE_AUCTION_SHIPMENT'));

-- Historical dispatch facts only: never replay reservation, outbound or Farm mutations.
-- Deploy with application writers stopped. Flyway applies this migration atomically.
LOCK TABLE auction_shipments, auction_shipment_lots, sales_slips, sales_slip_items IN SHARE ROW EXCLUSIVE MODE;
CREATE TEMPORARY TABLE historical_auction_document_sources ON COMMIT DROP AS
SELECT a.* FROM auction_shipments a
WHERE NOT EXISTS (SELECT 1 FROM sales_slips s WHERE s.auction_shipment_id = a.id)
  AND EXISTS (SELECT 1 FROM auction_shipment_lots l WHERE l.shipment_id = a.id);
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM historical_auction_document_sources a
        JOIN auction_shipment_lots l ON l.shipment_id = a.id
        WHERE l.shipped_quantity <= 0
           OR l.shipped_quantity::bigint <> l.sold_quantity::bigint + l.waiting_quantity + l.returned_quantity
           OR EXISTS (SELECT 1 FROM sales_slip_items i WHERE i.auction_shipment_lot_id = l.id)
    ) THEN
        RAISE EXCEPTION 'Historical auction document import: invalid quantity or conflicting lot ownership';
    END IF;
    IF EXISTS (
        SELECT 1 FROM historical_auction_document_sources a
        JOIN business_partners p ON p.id = a.auction_house_id
        WHERE p.partner_type <> 'AUCTION_HOUSE'
    ) THEN
        RAISE EXCEPTION 'Historical auction document import: partner is not an auction house';
    END IF;
END $$;
INSERT INTO sales_slips (
    id, created_at, updated_at, version, sale_date, sales_type,
    auction_shipment_id, partner_id, total_amount, expected_payment_date,
    paid_amount, remaining_amount, payment_status, sales_status, payment_method, memo
)
SELECT nextval('sales_slips_id_seq'), a.created_at, a.updated_at, 0,
       a.shipment_date, 'AUCTION', a.id, a.auction_house_id,
       0, NULL, 0, 0, '정산 대기', '출하 완료', '경매 정산', a.memo
FROM historical_auction_document_sources a ORDER BY a.id;
INSERT INTO sales_slip_items (
    id, sales_slip_id, auction_shipment_lot_id,
    item_name, genus, spec, quantity, unit_price, amount, memo
)
SELECT nextval('sales_slip_items_id_seq'), s.id, l.id,
       CASE WHEN NULLIF(btrim(l.variety_name), '') IS NOT NULL THEN l.variety_name ELSE l.item_name END,
       CASE WHEN NULLIF(btrim(l.variety_name), '') IS NOT NULL AND NULLIF(btrim(l.item_name), '') IS NOT NULL
            AND l.item_name IS DISTINCT FROM l.variety_name THEN l.item_name ELSE NULL END, l.shipment_grade, l.shipped_quantity, 0, 0, l.memo
FROM historical_auction_document_sources a
JOIN sales_slips s ON s.auction_shipment_id = a.id
JOIN auction_shipment_lots l ON l.shipment_id = a.id ORDER BY a.id, l.id;

-- The historical lot already contains this fact; preserve it on the migrated document item.
UPDATE sales_slip_items AS item
SET boxes = lot.boxes
FROM auction_shipment_lots AS lot
WHERE item.auction_shipment_lot_id = lot.id
  AND lot.boxes > 0;

ALTER TABLE sales_slip_items
    ADD CONSTRAINT sales_slip_items_boxes_positive CHECK (boxes IS NULL OR boxes > 0);

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
