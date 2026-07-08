CREATE TABLE IF NOT EXISTS varieties (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    code VARCHAR(50) NOT NULL UNIQUE,
    genus VARCHAR(100) NOT NULL,
    name VARCHAR(150) NOT NULL,
    alias VARCHAR(150),
    default_pot_size VARCHAR(50),
    description TEXT,
    sale_enabled BOOLEAN NOT NULL,
    is_active BOOLEAN NOT NULL,
    memo TEXT
);

ALTER TABLE orchid_groups
    ADD COLUMN IF NOT EXISTS variety_id BIGINT,
    ADD COLUMN IF NOT EXISTS inbound_record_id BIGINT;

CREATE TABLE IF NOT EXISTS inbound_records (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    inbound_date DATE NOT NULL,
    inbound_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    bottle_count INTEGER,
    estimated_quantity INTEGER,
    actual_quantity INTEGER,
    temp_location VARCHAR(255),
    potting_due_date DATE,
    potting_date DATE,
    pot_size VARCHAR(50),
    age_year INTEGER,
    growth_stage VARCHAR(100),
    placement_type VARCHAR(100),
    tray_count INTEGER,
    worker VARCHAR(50),
    memo TEXT,
    variety_id BIGINT NOT NULL REFERENCES varieties(id),
    bed_zone_id BIGINT REFERENCES bed_zones(id),
    created_orchid_group_id BIGINT REFERENCES orchid_groups(id)
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_schema = 'public'
          AND table_name = 'orchid_groups'
          AND constraint_name = 'fk_orchid_groups_variety'
    ) THEN
        ALTER TABLE orchid_groups
            ADD CONSTRAINT fk_orchid_groups_variety
            FOREIGN KEY (variety_id) REFERENCES varieties(id);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_schema = 'public'
          AND table_name = 'orchid_groups'
          AND constraint_name = 'fk_orchid_groups_inbound_record'
    ) THEN
        ALTER TABLE orchid_groups
            ADD CONSTRAINT fk_orchid_groups_inbound_record
            FOREIGN KEY (inbound_record_id) REFERENCES inbound_records(id);
    END IF;
END $$;
