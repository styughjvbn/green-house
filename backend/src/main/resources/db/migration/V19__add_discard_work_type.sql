INSERT INTO work_types (
    code,
    name,
    template,
    is_active,
    is_system,
    is_default,
    sort_order,
    created_at,
    updated_at
) VALUES (
    'DISCARD',
    '폐기',
    'DISCARD',
    TRUE,
    FALSE,
    TRUE,
    13,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    template = EXCLUDED.template,
    is_active = EXCLUDED.is_active,
    is_system = EXCLUDED.is_system,
    is_default = EXCLUDED.is_default,
    sort_order = EXCLUDED.sort_order,
    updated_at = CURRENT_TIMESTAMP;
