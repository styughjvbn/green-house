CREATE TABLE work_operations (
    id BIGSERIAL PRIMARY KEY,
    work_type_id BIGINT NOT NULL REFERENCES work_types(id),
    title VARCHAR(150) NOT NULL,
    status VARCHAR(30) NOT NULL,
    planned_start_date DATE NOT NULL,
    planned_end_date DATE,
    actual_start_at TIMESTAMP,
    actual_end_at TIMESTAMP,
    source_scope_type VARCHAR(30) NOT NULL,
    source_scope_id BIGINT,
    source_condition_snapshot JSONB,
    target_snapshot_at TIMESTAMP NOT NULL,
    details JSONB,
    worker VARCHAR(100),
    memo TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE work_operation_targets (
    id BIGSERIAL PRIMARY KEY,
    work_operation_id BIGINT NOT NULL REFERENCES work_operations(id),
    orchid_group_id BIGINT NOT NULL REFERENCES orchid_groups(id),
    inclusion_source VARCHAR(30) NOT NULL,
    source_reference_id BIGINT,
    included_at TIMESTAMP NOT NULL,
    excluded_at TIMESTAMP,
    exclusion_reason TEXT,
    variety_id_snapshot BIGINT,
    variety_name_snapshot VARCHAR(150) NOT NULL,
    age_year_snapshot INTEGER,
    pot_size_code_snapshot VARCHAR(50),
    pot_size_snapshot VARCHAR(50),
    quantity_snapshot INTEGER NOT NULL,
    location_snapshot JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_work_operation_target UNIQUE (work_operation_id, orchid_group_id)
);

CREATE TABLE work_target_executions (
    id BIGSERIAL PRIMARY KEY,
    work_operation_target_id BIGINT NOT NULL UNIQUE REFERENCES work_operation_targets(id),
    status VARCHAR(30) NOT NULL,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    worker VARCHAR(100),
    result_details JSONB,
    effect_applied_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_work_operation_targets_orchid_group
    ON work_operation_targets(orchid_group_id, work_operation_id);
CREATE INDEX idx_work_operations_status_start
    ON work_operations(status, planned_start_date);
