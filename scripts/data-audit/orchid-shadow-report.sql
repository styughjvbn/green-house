\set ON_ERROR_STOP on

BEGIN TRANSACTION READ ONLY;

SELECT status,
       COUNT(*) AS comparison_count,
       MIN(recorded_at) AS first_recorded_at,
       MAX(recorded_at) AS last_recorded_at
FROM orchid_group_shadow_comparisons
GROUP BY status
ORDER BY status;

SELECT source_domain,
       source_type,
       source_reference_id,
       source_operation_key,
       mutation_type,
       status,
       engine_error,
       mismatches,
       recorded_at
FROM orchid_group_shadow_comparisons
WHERE status <> 'MATCHED'
ORDER BY recorded_at, id;

COMMIT;
