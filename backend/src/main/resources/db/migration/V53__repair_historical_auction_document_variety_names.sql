-- Correct the V52 item label from its linked lot's recorded variety name.
-- Also restore the original CSV item classification as genus without querying current Farm data.
LOCK TABLE auction_shipment_lots, sales_slips, sales_slip_items IN SHARE ROW EXCLUSIVE MODE;
UPDATE sales_slip_items i
SET item_name = l.variety_name
FROM sales_slips s, auction_shipment_lots l
WHERE i.sales_slip_id = s.id
  AND s.historical_auction_import
  AND i.auction_shipment_lot_id = l.id
  AND s.auction_shipment_id = l.shipment_id
  AND i.item_name = l.item_name
  AND NULLIF(btrim(l.variety_name), '') IS NOT NULL
  AND i.item_name IS DISTINCT FROM l.variety_name;

UPDATE sales_slip_items i
SET genus = l.item_name
FROM sales_slips s, auction_shipment_lots l
WHERE i.sales_slip_id = s.id
  AND s.historical_auction_import
  AND i.auction_shipment_lot_id = l.id
  AND s.auction_shipment_id = l.shipment_id
  AND i.item_name = l.variety_name
  AND NULLIF(btrim(i.genus), '') IS NULL
  AND NULLIF(btrim(l.item_name), '') IS NOT NULL
  AND l.item_name IS DISTINCT FROM l.variety_name;
