INSERT INTO work_types (
    code, name, template, is_active, is_system, is_default, sort_order, created_at, updated_at
) VALUES
    ('DIVIDE', '분주', 'REPOT', TRUE, TRUE, TRUE, 11, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('MERGE', '합식', 'REPOT', TRUE, TRUE, TRUE, 12, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    template = EXCLUDED.template,
    is_active = EXCLUDED.is_active,
    is_system = EXCLUDED.is_system,
    is_default = EXCLUDED.is_default,
    sort_order = EXCLUDED.sort_order,
    updated_at = CURRENT_TIMESTAMP;
