\set ON_ERROR_STOP on
\pset pager off

-- Read-only profiling for an OrchidGroup complete state-chain rehearsal.
-- Run only against a restored backup or another read-only operational copy.

\echo 'SOURCE_COUNTS'
SELECT source, row_count
FROM (VALUES
    ('ORCHID_GROUP', (SELECT count(*) FROM orchid_groups)),
    ('INBOUND_RECORD', (SELECT count(*) FROM inbound_records)),
    ('WORK_OPERATION', (SELECT count(*) FROM work_operations)),
    ('WORK_EFFECT', (SELECT count(*) FROM work_applied_effects)),
    ('WORK_EFFECT_GROUP_LINK', (SELECT count(*) FROM work_effect_orchid_groups)),
    ('LINEAGE', (SELECT count(*) FROM orchid_group_lineage)),
    ('SALES_SLIP', (SELECT count(*) FROM sales_slips)),
    ('SALES_SLIP_ITEM', (SELECT count(*) FROM sales_slip_items)),
    ('SALES_ALLOCATION', (SELECT count(*) FROM sales_slip_item_allocations)),
    ('SALES_INVENTORY_MOVEMENT', (SELECT count(*) FROM sales_inventory_movements)),
    ('SALES_ORCHID_SNAPSHOT', (SELECT count(*) FROM sales_orchid_group_snapshots)),
    ('ORCHID_AUDIT_EVENT', (
        SELECT count(*) FROM audit_events WHERE entity_type = 'ORCHID_GROUP'
    ))
) AS counts(source, row_count)
ORDER BY source;

\echo 'STATE_CHANGING_WORK_EFFECTS'
SELECT
    handler_code,
    count(*) AS effects,
    count(*) FILTER (WHERE applied_at IS NOT NULL) AS timestamp_known,
    count(*) FILTER (WHERE command_details IS NOT NULL) AS command_known,
    count(*) FILTER (WHERE result_details IS NOT NULL) AS result_known,
    count(*) FILTER (WHERE EXISTS (
        SELECT 1
        FROM work_effect_orchid_groups link
        WHERE link.work_applied_effect_id = effect.id
    )) AS links_known
FROM work_applied_effects effect
WHERE handler_code IN ('DISCARD', 'DIVIDE', 'MOVE', 'MOVEMENT', 'POTTING', 'REPOT')
GROUP BY handler_code
ORDER BY handler_code;

\echo 'WORK_EFFECT_SOURCE_IDENTITIES'
SELECT
    count(*) AS effects,
    count(DISTINCT (work_operation_id, effect_key)) AS unique_engine_source_identities,
    count(*) FILTER (WHERE effect_key IS NULL OR btrim(effect_key) = '') AS missing_effect_keys,
    count(*) FILTER (WHERE applied_at IS NULL) AS missing_business_times
FROM work_applied_effects;

SELECT count(*) AS duplicate_engine_source_identities
FROM (
    SELECT work_operation_id, effect_key
    FROM work_applied_effects
    GROUP BY work_operation_id, effect_key
    HAVING count(*) > 1
) duplicates;

\echo 'EXPECTED_WORK_EFFECT_LINK_MISMATCHES'
WITH expected(effect_id, group_id, relation_type) AS (
    SELECT id, (result_details ->> 'orchidGroupId')::bigint, 'RESULT'
    FROM work_applied_effects
    WHERE handler_code IN ('DISCARD', 'MOVE')

    UNION ALL

    SELECT effect.id, (source_row ->> 'sourceOrchidGroupId')::bigint, 'SOURCE'
    FROM work_applied_effects effect
    CROSS JOIN LATERAL jsonb_array_elements(effect.command_details -> 'sources') source_row
    WHERE effect.handler_code IN ('DIVIDE', 'MOVEMENT', 'REPOT')

    UNION ALL

    SELECT effect.id, (result_row ->> 'orchidGroupId')::bigint, 'RESULT'
    FROM work_applied_effects effect
    CROSS JOIN LATERAL jsonb_array_elements(effect.result_details -> 'results') result_row
    WHERE effect.handler_code IN ('DIVIDE', 'MOVEMENT', 'REPOT')

    UNION ALL

    SELECT effect.id, (group_id #>> '{}')::bigint, 'RESULT'
    FROM work_applied_effects effect
    CROSS JOIN LATERAL jsonb_array_elements(
        effect.result_details -> 'createdOrchidGroupIds'
    ) group_id
    WHERE effect.handler_code = 'POTTING'
), actual AS (
    SELECT work_applied_effect_id AS effect_id, orchid_group_id AS group_id, relation_type
    FROM work_effect_orchid_groups
)
SELECT 'EXPECTED_MISSING_ACTUAL' AS issue, count(*) AS rows
FROM expected
LEFT JOIN actual USING (effect_id, group_id, relation_type)
WHERE actual.effect_id IS NULL

UNION ALL

SELECT 'UNEXPECTED_ACTUAL', count(*)
FROM actual
LEFT JOIN expected USING (effect_id, group_id, relation_type)
JOIN work_applied_effects effect ON effect.id = actual.effect_id
WHERE expected.effect_id IS NULL
  AND effect.handler_code <> 'RECORD_ONLY';

\echo 'LINEAGE_MATCHABILITY'
SELECT
    count(*) AS lineage_rows,
    count(*) FILTER (WHERE matching_effects = 1) AS uniquely_matchable,
    count(*) FILTER (WHERE matching_effects = 0) AS no_match,
    count(*) FILTER (WHERE matching_effects > 1) AS ambiguous
FROM (
    SELECT lineage.id, (
        SELECT count(DISTINCT effect.id)
        FROM work_applied_effects effect
        JOIN work_effect_orchid_groups source_link
          ON source_link.work_applied_effect_id = effect.id
         AND source_link.relation_type = 'SOURCE'
         AND source_link.orchid_group_id = lineage.source_orchid_group_id
        JOIN work_effect_orchid_groups result_link
          ON result_link.work_applied_effect_id = effect.id
         AND result_link.relation_type = 'RESULT'
         AND result_link.orchid_group_id = lineage.result_orchid_group_id
        WHERE effect.work_operation_id = lineage.work_operation_id
    ) AS matching_effects
    FROM orchid_group_lineage lineage
) matches;

\echo 'CREATION_PROVENANCE'
WITH provenance AS (
    SELECT entity_id AS group_id, 'AUDIT_CREATE' AS source
    FROM audit_events
    WHERE entity_type = 'ORCHID_GROUP' AND action = 'CREATED'

    UNION ALL

    SELECT (result_row ->> 'orchidGroupId')::bigint, 'STRUCTURE_RESULT'
    FROM work_applied_effects effect
    CROSS JOIN LATERAL jsonb_array_elements(effect.result_details -> 'results') result_row
    WHERE effect.handler_code IN ('DIVIDE', 'MOVEMENT', 'REPOT')

    UNION ALL

    SELECT (group_id #>> '{}')::bigint, 'POTTING_RESULT'
    FROM work_applied_effects effect
    CROSS JOIN LATERAL jsonb_array_elements(
        effect.result_details -> 'createdOrchidGroupIds'
    ) group_id
    WHERE effect.handler_code = 'POTTING'
), distinct_provenance AS (
    SELECT DISTINCT group_id FROM provenance
)
SELECT
    (SELECT count(*) FROM orchid_groups) AS total_groups,
    count(*) AS groups_with_creation_provenance,
    (SELECT count(*) FROM orchid_groups) - count(*) AS groups_requiring_synthetic_origin
FROM distinct_provenance;

\echo 'STATE_EVENT_GROUP_COVERAGE'
WITH affected AS (
    SELECT (result_details ->> 'orchidGroupId')::bigint AS group_id
    FROM work_applied_effects
    WHERE handler_code IN ('DISCARD', 'MOVE')

    UNION

    SELECT source_orchid_group_id FROM orchid_group_lineage

    UNION

    SELECT result_orchid_group_id FROM orchid_group_lineage

    UNION

    SELECT link.orchid_group_id
    FROM work_effect_orchid_groups link
    JOIN work_applied_effects effect ON effect.id = link.work_applied_effect_id
    WHERE effect.handler_code IN ('MOVEMENT', 'POTTING')

    UNION

    SELECT entity_id FROM audit_events WHERE entity_type = 'ORCHID_GROUP'
)
SELECT
    count(*) AS groups_with_any_state_event,
    (SELECT count(*) FROM orchid_groups) - count(*) AS groups_with_origin_only
FROM affected;

\echo 'QUANTITY_REPLAY_FOR_KNOWN_CREATIONS'
WITH creations AS (
    SELECT
        entity_id AS group_id,
        (after_data ->> 'quantity')::integer AS initial_quantity,
        'AUDIT_CREATE:' || id AS source
    FROM audit_events
    WHERE entity_type = 'ORCHID_GROUP' AND action = 'CREATED'

    UNION ALL

    SELECT
        (result_row ->> 'orchidGroupId')::bigint,
        (result_row ->> 'quantity')::integer,
        'WORK_RESULT:' || effect.id
    FROM work_applied_effects effect
    CROSS JOIN LATERAL jsonb_array_elements(effect.result_details -> 'results') result_row
    WHERE effect.handler_code IN ('DIVIDE', 'MOVEMENT', 'REPOT')

    UNION ALL

    SELECT
        (id_row.value #>> '{}')::bigint,
        (command_row.value ->> 'quantity')::integer,
        'POTTING_RESULT:' || effect.id
    FROM work_applied_effects effect
    CROSS JOIN LATERAL jsonb_array_elements(
        effect.result_details -> 'createdOrchidGroupIds'
    ) WITH ORDINALITY id_row(value, ordinality)
    JOIN LATERAL jsonb_array_elements(
        effect.command_details -> 'results'
    ) WITH ORDINALITY command_row(value, ordinality)
      ON command_row.ordinality = id_row.ordinality
    WHERE effect.handler_code = 'POTTING'
), deltas AS (
    SELECT
        (result_details ->> 'orchidGroupId')::bigint AS group_id,
        -(result_details ->> 'discardedQuantity')::integer AS delta
    FROM work_applied_effects
    WHERE handler_code = 'DISCARD'

    UNION ALL

    SELECT
        (source_row ->> 'sourceOrchidGroupId')::bigint,
        -(source_row ->> 'inputQuantity')::integer
    FROM work_applied_effects effect
    CROSS JOIN LATERAL jsonb_array_elements(effect.command_details -> 'sources') source_row
    WHERE effect.handler_code IN ('DIVIDE', 'MOVEMENT', 'REPOT')

    UNION ALL

    SELECT
        entity_id,
        (after_data ->> 'quantity')::integer - (before_data ->> 'quantity')::integer
    FROM audit_events
    WHERE entity_type = 'ORCHID_GROUP' AND action = 'UPDATED'
), replay AS (
    SELECT
        creation.group_id,
        count(*) AS creation_records,
        min(creation.initial_quantity) AS initial_quantity,
        coalesce(sum(delta.delta), 0) AS later_delta
    FROM creations creation
    LEFT JOIN deltas delta ON delta.group_id = creation.group_id
    GROUP BY creation.group_id
)
SELECT
    count(*) AS known_created_groups,
    count(*) FILTER (WHERE creation_records <> 1) AS duplicate_creation_records,
    count(*) FILTER (
        WHERE initial_quantity + later_delta = orchid_group.quantity
    ) AS quantity_matches,
    count(*) FILTER (
        WHERE initial_quantity + later_delta <> orchid_group.quantity
    ) AS quantity_mismatches,
    coalesce(sum(abs(
        (initial_quantity + later_delta) - orchid_group.quantity
    )) FILTER (
        WHERE initial_quantity + later_delta <> orchid_group.quantity
    ), 0) AS absolute_difference
FROM replay
JOIN orchid_groups orchid_group ON orchid_group.id = replay.group_id;

\echo 'SYNTHETIC_ORIGIN_QUANTITY_FEASIBILITY'
WITH creations AS (
    SELECT entity_id AS group_id
    FROM audit_events
    WHERE entity_type = 'ORCHID_GROUP' AND action = 'CREATED'

    UNION

    SELECT (result_row ->> 'orchidGroupId')::bigint
    FROM work_applied_effects effect
    CROSS JOIN LATERAL jsonb_array_elements(effect.result_details -> 'results') result_row
    WHERE effect.handler_code IN ('DIVIDE', 'MOVEMENT', 'REPOT')

    UNION

    SELECT (group_id #>> '{}')::bigint
    FROM work_applied_effects effect
    CROSS JOIN LATERAL jsonb_array_elements(
        effect.result_details -> 'createdOrchidGroupIds'
    ) group_id
    WHERE effect.handler_code = 'POTTING'
), deltas AS (
    SELECT
        (result_details ->> 'orchidGroupId')::bigint AS group_id,
        -(result_details ->> 'discardedQuantity')::integer AS delta
    FROM work_applied_effects
    WHERE handler_code = 'DISCARD'

    UNION ALL

    SELECT
        (source_row ->> 'sourceOrchidGroupId')::bigint,
        -(source_row ->> 'inputQuantity')::integer
    FROM work_applied_effects effect
    CROSS JOIN LATERAL jsonb_array_elements(effect.command_details -> 'sources') source_row
    WHERE effect.handler_code IN ('DIVIDE', 'MOVEMENT', 'REPOT')

    UNION ALL

    SELECT
        entity_id,
        (after_data ->> 'quantity')::integer - (before_data ->> 'quantity')::integer
    FROM audit_events
    WHERE entity_type = 'ORCHID_GROUP' AND action = 'UPDATED'
), origins AS (
    SELECT
        orchid_group.id,
        orchid_group.quantity - coalesce(sum(delta.delta), 0) AS derived_origin_quantity
    FROM orchid_groups orchid_group
    LEFT JOIN deltas delta ON delta.group_id = orchid_group.id
    WHERE NOT EXISTS (
        SELECT 1 FROM creations creation WHERE creation.group_id = orchid_group.id
    )
    GROUP BY orchid_group.id, orchid_group.quantity
)
SELECT
    count(*) AS synthetic_origin_groups,
    count(*) FILTER (WHERE derived_origin_quantity >= 0) AS nonnegative_origins,
    count(*) FILTER (WHERE derived_origin_quantity < 0) AS invalid_origins,
    min(derived_origin_quantity) AS minimum_origin_quantity,
    max(derived_origin_quantity) AS maximum_origin_quantity
FROM origins;

\echo 'QUANTITY_REPLAY_MISMATCH_DETAILS'
WITH creations AS (
    SELECT
        entity_id AS group_id,
        (after_data ->> 'quantity')::integer AS initial_quantity,
        'AUDIT_CREATE:' || id AS source
    FROM audit_events
    WHERE entity_type = 'ORCHID_GROUP' AND action = 'CREATED'

    UNION ALL

    SELECT
        (result_row ->> 'orchidGroupId')::bigint,
        (result_row ->> 'quantity')::integer,
        'WORK_RESULT:' || effect.id
    FROM work_applied_effects effect
    CROSS JOIN LATERAL jsonb_array_elements(effect.result_details -> 'results') result_row
    WHERE effect.handler_code IN ('DIVIDE', 'MOVEMENT', 'REPOT')

    UNION ALL

    SELECT
        (id_row.value #>> '{}')::bigint,
        (command_row.value ->> 'quantity')::integer,
        'POTTING_RESULT:' || effect.id
    FROM work_applied_effects effect
    CROSS JOIN LATERAL jsonb_array_elements(
        effect.result_details -> 'createdOrchidGroupIds'
    ) WITH ORDINALITY id_row(value, ordinality)
    JOIN LATERAL jsonb_array_elements(
        effect.command_details -> 'results'
    ) WITH ORDINALITY command_row(value, ordinality)
      ON command_row.ordinality = id_row.ordinality
    WHERE effect.handler_code = 'POTTING'
), deltas AS (
    SELECT
        (result_details ->> 'orchidGroupId')::bigint AS group_id,
        -(result_details ->> 'discardedQuantity')::integer AS delta
    FROM work_applied_effects
    WHERE handler_code = 'DISCARD'

    UNION ALL

    SELECT
        (source_row ->> 'sourceOrchidGroupId')::bigint,
        -(source_row ->> 'inputQuantity')::integer
    FROM work_applied_effects effect
    CROSS JOIN LATERAL jsonb_array_elements(effect.command_details -> 'sources') source_row
    WHERE effect.handler_code IN ('DIVIDE', 'MOVEMENT', 'REPOT')

    UNION ALL

    SELECT
        entity_id,
        (after_data ->> 'quantity')::integer - (before_data ->> 'quantity')::integer
    FROM audit_events
    WHERE entity_type = 'ORCHID_GROUP' AND action = 'UPDATED'
), replay AS (
    SELECT
        creation.group_id,
        min(creation.source) AS creation_source,
        min(creation.initial_quantity) AS initial_quantity,
        coalesce(sum(delta.delta), 0) AS later_delta
    FROM creations creation
    LEFT JOIN deltas delta ON delta.group_id = creation.group_id
    GROUP BY creation.group_id
)
SELECT
    replay.group_id,
    replay.creation_source,
    replay.initial_quantity,
    replay.later_delta,
    replay.initial_quantity + replay.later_delta AS replay_quantity,
    orchid_group.quantity AS current_quantity,
    orchid_group.updated_at AS last_group_update
FROM replay
JOIN orchid_groups orchid_group ON orchid_group.id = replay.group_id
WHERE replay.initial_quantity + replay.later_delta <> orchid_group.quantity
ORDER BY replay.group_id;

\echo 'SOURCE_REFERENCE_INTEGRITY'
SELECT 'WORK_EFFECT_MISSING_GROUP' AS issue, count(*) AS rows
FROM work_effect_orchid_groups link
LEFT JOIN orchid_groups orchid_group ON orchid_group.id = link.orchid_group_id
WHERE orchid_group.id IS NULL

UNION ALL

SELECT 'LINEAGE_MISSING_SOURCE', count(*)
FROM orchid_group_lineage lineage
LEFT JOIN orchid_groups orchid_group ON orchid_group.id = lineage.source_orchid_group_id
WHERE orchid_group.id IS NULL

UNION ALL

SELECT 'LINEAGE_MISSING_RESULT', count(*)
FROM orchid_group_lineage lineage
LEFT JOIN orchid_groups orchid_group ON orchid_group.id = lineage.result_orchid_group_id
WHERE orchid_group.id IS NULL

UNION ALL

SELECT 'INBOUND_MISSING_GROUP', count(*)
FROM inbound_records inbound
LEFT JOIN orchid_groups orchid_group ON orchid_group.id = inbound.created_orchid_group_id
WHERE inbound.created_orchid_group_id IS NOT NULL
  AND orchid_group.id IS NULL

UNION ALL

SELECT 'AUDIT_MISSING_GROUP', count(*)
FROM audit_events audit
LEFT JOIN orchid_groups orchid_group ON orchid_group.id = audit.entity_id
WHERE audit.entity_type = 'ORCHID_GROUP'
  AND orchid_group.id IS NULL;

\echo 'LEGACY_SALES_REFERENCE_DATA'
SELECT
    (SELECT count(*) FROM sales_slips) AS legacy_reference_slips,
    (SELECT count(*) FROM sales_slip_items) AS legacy_reference_items,
    (SELECT count(*) FROM sales_slip_item_allocations) AS group_allocations,
    (SELECT count(*) FROM sales_inventory_movements) AS inventory_movements,
    (SELECT count(*) FROM sales_orchid_group_snapshots) AS group_snapshots,
    (
        SELECT count(*)
        FROM sales_slips slip
        WHERE EXISTS (
            SELECT 1
            FROM sales_slip_items item
            JOIN sales_slip_item_allocations allocation
              ON allocation.sales_slip_item_id = item.id
            WHERE item.sales_slip_id = slip.id
        ) OR EXISTS (
            SELECT 1
            FROM sales_inventory_movements movement
            WHERE movement.sales_slip_id = slip.id
        )
    ) AS slips_with_group_attribution;
