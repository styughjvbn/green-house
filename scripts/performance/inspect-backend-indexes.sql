-- Read-only inventory for a real database; estimates and usage depend on ANALYZE/statistics resets.
-- Run with a read-only account: psql "$AUDIT_DATABASE_URL" -v ON_ERROR_STOP=1 -f this-file.sql
BEGIN READ ONLY;
SET LOCAL statement_timeout = '30s';

SELECT version(), current_database(), current_setting('work_mem') AS work_mem;
SELECT schemaname, relname, n_live_tup, n_dead_tup, last_analyze, last_autoanalyze
FROM pg_stat_user_tables WHERE schemaname = 'public' ORDER BY relname;

-- INCLUDE columns and partial/expression indexes do not count as full FK leading-column coverage.
SELECT f.conrelid::regclass AS child_table, f.conname AS foreign_key,
       f.confrelid::regclass AS parent_table,
       ARRAY(SELECT a.attname FROM unnest(f.conkey) WITH ORDINALITY k(attnum, pos)
             JOIN pg_attribute a ON a.attrelid = f.conrelid AND a.attnum = k.attnum
             ORDER BY k.pos) AS child_columns,
       EXISTS (
         SELECT 1 FROM pg_index i
         JOIN pg_class index_relation ON index_relation.oid = i.indexrelid
         JOIN pg_am am ON am.oid = index_relation.relam
         WHERE i.indrelid = f.conrelid AND i.indisvalid AND i.indisready
           AND i.indpred IS NULL AND am.amname = 'btree'
           AND i.indnkeyatts >= cardinality(f.conkey)
           AND (i.indkey::smallint[])[0:cardinality(f.conkey)-1] @> f.conkey
       ) AS has_full_btree_prefix
FROM pg_constraint f JOIN pg_namespace n ON n.oid = f.connamespace
WHERE f.contype = 'f' AND n.nspname = 'public'
ORDER BY f.conrelid::regclass::text, f.conname;

SELECT s.relname AS table_name, s.indexrelname AS index_name,
       i.indisvalid, i.indisready, s.idx_scan, s.idx_tup_read, s.idx_tup_fetch,
       pg_relation_size(s.indexrelid) AS index_bytes,
       pg_get_indexdef(s.indexrelid) AS definition
FROM pg_stat_user_indexes s JOIN pg_index i ON i.indexrelid = s.indexrelid
WHERE s.schemaname = 'public' ORDER BY s.relname, s.indexrelname;
COMMIT;
