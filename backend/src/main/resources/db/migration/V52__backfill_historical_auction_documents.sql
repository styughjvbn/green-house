-- Historical dispatch facts only: never replay reservation, outbound or Farm mutations.
-- Deploy with application writers stopped. Flyway applies this migration atomically.
ALTER TABLE sales_slips ADD COLUMN historical_auction_import boolean NOT NULL DEFAULT false;
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
    IF EXISTS (
        SELECT 1 FROM historical_auction_document_sources a
        JOIN sales_slips s ON s.slip_number = 'HIST-AUC-' || a.id::text
    ) THEN
        RAISE EXCEPTION 'Historical auction document import: document number conflict';
    END IF;
END $$;
INSERT INTO sales_slips (
    id, created_at, updated_at, version, slip_number, sale_date, sales_type,
    auction_shipment_id, partner_id, total_amount, expected_payment_date,
    paid_amount, remaining_amount, payment_status, sales_status, payment_method, memo, historical_auction_import
)
SELECT nextval('sales_slips_id_seq'), a.created_at, a.updated_at, 0,
       'HIST-AUC-' || a.id::text, a.shipment_date, 'AUCTION', a.id, a.auction_house_id,
       0, NULL, 0, 0, '정산 대기', '출하 완료', '경매 정산', a.memo, true
FROM historical_auction_document_sources a ORDER BY a.id;
INSERT INTO sales_slip_items (
    id, sales_slip_id, auction_shipment_lot_id,
    item_name, genus, spec, quantity, unit_price, amount, memo
)
SELECT nextval('sales_slip_items_id_seq'), s.id, l.id,
       l.item_name, NULL, l.shipment_grade, l.shipped_quantity, 0, 0, l.memo
FROM historical_auction_document_sources a
JOIN sales_slips s ON s.auction_shipment_id = a.id AND s.historical_auction_import
JOIN auction_shipment_lots l ON l.shipment_id = a.id ORDER BY a.id, l.id;
ALTER TABLE sales_slips ADD CONSTRAINT ck_sales_slips_historical_auction_import
    CHECK (NOT historical_auction_import OR (
        sales_type = 'AUCTION' AND sales_type IS NOT NULL
        AND auction_shipment_id IS NOT NULL AND sales_status = '출하 완료'
    ));
