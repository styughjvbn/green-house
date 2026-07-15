ALTER TABLE work_operations
    ADD COLUMN request_key VARCHAR(100);

ALTER TABLE work_operations
    ADD CONSTRAINT uk_work_operations_request_key UNIQUE (request_key);

ALTER TABLE work_applied_effects
    ALTER COLUMN work_operation_target_id DROP NOT NULL;

ALTER TABLE work_applied_effects
    ADD COLUMN effect_key VARCHAR(100);

UPDATE work_applied_effects
SET effect_key = 'TARGET:' || work_operation_target_id::text;

ALTER TABLE work_applied_effects
    ALTER COLUMN effect_key SET NOT NULL;

ALTER TABLE work_applied_effects
    DROP CONSTRAINT uk_work_applied_effect_operation_target_kind;

ALTER TABLE work_applied_effects
    ADD CONSTRAINT uk_work_applied_effect_operation_key_kind
        UNIQUE (work_operation_id, effect_key, effect_kind);

CREATE TABLE work_effect_orchid_groups (
    id BIGSERIAL PRIMARY KEY,
    work_applied_effect_id BIGINT NOT NULL REFERENCES work_applied_effects(id),
    orchid_group_id BIGINT NOT NULL REFERENCES orchid_groups(id),
    relation_type VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_work_effect_orchid_group UNIQUE (work_applied_effect_id, orchid_group_id, relation_type)
);

CREATE INDEX idx_work_effect_orchid_group_group
    ON work_effect_orchid_groups(orchid_group_id, work_applied_effect_id);

INSERT INTO work_types (
    code, name, template, is_active, is_system, is_default, sort_order, created_at, updated_at
)
SELECT
    'MULTI_CREATE', '난 묶음 다중 생성', 'MULTI_CREATE', TRUE, TRUE, TRUE,
    COALESCE(MAX(sort_order), 0) + 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM work_types
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    template = EXCLUDED.template,
    is_active = TRUE,
    is_system = TRUE,
    updated_at = CURRENT_TIMESTAMP;
