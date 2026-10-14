\set ON_ERROR_STOP on

-- Explicit operator action: one known CHECK per transaction, without data repair.
-- Run with psql -XAtq -v constraint=... after inspecting the read-only report.
\if :{?constraint}
\else
    DO $$ BEGIN RAISE EXCEPTION 'constraint parameter is required'; END $$;
\endif

BEGIN;
SET LOCAL search_path = pg_catalog, public;
SET LOCAL row_security = off;
SET LOCAL lock_timeout = '3s';
SET LOCAL statement_timeout = '5min';

\ir domain-constraint-catalog.sql

SELECT EXISTS (
    SELECT 1 FROM jsonb_array_elements(:'domain_constraint_catalog'::jsonb) entry
    WHERE entry->>'constraint' = :'constraint'
      AND (entry->>'present')::boolean AND entry->>'kind' = 'c'
) AS known_domain_check
\gset

\if :known_domain_check
    SELECT format('ALTER TABLE %I.%I VALIDATE CONSTRAINT %I;',
        entry->>'schema', entry->>'table', entry->>'constraint')
    FROM jsonb_array_elements(:'domain_constraint_catalog'::jsonb) entry
    WHERE entry->>'constraint' = :'constraint' AND NOT (entry->>'validated')::boolean
    \gexec
\else
    DO $$ BEGIN RAISE EXCEPTION 'Unknown, missing, or non-CHECK domain constraint'; END $$;
\endif

COMMIT;

-- Refresh after commit; a successful report describes the committed catalog state.
\ir domain-constraint-catalog.sql
SELECT entry
FROM jsonb_array_elements(:'domain_constraint_catalog'::jsonb) entry
WHERE entry->>'constraint' = :'constraint';
