CREATE TABLE sales_orchid_group_snapshots (
    id BIGSERIAL PRIMARY KEY,
    sales_slip_item_allocation_id BIGINT NOT NULL
        REFERENCES sales_slip_item_allocations(id) ON DELETE CASCADE,
    snapshot_type VARCHAR(20) NOT NULL,
    capture_source VARCHAR(30) NOT NULL,
    captured_at TIMESTAMP NOT NULL,
    orchid_group_id BIGINT NOT NULL,
    variety_id BIGINT,
    variety_name VARCHAR(150) NOT NULL,
    genus VARCHAR(100),
    age_year INTEGER,
    pot_size_code VARCHAR(30),
    pot_size VARCHAR(50),
    quantity INTEGER NOT NULL,
    reserved_quantity INTEGER NOT NULL,
    status VARCHAR(50) NOT NULL,
    allocated_quantity INTEGER NOT NULL,
    house_id BIGINT NOT NULL,
    house_number INTEGER NOT NULL,
    physical_bed_id BIGINT NOT NULL,
    physical_bed_number INTEGER NOT NULL,
    bed_zone_id BIGINT NOT NULL,
    bed_zone_name VARCHAR(100) NOT NULL,
    start_position NUMERIC(6, 2),
    end_position NUMERIC(6, 2),
    CONSTRAINT uk_sales_orchid_snapshot_allocation_type
        UNIQUE (sales_slip_item_allocation_id, snapshot_type),
    CONSTRAINT ck_sales_orchid_snapshot_type
        CHECK (snapshot_type IN ('CREATION', 'OUTBOUND')),
    CONSTRAINT ck_sales_orchid_snapshot_source
        CHECK (capture_source IN ('LIVE', 'MIGRATED_CURRENT_STATE')),
    CONSTRAINT ck_sales_orchid_snapshot_quantities
        CHECK (quantity >= 0 AND reserved_quantity >= 0 AND allocated_quantity > 0)
);

CREATE INDEX idx_sales_orchid_snapshots_analysis
    ON sales_orchid_group_snapshots(snapshot_type, captured_at, variety_id);

-- Existing allocations did not capture historical values. Preserve their current
-- state explicitly as migrated data so analytics can distinguish it from live snapshots.
INSERT INTO sales_orchid_group_snapshots (
    sales_slip_item_allocation_id,
    snapshot_type,
    capture_source,
    captured_at,
    orchid_group_id,
    variety_id,
    variety_name,
    genus,
    age_year,
    pot_size_code,
    pot_size,
    quantity,
    reserved_quantity,
    status,
    allocated_quantity,
    house_id,
    house_number,
    physical_bed_id,
    physical_bed_number,
    bed_zone_id,
    bed_zone_name,
    start_position,
    end_position
)
SELECT
    allocation.id,
    'CREATION',
    'MIGRATED_CURRENT_STATE',
    allocation.created_at,
    orchid.id,
    orchid.variety_id,
    orchid.variety_name,
    orchid.genus,
    orchid.age_year,
    orchid.pot_size_code,
    orchid.pot_size,
    orchid.quantity,
    orchid.reserved_quantity,
    orchid.status,
    allocation.allocated_quantity,
    house.id,
    house.number,
    bed.id,
    bed.number,
    zone.id,
    zone.name,
    orchid.start_position,
    orchid.end_position
FROM sales_slip_item_allocations allocation
JOIN orchid_groups orchid ON orchid.id = allocation.orchid_group_id
JOIN bed_zones zone ON zone.id = orchid.bed_zone_id
JOIN physical_beds bed ON bed.id = zone.physical_bed_id
JOIN houses house ON house.id = bed.house_id;

INSERT INTO sales_orchid_group_snapshots (
    sales_slip_item_allocation_id,
    snapshot_type,
    capture_source,
    captured_at,
    orchid_group_id,
    variety_id,
    variety_name,
    genus,
    age_year,
    pot_size_code,
    pot_size,
    quantity,
    reserved_quantity,
    status,
    allocated_quantity,
    house_id,
    house_number,
    physical_bed_id,
    physical_bed_number,
    bed_zone_id,
    bed_zone_name,
    start_position,
    end_position
)
SELECT
    allocation.id,
    'OUTBOUND',
    'MIGRATED_CURRENT_STATE',
    outbound.captured_at,
    orchid.id,
    orchid.variety_id,
    orchid.variety_name,
    orchid.genus,
    orchid.age_year,
    orchid.pot_size_code,
    orchid.pot_size,
    orchid.quantity,
    orchid.reserved_quantity,
    orchid.status,
    allocation.allocated_quantity,
    house.id,
    house.number,
    bed.id,
    bed.number,
    zone.id,
    zone.name,
    orchid.start_position,
    orchid.end_position
FROM sales_slip_item_allocations allocation
JOIN sales_slip_items item ON item.id = allocation.sales_slip_item_id
JOIN orchid_groups orchid ON orchid.id = allocation.orchid_group_id
JOIN bed_zones zone ON zone.id = orchid.bed_zone_id
JOIN physical_beds bed ON bed.id = zone.physical_bed_id
JOIN houses house ON house.id = bed.house_id
JOIN LATERAL (
    SELECT MIN(movement.created_at) AS captured_at
    FROM sales_inventory_movements movement
    WHERE movement.sales_slip_item_id = item.id
      AND movement.orchid_group_id = allocation.orchid_group_id
      AND movement.change_type = 'SALES_OUTBOUND'
) outbound ON outbound.captured_at IS NOT NULL;
