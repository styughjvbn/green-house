ALTER TABLE orchid_groups
    ADD COLUMN IF NOT EXISTS start_position NUMERIC(6, 2),
    ADD COLUMN IF NOT EXISTS end_position NUMERIC(6, 2);

CREATE TABLE IF NOT EXISTS bed_zone_capacities (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    bed_zone_id BIGINT NOT NULL REFERENCES bed_zones(id),
    placement_type VARCHAR(100) NOT NULL,
    pot_size VARCHAR(50),
    capacity_mode VARCHAR(20) NOT NULL,
    unit_span NUMERIC(6, 2) NOT NULL,
    capacity_value INTEGER NOT NULL,
    is_allowed BOOLEAN NOT NULL,
    memo TEXT
);

INSERT INTO bed_zone_capacities (
    created_at,
    updated_at,
    bed_zone_id,
    placement_type,
    pot_size,
    capacity_mode,
    unit_span,
    capacity_value,
    is_allowed,
    memo
)
SELECT DISTINCT
    capacities.created_at,
    capacities.updated_at,
    segments.bed_zone_id,
    capacities.placement_type,
    capacities.pot_size,
    capacities.capacity_mode,
    capacities.unit_span,
    capacities.capacity_value,
    capacities.is_allowed,
    capacities.memo
FROM bed_zone_segment_capacities capacities
JOIN bed_zone_segments segments ON segments.id = capacities.bed_zone_segment_id
WHERE EXISTS (
    SELECT 1
    FROM information_schema.tables
    WHERE table_name = 'bed_zone_segment_capacities'
)
ON CONFLICT DO NOTHING;

CREATE UNIQUE INDEX IF NOT EXISTS uk_bed_zone_capacities_rule
    ON bed_zone_capacities (
        bed_zone_id,
        placement_type,
        COALESCE(pot_size, ''),
        capacity_mode
    );

DROP TABLE IF EXISTS orchid_group_segment_placements;
DROP TABLE IF EXISTS bed_zone_segment_capacities;
DROP TABLE IF EXISTS bed_zone_segments;
