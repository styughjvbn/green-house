CREATE FUNCTION pg_temp.demo_sanitize_json(input_value jsonb)
RETURNS jsonb
LANGUAGE plpgsql
IMMUTABLE
AS $function$
DECLARE
  scalar_text text;
BEGIN
  IF input_value IS NULL THEN
    RETURN NULL;
  END IF;

  CASE jsonb_typeof(input_value)
    WHEN 'object' THEN
      RETURN (
        SELECT coalesce(jsonb_object_agg(key, pg_temp.demo_sanitize_json(value)), '{}'::jsonb)
        FROM jsonb_each(input_value)
      );
    WHEN 'array' THEN
      RETURN (
        SELECT coalesce(
          jsonb_agg(pg_temp.demo_sanitize_json(value) ORDER BY position),
          '[]'::jsonb
        )
        FROM jsonb_array_elements(input_value) WITH ORDINALITY items(value, position)
      );
    WHEN 'string' THEN
      scalar_text := input_value #>> '{}';
      IF scalar_text ~ '^[A-Z][A-Z0-9_:-]{0,49}$' THEN
        RETURN input_value;
      END IF;
      RETURN to_jsonb('데모'::text);
    ELSE
      RETURN input_value;
  END CASE;
END
$function$;

UPDATE auction_settlement_lines
SET line_meta_json = pg_temp.demo_sanitize_json(line_meta_json);
UPDATE auction_settlements
SET payment_meta_json = pg_temp.demo_sanitize_json(payment_meta_json);
UPDATE partner_balance_summaries
SET summary_json = pg_temp.demo_sanitize_json(summary_json);
UPDATE partner_payment_events
SET allocation_payload = pg_temp.demo_sanitize_json(allocation_payload),
    balance_snapshot_json = pg_temp.demo_sanitize_json(balance_snapshot_json),
    match_payload = pg_temp.demo_sanitize_json(match_payload),
    raw_payload = pg_temp.demo_sanitize_json(raw_payload);
UPDATE partner_settlement_settings
SET depositor_aliases = pg_temp.demo_sanitize_json(depositor_aliases),
    rule_json = pg_temp.demo_sanitize_json(rule_json);
UPDATE work_records SET details = pg_temp.demo_sanitize_json(details);
UPDATE work_operations
SET source_condition_snapshot = pg_temp.demo_sanitize_json(source_condition_snapshot),
    details = pg_temp.demo_sanitize_json(details);
UPDATE work_operation_targets
SET location_snapshot = pg_temp.demo_sanitize_json(location_snapshot);
UPDATE work_target_executions
SET result_details = pg_temp.demo_sanitize_json(result_details);
UPDATE work_applied_effects
SET command_details = pg_temp.demo_sanitize_json(command_details),
    result_details = pg_temp.demo_sanitize_json(result_details);

DO $block$
DECLARE
  target record;
  match_count bigint;
BEGIN
  FOR target IN
    SELECT table_name, column_name
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name <> 'flyway_schema_history'
      AND data_type IN ('character varying', 'text', 'json', 'jsonb')
  LOOP
    EXECUTE format(
      'SELECT count(*) FROM public.%I row_value JOIN demo_original_sensitive_values original ON position(original.original_value IN row_value.%I::text) > 0',
      target.table_name, target.column_name
    )
    INTO match_count;

    IF match_count > 0 THEN
      RAISE EXCEPTION 'Original sensitive value remains in %.% (% matches)',
        target.table_name, target.column_name, match_count;
    END IF;
  END LOOP;
END
$block$;
