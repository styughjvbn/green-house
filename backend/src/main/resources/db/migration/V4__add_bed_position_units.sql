ALTER TABLE physical_beds
    ADD COLUMN IF NOT EXISTS position_unit_count NUMERIC(6, 2),
    ADD COLUMN IF NOT EXISTS position_unit_label VARCHAR(50);

ALTER TABLE bed_zone_segments
    ADD COLUMN IF NOT EXISTS start_position NUMERIC(6, 2),
    ADD COLUMN IF NOT EXISTS end_position NUMERIC(6, 2);

ALTER TABLE bed_zone_segment_capacities
    ADD COLUMN IF NOT EXISTS unit_span NUMERIC(6, 2);

UPDATE physical_beds
SET position_unit_count = CASE
        WHEN number IN (1, 2) THEN 24.00
        ELSE 28.00
    END,
    position_unit_label = '치'
WHERE position_unit_count IS NULL;

WITH segment_ranges AS (
    SELECT
        segments.id,
        beds.position_unit_count,
        ROUND(((segments.sort_order - 1) * beds.position_unit_count / 5.0)::numeric, 2) AS start_position,
        ROUND((segments.sort_order * beds.position_unit_count / 5.0)::numeric, 2) AS end_position
    FROM bed_zone_segments segments
    JOIN bed_zones zones ON zones.id = segments.bed_zone_id
    JOIN physical_beds beds ON beds.id = zones.physical_bed_id
)
UPDATE bed_zone_segments segments
SET start_position = ranges.start_position,
    end_position = ranges.end_position
FROM segment_ranges ranges
WHERE segments.id = ranges.id
  AND (segments.start_position IS NULL OR segments.end_position IS NULL);

UPDATE bed_zone_segment_capacities
SET unit_span = 6.00
WHERE unit_span IS NULL;

ALTER TABLE bed_zone_segments
    ALTER COLUMN start_position SET NOT NULL,
    ALTER COLUMN end_position SET NOT NULL;

ALTER TABLE bed_zone_segment_capacities
    ALTER COLUMN unit_span SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_bed_zone_segment_capacities_rule
    ON bed_zone_segment_capacities (
        bed_zone_segment_id,
        placement_type,
        COALESCE(pot_size, ''),
        capacity_mode
    );
