UPDATE business_partners
SET name = CASE partner_type
      WHEN 'AUCTION_HOUSE' THEN '데모 경매장 ' || lpad(id::text, 3, '0')
      ELSE '데모 거래처 ' || lpad(id::text, 3, '0')
    END,
    owner_name = CASE WHEN owner_name IS NULL THEN NULL ELSE '데모 대표 ' || lpad(id::text, 3, '0') END,
    phone = CASE WHEN phone IS NULL THEN NULL ELSE '010-0000-0000' END,
    address = CASE WHEN address IS NULL THEN NULL ELSE '데모 주소' END,
    memo = NULL;

CREATE TEMP TABLE demo_depositor_map (
  original_value text PRIMARY KEY,
  demo_value text NOT NULL
) ON COMMIT DROP;

INSERT INTO demo_depositor_map(original_value, demo_value)
SELECT depositor_name,
       '입금자 ' || lpad(
         row_number() OVER (ORDER BY pg_temp.demo_token(depositor_name, ''))::text,
         3,
         '0'
       )
FROM partner_payment_events
WHERE depositor_name IS NOT NULL AND btrim(depositor_name) <> ''
GROUP BY depositor_name;

UPDATE partner_payment_events t SET depositor_name = m.demo_value
FROM demo_depositor_map m WHERE t.depositor_name = m.original_value;

UPDATE partner_settlement_settings
SET depositor_aliases = '[]'::jsonb;
