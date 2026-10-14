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
    WHERE amount::bigint <> quantity::bigint * unit_price
  ) THEN
    RAISE EXCEPTION 'Sales item amount does not match quantity and unit price';
  END IF;

  IF EXISTS (
    SELECT 1 FROM direct_sales sale
    LEFT JOIN (
      SELECT sales_slip_id, sum(amount::bigint) AS original_total
      FROM direct_sale_prices GROUP BY sales_slip_id
    ) prices ON prices.sales_slip_id=sale.sales_slip_id
    LEFT JOIN (
      SELECT sales_slip_id, sum(amount_delta) AS delta
      FROM direct_sale_changes GROUP BY sales_slip_id
    ) changes ON changes.sales_slip_id=sale.sales_slip_id
    JOIN sales_slips document ON document.id=sale.sales_slip_id
    WHERE sale.total_amount <> coalesce(prices.original_total,0) + coalesce(changes.delta,0)
       OR document.total_amount <> sale.total_amount
  ) THEN
    RAISE EXCEPTION 'Direct financial total does not match original prices and change history';
  END IF;

  IF EXISTS (
    SELECT 1 FROM sales_slips document
    LEFT JOIN (
      SELECT item.sales_slip_id, sum(result.amount::bigint) AS gross
      FROM sales_slip_items item
      JOIN auction_attempts attempt ON attempt.shipment_lot_id=item.auction_shipment_lot_id
      JOIN auction_result_lines result ON result.auction_attempt_id=attempt.id
      GROUP BY item.sales_slip_id
    ) results ON results.sales_slip_id=document.id
    WHERE document.sales_type='AUCTION' AND document.total_amount <> coalesce(results.gross,0)
  ) THEN
    RAISE EXCEPTION 'Auction document gross does not match linked lot results';
  END IF;

  IF EXISTS (
    SELECT 1 FROM sales_slips
    WHERE coalesce(sales_type, 'DIRECT')='DIRECT'
      AND coalesce(remaining_amount, 0) <> greatest(0, total_amount::bigint - coalesce(paid_amount, 0))
  ) THEN
    RAISE EXCEPTION 'Sales slip remaining amount is inconsistent';
  END IF;

  -- Imported auction amounts can differ from quantity * unit_price. Preserve
  -- the recorded amount and inspection flag; validate the document's gross
  -- against those recorded results above instead of repairing historical data.

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
