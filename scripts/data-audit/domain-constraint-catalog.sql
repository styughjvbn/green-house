-- Shared target inventory. Invoked with psql \ir inside the caller's transaction.
-- Read the installed CHECK expression instead of copying domain predicates here.
WITH expected(table_name, constraint_name) AS (
    VALUES
        ('orchid_groups', 'ck_orchid_groups_quantity_nonnegative'),
        ('orchid_groups', 'ck_orchid_groups_reserved_quantity'),
        ('orchid_groups', 'ck_orchid_groups_state_revision'),
        ('sales_slips', 'ck_sales_slips_sales_status'),
        ('sales_slip_items', 'ck_sales_slip_items_amount')
)
SELECT jsonb_agg(jsonb_build_object(
    'schema', 'public',
    'table', expected.table_name,
    'constraint', expected.constraint_name,
    'present', installed.oid IS NOT NULL,
    'kind', installed.contype,
    'validated', installed.convalidated,
    'expression', pg_get_expr(installed.conbin, installed.conrelid),
    'definition', pg_get_constraintdef(installed.oid),
    'status', CASE WHEN installed.oid IS NULL THEN 'MISSING'
                   WHEN installed.contype <> 'c' THEN 'NOT_CHECK'
                   WHEN installed.convalidated THEN 'VALIDATED' ELSE 'UNVALIDATED' END
) ORDER BY expected.table_name, expected.constraint_name)::text AS domain_constraint_catalog
FROM expected
LEFT JOIN pg_namespace namespace ON namespace.nspname = 'public'
LEFT JOIN pg_class relation
    ON relation.relnamespace = namespace.oid AND relation.relname = expected.table_name
LEFT JOIN pg_constraint installed
    ON installed.conrelid = relation.oid AND installed.conname = expected.constraint_name
\gset
