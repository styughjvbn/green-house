ALTER TABLE varieties
    ADD COLUMN color character varying(7);

ALTER TABLE varieties
    ADD CONSTRAINT ck_varieties_color_hex
    CHECK (color IS NULL OR color ~ '^#[0-9A-Fa-f]{6}$');

WITH ranked_varieties AS (
    SELECT id, row_number() OVER (ORDER BY id) AS row_number, count(*) OVER () AS total_count
    FROM varieties
)
UPDATE varieties variety
SET color = '#' || lpad(to_hex(((ranked_varieties.row_number * 16777215) / (ranked_varieties.total_count + 1))::integer), 6, '0')
FROM ranked_varieties
WHERE variety.id = ranked_varieties.id;
