CREATE TEMP TABLE demo_actor_map (
  original_value text PRIMARY KEY,
  demo_value text NOT NULL
) ON COMMIT DROP;

INSERT INTO demo_actor_map(original_value, demo_value)
SELECT original_value,
       '작업자 ' || lpad(
         row_number() OVER (ORDER BY pg_temp.demo_token(original_value, ''))::text,
         3,
         '0'
       )
FROM (
  SELECT worker AS original_value FROM auction_lot_status_history
  UNION SELECT confirmed_by FROM auction_settlements
  UNION SELECT worker FROM inbound_records
  UNION SELECT created_by FROM partner_payment_events
  UNION SELECT worker FROM work_records
  UNION SELECT created_by FROM orchid_group_collections
  UNION SELECT created_by FROM orchid_group_collection_members
  UNION SELECT worker FROM work_operations
  UNION SELECT worker FROM work_target_executions
  UNION SELECT worker FROM work_applied_effects
) actors
WHERE original_value IS NOT NULL AND btrim(original_value) <> '';

UPDATE auction_lot_status_history t SET worker = m.demo_value
FROM demo_actor_map m WHERE t.worker = m.original_value;
UPDATE auction_settlements t SET confirmed_by = m.demo_value
FROM demo_actor_map m WHERE t.confirmed_by = m.original_value;
UPDATE inbound_records t SET worker = m.demo_value
FROM demo_actor_map m WHERE t.worker = m.original_value;
UPDATE partner_payment_events t SET created_by = m.demo_value
FROM demo_actor_map m WHERE t.created_by = m.original_value;
UPDATE work_records t SET worker = m.demo_value
FROM demo_actor_map m WHERE t.worker = m.original_value;
UPDATE orchid_group_collections t SET created_by = m.demo_value
FROM demo_actor_map m WHERE t.created_by = m.original_value;
UPDATE orchid_group_collection_members t SET created_by = m.demo_value
FROM demo_actor_map m WHERE t.created_by = m.original_value;
UPDATE work_operations t SET worker = m.demo_value
FROM demo_actor_map m WHERE t.worker = m.original_value;
UPDATE work_target_executions t SET worker = m.demo_value
FROM demo_actor_map m WHERE t.worker = m.original_value;
UPDATE work_applied_effects t SET worker = m.demo_value
FROM demo_actor_map m WHERE t.worker = m.original_value;
