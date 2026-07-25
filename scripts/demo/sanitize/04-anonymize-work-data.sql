DO $block$
DECLARE
  target record;
  shift_days integer;
BEGIN
  SELECT date_shift_days INTO shift_days FROM demo_sanitize_config;

  FOR target IN
    SELECT table_name, column_name
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name <> 'flyway_schema_history'
      AND data_type IN ('date', 'timestamp without time zone', 'timestamp with time zone')
  LOOP
    EXECUTE format(
      'UPDATE public.%I SET %I = %I + make_interval(days => %L) WHERE %I IS NOT NULL',
      target.table_name, target.column_name, target.column_name, shift_days, target.column_name
    );
  END LOOP;
END
$block$;

UPDATE varieties
SET name = pg_temp.demo_token(name, '품종-'),
    alias = CASE WHEN alias IS NULL THEN NULL ELSE pg_temp.demo_token(alias, '품종별칭-') END,
    genus = pg_temp.demo_token(genus, '속-');

UPDATE orchid_groups
SET variety_name = pg_temp.demo_token(variety_name, '품종-'),
    genus = CASE WHEN genus IS NULL THEN NULL ELSE pg_temp.demo_token(genus, '속-') END;

UPDATE auction_shipment_lots
SET variety_name = pg_temp.demo_token(variety_name, '품종-'),
    item_name = pg_temp.demo_token(item_name, '품목-');

UPDATE sales_slip_items
SET item_name = pg_temp.demo_token(item_name, '품목-'),
    genus = CASE WHEN genus IS NULL THEN NULL ELSE pg_temp.demo_token(genus, '속-') END;

UPDATE work_operation_targets
SET variety_name_snapshot = pg_temp.demo_token(variety_name_snapshot, '품종-');

UPDATE orchid_group_collections
SET name = '데모 묶음 ' || lpad(id::text, 3, '0');
UPDATE work_operations
SET title = '데모 작업 ' || lpad(id::text, 4, '0'),
    request_key = CASE WHEN request_key IS NULL THEN NULL ELSE 'demo-request-' || id END;

UPDATE materials
SET name = pg_temp.demo_token(name, '데모 자재-'),
    manufacturer = CASE WHEN manufacturer IS NULL THEN NULL ELSE '데모 제조사' END,
    storage_location = CASE WHEN storage_location IS NULL THEN NULL ELSE '데모 보관 위치' END,
    usage = CASE WHEN usage IS NULL THEN NULL ELSE '데모 용도' END;

UPDATE inbound_records
SET temp_location = CASE WHEN temp_location IS NULL THEN NULL ELSE '데모 임시 위치' END;

UPDATE sales_slips SET slip_number = 'DEMO-SALE-' || lpad(id::text, 8, '0');
UPDATE work_records
SET material_name = CASE
      WHEN material_name IS NULL THEN NULL
      ELSE pg_temp.demo_token(material_name, '데모 자재-')
    END,
    quantity = CASE
      WHEN quantity IS NULL THEN NULL
      WHEN quantity ~ '^[0-9]+$' THEN (
        quantity::bigint * (SELECT quantity_factor FROM demo_sanitize_config)
      )::text
      ELSE '데모 수량'
    END;
