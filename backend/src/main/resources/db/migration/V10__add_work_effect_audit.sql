CREATE TABLE work_applied_effects (
    id BIGSERIAL PRIMARY KEY,
    work_operation_id BIGINT NOT NULL REFERENCES work_operations(id),
    work_operation_target_id BIGINT NOT NULL REFERENCES work_operation_targets(id),
    effect_kind VARCHAR(30) NOT NULL,
    handler_code VARCHAR(50) NOT NULL,
    applied_at TIMESTAMP NOT NULL,
    worker VARCHAR(100),
    command_details JSONB,
    result_details JSONB,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_work_applied_effect_operation_target_kind
        UNIQUE (work_operation_id, work_operation_target_id, effect_kind)
);

CREATE INDEX idx_work_applied_effect_target
    ON work_applied_effects(work_operation_target_id, applied_at);
