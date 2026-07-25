CREATE EXTENSION IF NOT EXISTS pgcrypto;

\getenv demo_anonymization_key DEMO_ANONYMIZATION_KEY
\getenv demo_date_shift_days DEMO_DATE_SHIFT_DAYS
\getenv demo_quantity_factor DEMO_QUANTITY_FACTOR
\getenv demo_price_factor DEMO_PRICE_FACTOR

CREATE TEMP TABLE demo_sanitize_config (
  anonymization_key text NOT NULL,
  date_shift_days integer NOT NULL,
  quantity_factor integer NOT NULL,
  price_factor integer NOT NULL
) ON COMMIT DROP;

INSERT INTO demo_sanitize_config
VALUES (
  :'demo_anonymization_key',
  :'demo_date_shift_days',
  :'demo_quantity_factor',
  :'demo_price_factor'
);

CREATE FUNCTION pg_temp.demo_token(input_value text, prefix text)
RETURNS text
LANGUAGE sql
STABLE
STRICT
AS $function$
  SELECT prefix || substr(
    encode(
      hmac(
        convert_to(input_value, 'UTF8'),
        convert_to((SELECT anonymization_key FROM demo_sanitize_config), 'UTF8'),
        'sha256'
      ),
      'hex'
    ),
    1,
    12
  )
$function$;

CREATE TEMP TABLE demo_original_sensitive_values (
  original_value text PRIMARY KEY
) ON COMMIT DROP;

INSERT INTO demo_original_sensitive_values(original_value)
SELECT DISTINCT btrim(original_value)
FROM (
  SELECT address AS original_value FROM business_partners
  UNION ALL SELECT memo FROM business_partners
  UNION ALL SELECT name FROM business_partners
  UNION ALL SELECT owner_name FROM business_partners
  UNION ALL SELECT phone FROM business_partners
  UNION ALL SELECT worker FROM auction_lot_status_history
  UNION ALL SELECT confirmed_by FROM auction_settlements
  UNION ALL SELECT worker FROM inbound_records
  UNION ALL SELECT created_by FROM partner_payment_events
  UNION ALL SELECT depositor_name FROM partner_payment_events
  UNION ALL SELECT description FROM partner_payment_events
  UNION ALL SELECT memo FROM partner_payment_events
  UNION ALL SELECT external_uid FROM partner_payment_events
  UNION ALL SELECT worker FROM work_records
  UNION ALL SELECT created_by FROM orchid_group_collections
  UNION ALL SELECT created_by FROM orchid_group_collection_members
  UNION ALL SELECT worker FROM work_operations
  UNION ALL SELECT worker FROM work_target_executions
  UNION ALL SELECT worker FROM work_applied_effects
  UNION ALL SELECT manufacturer FROM materials
  UNION ALL SELECT storage_location FROM materials
  UNION ALL SELECT usage FROM materials
  UNION ALL SELECT memo FROM auction_attempts
  UNION ALL SELECT note FROM auction_result_lines
  UNION ALL SELECT memo FROM auction_settlements
  UNION ALL SELECT memo FROM auction_shipment_lots
  UNION ALL SELECT memo FROM auction_shipments
  UNION ALL SELECT memo FROM inbound_records
  UNION ALL SELECT memo FROM orchid_groups
  UNION ALL SELECT memo FROM sales_slips
  UNION ALL SELECT memo FROM sales_slip_items
  UNION ALL SELECT memo FROM work_records
  UNION ALL SELECT memo FROM work_operations
  UNION ALL SELECT cancel_reason FROM work_records
  UNION ALL SELECT exclusion_reason FROM work_operation_targets
  UNION ALL SELECT reason FROM work_operation_corrections
) original_values
WHERE original_value IS NOT NULL
  AND char_length(btrim(original_value)) >= 2
  AND btrim(original_value) !~ '^[A-Z][A-Z0-9_:-]{1,49}$';

UPDATE auction_attempts
SET memo = NULL,
    failed_reason = CASE WHEN failed_reason IS NULL THEN NULL ELSE '데모 사유' END;
UPDATE auction_lot_status_history SET memo = NULL, reason = '데모 상태 변경';
UPDATE auction_result_lines SET note = NULL;
UPDATE auction_settlements SET memo = NULL;
UPDATE auction_shipment_lots SET memo = NULL;
UPDATE auction_shipments SET memo = NULL;
UPDATE bed_zone_capacities SET memo = NULL;
UPDATE bed_zones SET memo = NULL;
UPDATE houses SET memo = NULL;
UPDATE inbound_records SET memo = NULL;
UPDATE orchid_groups SET memo = NULL;
UPDATE physical_beds SET memo = NULL;
UPDATE sales_inventory_movements SET memo = NULL;
UPDATE sales_slip_items SET memo = NULL;
UPDATE sales_slips SET memo = NULL;
UPDATE work_records
SET memo = NULL,
    cancel_reason = CASE WHEN cancel_reason IS NULL THEN NULL ELSE '데모 취소 사유' END;
UPDATE work_operations SET memo = NULL;
UPDATE work_operation_targets
SET exclusion_reason = CASE WHEN exclusion_reason IS NULL THEN NULL ELSE '데모 제외 사유' END;
UPDATE work_operation_corrections SET reason = '데모 보정 사유';

UPDATE partner_payment_events
SET description = NULL,
    memo = NULL,
    external_uid = NULL,
    raw_payload = '{}'::jsonb,
    match_payload = '{}'::jsonb;
UPDATE partner_settlement_settings SET memo = NULL;

UPDATE varieties SET description = NULL, memo = NULL;
UPDATE orchid_group_collections
SET description = NULL,
    purpose = CASE WHEN purpose IS NULL THEN NULL ELSE '데모 묶음' END;
