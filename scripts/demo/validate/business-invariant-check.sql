DO $block$
BEGIN
  IF EXISTS (
    SELECT 1 FROM orchid_groups
    WHERE quantity < 0 OR reserved_quantity < 0 OR reserved_quantity > quantity
  ) THEN
    RAISE EXCEPTION 'Invalid orchid group quantity';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM sales_slip_items item
    JOIN (
      SELECT sales_slip_item_id, sum(allocated_quantity) AS allocated_quantity
      FROM sales_slip_item_allocations
      GROUP BY sales_slip_item_id
    ) allocation ON allocation.sales_slip_item_id = item.id
    WHERE allocation.allocated_quantity <> item.quantity
  ) THEN
    RAISE EXCEPTION 'Sales item allocation total does not match item quantity';
  END IF;

  IF EXISTS (
    SELECT 1 FROM sales_slip_items
    WHERE amount <> quantity * unit_price
  ) THEN
    RAISE EXCEPTION 'Sales item amount does not match quantity and unit price';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM sales_slips slip
    JOIN (
      SELECT sales_slip_id, sum(amount) AS item_total
      FROM sales_slip_items
      GROUP BY sales_slip_id
    ) items ON items.sales_slip_id = slip.id
    WHERE slip.total_amount <> items.item_total
  ) THEN
    RAISE EXCEPTION 'Sales slip total does not match item total';
  END IF;

  IF EXISTS (
    SELECT 1 FROM sales_slips
    WHERE coalesce(remaining_amount, 0) <> greatest(0, total_amount::bigint - coalesce(paid_amount, 0))
  ) THEN
    RAISE EXCEPTION 'Sales slip remaining amount is inconsistent';
  END IF;

  IF EXISTS (
    SELECT 1 FROM auction_result_lines
    WHERE amount <> quantity::bigint * unit_price
  ) THEN
    RAISE EXCEPTION 'Auction line amount is inconsistent';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM auction_proceeds proceeds
    WHERE proceeds.reported_gross_amount < 0 OR proceeds.receivable_amount < 0
       OR (proceeds.matching_confirmed AND (
         proceeds.source_reference IS NULL OR proceeds.reported_gross_amount IS NULL
         OR NOT EXISTS (SELECT 1 FROM auction_proceeds_results reference
           WHERE reference.auction_proceeds_id=proceeds.id)))
  ) THEN
    RAISE EXCEPTION 'Auction proceeds evidence is inconsistent';
  END IF;

  IF EXISTS (
    SELECT 1 FROM auction_shipment_lots
    WHERE shipped_quantity < 0
       OR sold_quantity < 0
       OR returned_quantity < 0
       OR waiting_quantity < 0
       OR disposed_quantity < 0
       OR inferred_return_quantity < 0
       OR sold_quantity > shipped_quantity
       OR returned_quantity > shipped_quantity
       OR waiting_quantity > shipped_quantity
  ) THEN
    RAISE EXCEPTION 'Auction lot quantity is inconsistent';
  END IF;

  IF EXISTS (
    SELECT 1 FROM orchid_group_lineage
    WHERE source_quantity <= 0 OR result_quantity <= 0
  ) THEN
    RAISE EXCEPTION 'Orchid group lineage quantity is invalid';
  END IF;

  IF EXISTS (
    SELECT 1 FROM work_operations
    WHERE planned_end_date < planned_start_date
       OR actual_end_at < actual_start_at
  ) THEN
    RAISE EXCEPTION 'Work operation date order is invalid';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM auction_attempts attempt
    JOIN auction_shipment_lots lot ON lot.id = attempt.shipment_lot_id
    JOIN auction_shipments shipment ON shipment.id = lot.shipment_id
    WHERE attempt.auction_date < shipment.shipment_date
  ) THEN
    RAISE EXCEPTION 'Auction date precedes shipment date';
  END IF;
END
$block$;
