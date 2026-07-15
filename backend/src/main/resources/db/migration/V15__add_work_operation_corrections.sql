INSERT INTO work_types (
    code, name, template, is_active, is_system, is_default, sort_order, created_at, updated_at
) VALUES (
    'CORRECTION', '구조 변경 보정', 'CORRECTION', TRUE, TRUE, TRUE,
    (SELECT COALESCE(MAX(sort_order), 0) + 1 FROM work_types),
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
)
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    template = EXCLUDED.template,
    is_active = EXCLUDED.is_active,
    is_system = EXCLUDED.is_system,
    updated_at = CURRENT_TIMESTAMP;

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
