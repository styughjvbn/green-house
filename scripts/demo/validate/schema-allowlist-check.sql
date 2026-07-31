CREATE TEMP TABLE demo_expected_schema (
  table_name text PRIMARY KEY,
  schema_fingerprint text NOT NULL
) ON COMMIT DROP;

INSERT INTO demo_expected_schema(table_name, schema_fingerprint)
VALUES :allowlist_values;

CREATE TEMP TABLE demo_actual_schema ON COMMIT DROP AS
SELECT table_name,
       md5(string_agg(
         column_name || ':' || data_type || ':' || is_nullable,
         ',' ORDER BY ordinal_position
       )) AS schema_fingerprint
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name <> 'flyway_schema_history'
GROUP BY table_name;

DO $block$
DECLARE
  mismatch text;
BEGIN
  SELECT string_agg(
           coalesce(expected.table_name, actual.table_name) ||
           CASE
             WHEN expected.table_name IS NULL THEN ' (not allowlisted)'
             WHEN actual.table_name IS NULL THEN ' (missing)'
             ELSE ' (column schema changed)'
           END,
           ', ' ORDER BY coalesce(expected.table_name, actual.table_name)
         )
  INTO mismatch
  FROM demo_expected_schema expected
  FULL JOIN demo_actual_schema actual USING (table_name)
  WHERE expected.table_name IS NULL
     OR actual.table_name IS NULL
     OR expected.schema_fingerprint <> actual.schema_fingerprint;

  IF mismatch IS NOT NULL THEN
    RAISE EXCEPTION 'Demo export schema allowlist mismatch: %', mismatch;
  END IF;

  IF to_regclass('public.flyway_schema_history') IS NULL THEN
    RAISE EXCEPTION 'flyway_schema_history is required';
  END IF;
END
$block$;
