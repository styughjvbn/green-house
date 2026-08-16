INSERT INTO work_effect_orchid_groups (
    work_applied_effect_id,
    orchid_group_id,
    relation_type,
    created_at
)
SELECT
    effect.id,
    (source_row ->> 'sourceOrchidGroupId')::BIGINT,
    'SOURCE',
    effect.applied_at
FROM work_applied_effects effect
CROSS JOIN LATERAL jsonb_array_elements(
    COALESCE(effect.command_details -> 'sources', '[]'::jsonb)
) source_row
WHERE effect.handler_code IN ('MOVEMENT', 'MERGE')
  AND source_row ? 'sourceOrchidGroupId'
ON CONFLICT ON CONSTRAINT uk_work_effect_orchid_group DO NOTHING;
