ALTER TABLE orchid_groups
    ADD COLUMN pot_size_code VARCHAR(30) NOT NULL DEFAULT 'UNSPECIFIED';

ALTER TABLE orchid_groups
    ADD CONSTRAINT ck_orchid_groups_pot_size_code CHECK (pot_size_code IN (
        'UNSPECIFIED', 'UNMAPPED', 'POT_2', 'POT_2_5', 'POT_3',
        'POT_3_5', 'POT_4', 'POT_4_5', 'POT_5', 'POT_6', 'HANGING', 'ETC'
    ));

CREATE INDEX idx_orchid_groups_derived_group
    ON orchid_groups(variety_id, age_year, pot_size_code, status);

CREATE TABLE orchid_group_collections (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    purpose VARCHAR(200),
    status VARCHAR(20) NOT NULL,
    created_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE orchid_group_collection_members (
    id BIGSERIAL PRIMARY KEY,
    collection_id BIGINT NOT NULL REFERENCES orchid_group_collections(id),
    orchid_group_id BIGINT NOT NULL REFERENCES orchid_groups(id),
    joined_at TIMESTAMP NOT NULL,
    removed_at TIMESTAMP,
    created_by VARCHAR(100)
);

CREATE UNIQUE INDEX uk_orchid_collection_active_member
    ON orchid_group_collection_members(collection_id, orchid_group_id)
    WHERE removed_at IS NULL;

CREATE INDEX idx_orchid_collection_members_orchid_group
    ON orchid_group_collection_members(orchid_group_id, collection_id)
    WHERE removed_at IS NULL;

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
    request_key VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_work_operations_request_key UNIQUE (request_key),
    CONSTRAINT ck_work_operations_status CHECK (
        status IN ('PLANNED', 'IN_PROGRESS', 'PAUSED', 'COMPLETED', 'CANCELED', 'CORRECTED')
    )
);

CREATE INDEX idx_work_operations_status_start
    ON work_operations(status, planned_start_date);

CREATE TABLE work_operation_targets (
    id BIGSERIAL PRIMARY KEY,
    work_operation_id BIGINT NOT NULL REFERENCES work_operations(id),
    orchid_group_id BIGINT REFERENCES orchid_groups(id),
    target_reference_type VARCHAR(30) NOT NULL,
    inbound_record_id BIGINT REFERENCES inbound_records(id),
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
    CONSTRAINT uk_work_operation_target UNIQUE (work_operation_id, orchid_group_id),
    CONSTRAINT ck_work_operation_target_reference CHECK (
        (target_reference_type = 'ORCHID_GROUP' AND orchid_group_id IS NOT NULL AND inbound_record_id IS NULL)
        OR
        (target_reference_type = 'INBOUND_RECORD' AND orchid_group_id IS NULL AND inbound_record_id IS NOT NULL)
    )
);

CREATE INDEX idx_work_operation_targets_orchid_group
    ON work_operation_targets(orchid_group_id, work_operation_id);

CREATE INDEX idx_work_operation_targets_inbound_record
    ON work_operation_targets(inbound_record_id);

CREATE TABLE work_target_executions (
    id BIGSERIAL PRIMARY KEY,
    work_operation_target_id BIGINT NOT NULL UNIQUE REFERENCES work_operation_targets(id),
    status VARCHAR(30) NOT NULL,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    worker VARCHAR(100),
    result_details JSONB,
    effect_applied_at TIMESTAMP,
    processed_quantity INTEGER NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT ck_work_target_executions_processed_quantity CHECK (processed_quantity >= 0)
);

CREATE TABLE work_applied_effects (
    id BIGSERIAL PRIMARY KEY,
    work_operation_id BIGINT NOT NULL REFERENCES work_operations(id),
    work_operation_target_id BIGINT REFERENCES work_operation_targets(id),
    effect_key VARCHAR(100) NOT NULL,
    effect_kind VARCHAR(30) NOT NULL,
    handler_code VARCHAR(50) NOT NULL,
    applied_at TIMESTAMP NOT NULL,
    canceled_at TIMESTAMP,
    worker VARCHAR(100),
    command_details JSONB,
    result_details JSONB,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_work_applied_effect_operation_key_kind
        UNIQUE (work_operation_id, effect_key, effect_kind)
);

CREATE INDEX idx_work_applied_effect_target
    ON work_applied_effects(work_operation_target_id, applied_at);

CREATE TABLE work_effect_orchid_groups (
    id BIGSERIAL PRIMARY KEY,
    work_applied_effect_id BIGINT NOT NULL REFERENCES work_applied_effects(id),
    orchid_group_id BIGINT NOT NULL REFERENCES orchid_groups(id),
    relation_type VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_work_effect_orchid_group
        UNIQUE (work_applied_effect_id, orchid_group_id, relation_type)
);

CREATE INDEX idx_work_effect_orchid_group_group
    ON work_effect_orchid_groups(orchid_group_id, work_applied_effect_id);

CREATE TABLE orchid_group_lineage (
    id BIGSERIAL PRIMARY KEY,
    source_orchid_group_id BIGINT NOT NULL REFERENCES orchid_groups(id),
    result_orchid_group_id BIGINT NOT NULL REFERENCES orchid_groups(id),
    relation_type VARCHAR(30) NOT NULL,
    work_operation_id BIGINT NOT NULL REFERENCES work_operations(id),
    source_quantity INTEGER NOT NULL CHECK (source_quantity > 0),
    result_quantity INTEGER NOT NULL CHECK (result_quantity > 0),
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT ck_orchid_group_lineage_distinct_groups
        CHECK (source_orchid_group_id <> result_orchid_group_id),
    CONSTRAINT uk_orchid_group_lineage_operation_groups_relation
        UNIQUE (work_operation_id, source_orchid_group_id, result_orchid_group_id, relation_type)
);

CREATE INDEX idx_orchid_group_lineage_source
    ON orchid_group_lineage(source_orchid_group_id, created_at);

CREATE INDEX idx_orchid_group_lineage_result
    ON orchid_group_lineage(result_orchid_group_id, created_at);

CREATE TABLE work_operation_corrections (
    id BIGSERIAL PRIMARY KEY,
    original_work_operation_id BIGINT NOT NULL REFERENCES work_operations(id),
    correction_work_operation_id BIGINT NOT NULL REFERENCES work_operations(id),
    reason TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT ck_work_operation_correction_distinct_operations
        CHECK (original_work_operation_id <> correction_work_operation_id),
    CONSTRAINT uk_work_operation_correction_operation
        UNIQUE (correction_work_operation_id)
);

CREATE INDEX idx_work_operation_corrections_original
    ON work_operation_corrections(original_work_operation_id, created_at);
