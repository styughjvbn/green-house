ALTER TABLE work_operations
    ADD COLUMN voided_at TIMESTAMP,
    ADD COLUMN void_reason TEXT,
    ADD COLUMN void_request_key VARCHAR(100),
    ADD COLUMN void_mutation_id BIGINT REFERENCES orchid_group_mutations(id);

ALTER TABLE work_operations
    ADD CONSTRAINT uk_work_operations_void_request_key UNIQUE (void_request_key);

ALTER TABLE work_operations DROP CONSTRAINT ck_work_operations_status;
ALTER TABLE work_operations
    ADD CONSTRAINT ck_work_operations_status CHECK (
        status IN ('PLANNED', 'IN_PROGRESS', 'PAUSED', 'COMPLETED', 'CANCELED', 'CORRECTED', 'VOIDED')
    );

INSERT INTO work_types (
    code, name, template, is_active, is_system, is_default, sort_order, created_at, updated_at
) VALUES (
    'RECONCILIATION', '현장 상태 동기화', 'RECONCILIATION', TRUE, TRUE, TRUE, 14,
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
)
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    template = EXCLUDED.template,
    is_active = EXCLUDED.is_active,
    is_system = EXCLUDED.is_system,
    is_default = EXCLUDED.is_default,
    sort_order = EXCLUDED.sort_order,
    updated_at = CURRENT_TIMESTAMP;
