\set ON_ERROR_STOP on

-- Run with psql -XAtq. Reports JSON lines; exit 0 means the inspection completed,
-- not that every constraint is validated. See docs/07-deployment.md.
BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY;
SET LOCAL search_path = pg_catalog, public;
SET LOCAL time zone 'UTC';
SET LOCAL row_security = off;
SET LOCAL lock_timeout = '3s';
SET LOCAL statement_timeout = '5min';

SELECT jsonb_build_object(
    'report', 'SNAPSHOT',
    'database', current_database(),
    'user', current_user,
    'transactionStartedAt', transaction_timestamp(),
    'snapshot', pg_current_snapshot()::text,
    'readOnly', current_setting('transaction_read_only'),
    'isolation', current_setting('transaction_isolation')
);

\ir domain-constraint-catalog.sql

SELECT CASE
    WHEN NOT (entry->>'present')::boolean OR entry->>'kind' <> 'c' THEN
        format('SELECT %L::jsonb;', (entry || jsonb_build_object(
            'status', CASE WHEN NOT (entry->>'present')::boolean THEN 'MISSING' ELSE 'NOT_CHECK' END,
            'violationCount', NULL,
            'sampleIds', '[]'::jsonb
        ))::text)
    ELSE format($query$
        WITH violating_ids AS MATERIALIZED (
            SELECT id FROM %I.%I WHERE (%s) IS FALSE
        )
        SELECT %L::jsonb || jsonb_build_object(
            'status', CASE WHEN count(*) > 0 THEN 'VIOLATIONS'
                          WHEN %L::boolean THEN 'VALIDATED' ELSE 'UNVALIDATED' END,
            'violationCount', count(*),
            'sampleIds', COALESCE((
                SELECT jsonb_agg(id ORDER BY id)
                FROM (SELECT id FROM violating_ids ORDER BY id LIMIT 50) sample
            ), '[]'::jsonb)
        ) FROM violating_ids;
    $query$, entry->>'schema', entry->>'table', entry->>'expression',
        entry::text, entry->>'validated')
END
FROM jsonb_array_elements(:'domain_constraint_catalog'::jsonb) entry
ORDER BY entry->>'table', entry->>'constraint'
\gexec

COMMIT;
