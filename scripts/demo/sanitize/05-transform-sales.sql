UPDATE inbound_records
SET bottle_count = bottle_count * (SELECT quantity_factor FROM demo_sanitize_config),
    estimated_quantity = estimated_quantity * (SELECT quantity_factor FROM demo_sanitize_config),
    actual_quantity = actual_quantity * (SELECT quantity_factor FROM demo_sanitize_config),
    tray_count = tray_count * (SELECT quantity_factor FROM demo_sanitize_config);

UPDATE orchid_groups
SET quantity = quantity * (SELECT quantity_factor FROM demo_sanitize_config),
    tray_count = tray_count * (SELECT quantity_factor FROM demo_sanitize_config),
    reserved_quantity = reserved_quantity * (SELECT quantity_factor FROM demo_sanitize_config);

UPDATE orchid_group_lineage
SET source_quantity = source_quantity * (SELECT quantity_factor FROM demo_sanitize_config),
    result_quantity = result_quantity * (SELECT quantity_factor FROM demo_sanitize_config);
UPDATE work_operation_targets
SET quantity_snapshot = quantity_snapshot * (SELECT quantity_factor FROM demo_sanitize_config);
UPDATE work_target_executions
SET processed_quantity = processed_quantity * (SELECT quantity_factor FROM demo_sanitize_config);

UPDATE sales_slip_item_allocations
SET allocated_quantity = allocated_quantity * (SELECT quantity_factor FROM demo_sanitize_config);
UPDATE sales_inventory_movements
SET quantity_delta = quantity_delta * (SELECT quantity_factor FROM demo_sanitize_config);
UPDATE sales_slip_items
SET quantity = quantity * (SELECT quantity_factor FROM demo_sanitize_config),
    unit_price = unit_price * (SELECT price_factor FROM demo_sanitize_config),
    amount = amount * (
      SELECT quantity_factor * price_factor FROM demo_sanitize_config
    );
UPDATE sales_slips
SET total_amount = total_amount * (
      SELECT quantity_factor * price_factor FROM demo_sanitize_config
    ),
    paid_amount = paid_amount * (
      SELECT quantity_factor * price_factor FROM demo_sanitize_config
    ),
    remaining_amount = remaining_amount * (
      SELECT quantity_factor * price_factor FROM demo_sanitize_config
    );

UPDATE auction_shipment_lots
SET boxes = boxes * (SELECT quantity_factor FROM demo_sanitize_config),
    returned_quantity = returned_quantity * (SELECT quantity_factor FROM demo_sanitize_config),
    shipped_quantity = shipped_quantity * (SELECT quantity_factor FROM demo_sanitize_config),
    sold_quantity = sold_quantity * (SELECT quantity_factor FROM demo_sanitize_config),
    waiting_quantity = waiting_quantity * (SELECT quantity_factor FROM demo_sanitize_config);
UPDATE auction_result_lines
SET quantity = quantity * (SELECT quantity_factor FROM demo_sanitize_config),
    unit_price = unit_price * (SELECT price_factor FROM demo_sanitize_config),
    amount = amount * (
      SELECT quantity_factor * price_factor FROM demo_sanitize_config
    );
UPDATE auction_settlement_lines
SET quantity = quantity * (SELECT quantity_factor FROM demo_sanitize_config),
    unit_price = unit_price * (SELECT price_factor FROM demo_sanitize_config),
    amount = amount * (
      SELECT quantity_factor * price_factor FROM demo_sanitize_config
    );
UPDATE auction_settlements
SET deduction_amount = deduction_amount * (
      SELECT quantity_factor * price_factor FROM demo_sanitize_config
    ),
    expected_deposit_amount = expected_deposit_amount * (
      SELECT quantity_factor * price_factor FROM demo_sanitize_config
    ),
    fee_amount = fee_amount * (
      SELECT quantity_factor * price_factor FROM demo_sanitize_config
    ),
    gross_amount = gross_amount * (
      SELECT quantity_factor * price_factor FROM demo_sanitize_config
    ),
    paid_amount = paid_amount * (
      SELECT quantity_factor * price_factor FROM demo_sanitize_config
    ),
    remaining_amount = remaining_amount * (
      SELECT quantity_factor * price_factor FROM demo_sanitize_config
    );

UPDATE partner_payment_events
SET amount = amount * (
      SELECT quantity_factor * price_factor FROM demo_sanitize_config
    ),
    unapplied_amount = unapplied_amount * (
      SELECT quantity_factor * price_factor FROM demo_sanitize_config
    );
UPDATE partner_balance_summaries
SET credit_balance = credit_balance * (
      SELECT quantity_factor * price_factor FROM demo_sanitize_config
    ),
    receivable_balance = receivable_balance * (
      SELECT quantity_factor * price_factor FROM demo_sanitize_config
    ),
    unapplied_payment_amount = unapplied_payment_amount * (
      SELECT quantity_factor * price_factor FROM demo_sanitize_config
    );
UPDATE partner_settlement_settings
SET amount_tolerance = amount_tolerance * (
  SELECT quantity_factor * price_factor FROM demo_sanitize_config
);
