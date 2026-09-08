-- Codes have an independent sequence: Hibernate IDs are pooled and existing codes may have been imported.
-- Keep every existing code. Deleted/rolled-back allocations may leave gaps and are not reused.
CREATE SEQUENCE variety_codes_seq;
CREATE SEQUENCE material_codes_seq;

WITH codes AS (
    SELECT CASE WHEN code ~ '^VAR-[0-9]+$' THEN SUBSTRING(code FROM 5)::numeric END AS value FROM varieties
)
SELECT setval('variety_codes_seq', GREATEST(
    COALESCE((SELECT MAX(id) FROM varieties), 0),
    COALESCE((SELECT MAX(value) FROM codes WHERE value < 9223372036854775807), 0)
)::bigint + 1, false);

WITH codes AS (
    SELECT CASE WHEN code ~ '^MAT-[0-9]+$' THEN SUBSTRING(code FROM 5)::numeric END AS value FROM materials
)
SELECT setval('material_codes_seq', GREATEST(
    COALESCE((SELECT MAX(id) FROM materials), 0),
    COALESCE((SELECT MAX(value) FROM codes WHERE value < 9223372036854775807), 0)
)::bigint + 1, false);
