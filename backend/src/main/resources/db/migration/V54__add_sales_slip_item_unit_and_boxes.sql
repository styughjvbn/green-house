ALTER TABLE sales_slip_items
    ADD COLUMN unit varchar(30),
    ADD COLUMN boxes integer;

-- The historical lot already contains this fact; preserve it on the migrated document item.
UPDATE sales_slip_items AS item
SET boxes = lot.boxes
FROM auction_shipment_lots AS lot
WHERE item.auction_shipment_lot_id = lot.id
  AND lot.boxes > 0;

ALTER TABLE sales_slip_items
    ADD CONSTRAINT sales_slip_items_boxes_positive CHECK (boxes IS NULL OR boxes > 0);
