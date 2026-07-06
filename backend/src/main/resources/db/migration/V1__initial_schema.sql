CREATE TABLE houses (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    memo TEXT,
    name VARCHAR(255) NOT NULL,
    number INTEGER NOT NULL UNIQUE
);

CREATE TABLE physical_beds (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    display_order INTEGER NOT NULL,
    length_cm INTEGER,
    memo TEXT,
    number INTEGER NOT NULL,
    support_interval_cm INTEGER,
    width_cm INTEGER,
    wire_count INTEGER,
    house_id BIGINT NOT NULL REFERENCES houses(id),
    CONSTRAINT uk_physical_beds_house_number UNIQUE (house_id, number)
);

CREATE TABLE bed_zones (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    is_active BOOLEAN NOT NULL,
    memo TEXT,
    name VARCHAR(255) NOT NULL,
    side VARCHAR(255) NOT NULL,
    sort_order INTEGER NOT NULL,
    zone_type VARCHAR(255) NOT NULL,
    physical_bed_id BIGINT NOT NULL REFERENCES physical_beds(id)
);

CREATE TABLE bed_zone_segments (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    memo TEXT,
    name VARCHAR(100) NOT NULL,
    segment_type VARCHAR(20) NOT NULL,
    sort_order INTEGER NOT NULL,
    bed_zone_id BIGINT NOT NULL REFERENCES bed_zones(id)
);

CREATE TABLE bed_zone_segment_capacities (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    is_allowed BOOLEAN NOT NULL,
    capacity_mode VARCHAR(20) NOT NULL,
    capacity_value INTEGER NOT NULL,
    memo TEXT,
    placement_type VARCHAR(100) NOT NULL,
    pot_size VARCHAR(50),
    bed_zone_segment_id BIGINT NOT NULL REFERENCES bed_zone_segments(id)
);

CREATE TABLE orchid_groups (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    age_year INTEGER,
    genus VARCHAR(255),
    memo TEXT,
    placement_type VARCHAR(255),
    pot_size VARCHAR(255),
    quantity INTEGER NOT NULL,
    sort_order INTEGER NOT NULL,
    status VARCHAR(255) NOT NULL,
    tray_count INTEGER,
    variety_name VARCHAR(255) NOT NULL,
    bed_zone_id BIGINT NOT NULL REFERENCES bed_zones(id),
    split_placement_allowed BOOLEAN
);

CREATE TABLE orchid_group_segment_placements (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    memo TEXT,
    placement_mode VARCHAR(20) NOT NULL,
    quantity INTEGER NOT NULL,
    reorganize_due_date DATE,
    tray_count INTEGER,
    orchid_group_id BIGINT NOT NULL REFERENCES orchid_groups(id),
    bed_zone_segment_id BIGINT NOT NULL REFERENCES bed_zone_segments(id)
);

CREATE TABLE work_types (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    is_active BOOLEAN NOT NULL,
    code VARCHAR(50) NOT NULL UNIQUE,
    is_default BOOLEAN NOT NULL,
    name VARCHAR(50) NOT NULL,
    sort_order INTEGER NOT NULL,
    is_system BOOLEAN NOT NULL,
    template VARCHAR(30) NOT NULL
);

CREATE TABLE work_records (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    dilution_ratio VARCHAR(255),
    from_bed_zone_id BIGINT,
    material_name VARCHAR(255),
    memo TEXT,
    quantity VARCHAR(255),
    target_id BIGINT,
    target_type VARCHAR(255) NOT NULL,
    to_bed_zone_id BIGINT,
    work_date DATE NOT NULL,
    work_type VARCHAR(255) NOT NULL,
    worker VARCHAR(255),
    work_type_id BIGINT REFERENCES work_types(id)
);

CREATE TABLE business_partners (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    address TEXT,
    memo TEXT,
    name VARCHAR(255) NOT NULL,
    owner_name VARCHAR(255),
    phone VARCHAR(255),
    partner_type VARCHAR(255) NOT NULL DEFAULT 'WHOLESALE',
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE auction_shipments (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    memo TEXT,
    shipment_date DATE NOT NULL,
    status VARCHAR(255) NOT NULL,
    auction_house_id BIGINT NOT NULL REFERENCES business_partners(id)
);

CREATE TABLE auction_shipment_lots (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    boxes INTEGER,
    current_status VARCHAR(255) NOT NULL,
    item_name VARCHAR(255) NOT NULL,
    memo TEXT,
    returned_quantity INTEGER NOT NULL,
    shipment_grade VARCHAR(255),
    shipped_quantity INTEGER NOT NULL,
    sold_quantity INTEGER NOT NULL,
    variety_name VARCHAR(255) NOT NULL,
    waiting_quantity INTEGER NOT NULL,
    shipment_id BIGINT NOT NULL REFERENCES auction_shipments(id),
    return_confirmed_date DATE
);

CREATE TABLE auction_attempts (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    attempt_no INTEGER NOT NULL,
    attempt_status VARCHAR(255) NOT NULL,
    auction_date DATE NOT NULL,
    failed_reason VARCHAR(255),
    memo TEXT,
    shipment_lot_id BIGINT NOT NULL REFERENCES auction_shipment_lots(id)
);

CREATE TABLE auction_result_lines (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    amount INTEGER NOT NULL,
    auction_date DATE NOT NULL,
    auction_grade VARCHAR(255),
    inspection_status VARCHAR(255) NOT NULL,
    note TEXT,
    quantity INTEGER NOT NULL,
    unit_price INTEGER NOT NULL,
    auction_attempt_id BIGINT NOT NULL REFERENCES auction_attempts(id)
);

CREATE TABLE auction_lot_status_history (
    id BIGSERIAL PRIMARY KEY,
    changed_at TIMESTAMP NOT NULL,
    memo TEXT,
    new_status VARCHAR(255) NOT NULL,
    previous_status VARCHAR(255),
    reason VARCHAR(255) NOT NULL,
    worker VARCHAR(255),
    shipment_lot_id BIGINT NOT NULL REFERENCES auction_shipment_lots(id)
);

CREATE TABLE sales_slips (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    memo TEXT,
    payment_method VARCHAR(255),
    payment_status VARCHAR(255) NOT NULL,
    sale_date DATE NOT NULL,
    sales_status VARCHAR(255) NOT NULL,
    slip_number VARCHAR(255) NOT NULL UNIQUE,
    total_amount INTEGER NOT NULL,
    partner_id BIGINT NOT NULL REFERENCES business_partners(id),
    sales_type VARCHAR(255),
    auction_shipment_id BIGINT UNIQUE REFERENCES auction_shipments(id),
    expected_payment_date DATE,
    paid_amount BIGINT,
    remaining_amount BIGINT
);

CREATE TABLE sales_slip_items (
    id BIGSERIAL PRIMARY KEY,
    amount INTEGER NOT NULL,
    genus VARCHAR(255),
    item_name VARCHAR(255) NOT NULL,
    memo TEXT,
    quantity INTEGER NOT NULL,
    spec VARCHAR(255),
    unit_price INTEGER NOT NULL,
    orchid_group_id BIGINT REFERENCES orchid_groups(id),
    sales_slip_id BIGINT NOT NULL REFERENCES sales_slips(id),
    auction_shipment_lot_id BIGINT UNIQUE REFERENCES auction_shipment_lots(id)
);

CREATE TABLE partner_settlement_settings (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    allow_prepayment BOOLEAN NOT NULL,
    amount_tolerance BIGINT NOT NULL,
    auto_match_enabled BOOLEAN NOT NULL,
    auto_settle_enabled BOOLEAN NOT NULL,
    credit_auto_apply_enabled BOOLEAN NOT NULL,
    depositor_aliases JSONB,
    memo TEXT,
    payment_day_mode VARCHAR(255) NOT NULL,
    payment_delay_days INTEGER NOT NULL,
    rule_json JSONB,
    settlement_unit VARCHAR(255) NOT NULL,
    partner_id BIGINT NOT NULL UNIQUE REFERENCES business_partners(id)
);

CREATE TABLE auction_settlements (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    auction_date DATE NOT NULL,
    confirmed_at TIMESTAMP,
    confirmed_by VARCHAR(255),
    deduction_amount BIGINT NOT NULL,
    expected_deposit_amount BIGINT NOT NULL,
    expected_payment_date DATE,
    fee_amount BIGINT NOT NULL,
    gross_amount BIGINT NOT NULL,
    memo TEXT,
    paid_amount BIGINT NOT NULL,
    payment_meta_json JSONB,
    remaining_amount BIGINT NOT NULL,
    result_received_at TIMESTAMP,
    status VARCHAR(255) NOT NULL,
    auction_house_id BIGINT NOT NULL REFERENCES business_partners(id),
    CONSTRAINT uk_auction_settlement_house_date UNIQUE (auction_house_id, auction_date)
);

CREATE TABLE auction_settlement_lines (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    amount BIGINT NOT NULL,
    line_meta_json JSONB,
    quantity INTEGER NOT NULL,
    status VARCHAR(255) NOT NULL,
    unit_price INTEGER NOT NULL,
    auction_result_line_id BIGINT NOT NULL UNIQUE REFERENCES auction_result_lines(id),
    auction_shipment_lot_id BIGINT NOT NULL REFERENCES auction_shipment_lots(id),
    settlement_id BIGINT NOT NULL REFERENCES auction_settlements(id)
);

CREATE TABLE partner_payment_events (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    amount BIGINT NOT NULL,
    created_by VARCHAR(255),
    depositor_name VARCHAR(255),
    description TEXT,
    event_date DATE NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    memo TEXT,
    payment_method VARCHAR(255),
    status VARCHAR(255) NOT NULL,
    target_id BIGINT,
    target_type VARCHAR(255),
    unapplied_amount BIGINT NOT NULL,
    parent_event_id BIGINT REFERENCES partner_payment_events(id),
    partner_id BIGINT NOT NULL REFERENCES business_partners(id),
    allocation_payload JSONB,
    balance_snapshot_json JSONB,
    event_time TIME,
    external_uid VARCHAR(255) UNIQUE,
    match_payload JSONB,
    raw_payload JSONB
);

CREATE TABLE partner_balance_summaries (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    credit_balance BIGINT NOT NULL,
    receivable_balance BIGINT NOT NULL,
    unapplied_payment_amount BIGINT NOT NULL,
    partner_id BIGINT NOT NULL UNIQUE REFERENCES business_partners(id),
    summary_json JSONB,
    last_payment_event_id BIGINT UNIQUE REFERENCES partner_payment_events(id)
);

CREATE INDEX idx_auction_shipments_date_house
    ON auction_shipments (shipment_date, auction_house_id);
CREATE INDEX idx_partner_payment_partner_date
    ON partner_payment_events (partner_id, event_date);
CREATE INDEX idx_partner_payment_target
    ON partner_payment_events (target_type, target_id);
CREATE INDEX idx_sales_slips_partner_id
    ON sales_slips (partner_id);
