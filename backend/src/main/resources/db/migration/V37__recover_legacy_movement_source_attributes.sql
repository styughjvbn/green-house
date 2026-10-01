-- V36 intentionally required identical non-placement snapshots. The legacy
-- movement transformer, however, created replacement groups from result-form
-- fields and could drop source-only metadata (notably memo and inbound origin)
-- even for a full 1:1 move. Current MOVE semantics retain all source attributes.
-- Recover only untouched replacement groups (revision 1) whose core inherited
-- attributes prove that they are the same OrchidGroup.

CREATE TEMP TABLE v37_identity_pairs ON COMMIT DROP AS
SELECT
    operation.id AS work_operation_id,
    mutation.id AS mutation_id,
    effect.id AS effect_id,
    source_entry.id AS source_entry_id,
    result_entry.id AS result_entry_id,
    source_entry.orchid_group_id AS source_group_id,
    result_entry.orchid_group_id AS result_group_id
FROM work_operations operation
JOIN work_types work_type
  ON work_type.id = operation.work_type_id
 AND work_type.code = 'MOVEMENT'
JOIN work_applied_effects effect
  ON effect.work_operation_id = operation.id
 AND effect.mutation_id IS NOT NULL
 AND effect.handler_code = 'MOVEMENT'
JOIN orchid_group_mutations mutation
  ON mutation.id = effect.mutation_id
 AND mutation.mutation_type = 'TRANSFORM'
CROSS JOIN LATERAL jsonb_array_elements(CASE
        WHEN jsonb_typeof(effect.command_details -> 'results') = 'array'
            THEN effect.command_details -> 'results'
        ELSE '[]'::jsonb
    END)
    WITH ORDINALITY AS result_command(value, position)
JOIN LATERAL jsonb_array_elements(CASE
        WHEN jsonb_typeof(effect.result_details -> 'results') = 'array'
            THEN effect.result_details -> 'results'
        ELSE '[]'::jsonb
    END)
    WITH ORDINALITY AS result_row(value, position)
  ON result_row.position = result_command.position
JOIN LATERAL jsonb_array_elements(CASE
        WHEN jsonb_typeof(effect.command_details -> 'sources') = 'array'
            THEN effect.command_details -> 'sources'
        ELSE '[]'::jsonb
    END) AS source_row(value)
  ON (source_row.value ->> 'sourceOrchidGroupId')::BIGINT
     = (result_command.value ->> 'attributeSourceOrchidGroupId')::BIGINT
JOIN orchid_group_mutation_entries source_entry
  ON source_entry.mutation_id = mutation.id
 AND source_entry.orchid_group_id = (source_row.value ->> 'sourceOrchidGroupId')::BIGINT
 AND source_entry.entry_kind = 'CHANGE'
 AND source_entry.role = 'SOURCE'
JOIN orchid_group_mutation_entries result_entry
  ON result_entry.mutation_id = mutation.id
 AND result_entry.orchid_group_id = (result_row.value ->> 'orchidGroupId')::BIGINT
 AND result_entry.entry_kind = 'CREATE'
 AND result_entry.role = 'RESULT'
JOIN orchid_groups result_group
  ON result_group.id = result_entry.orchid_group_id
 AND result_group.state_revision = 1
WHERE operation.status = 'COMPLETED'
  AND result_command.value ->> 'attributeSourceOrchidGroupId' IS NOT NULL
  AND COALESCE(result_command.value ->> 'purpose', 'NORMAL') = 'NORMAL'
  AND COALESCE(source_row.value -> 'releasedStartPosition', 'null'::jsonb) = 'null'::jsonb
  AND COALESCE(source_row.value -> 'releasedEndPosition', 'null'::jsonb) = 'null'::jsonb
  AND (source_row.value ->> 'inputQuantity')::INTEGER
      = (source_entry.before_state ->> 'quantity')::INTEGER
  AND (result_command.value ->> 'quantity')::INTEGER
      = (source_row.value ->> 'inputQuantity')::INTEGER
  AND (result_row.value ->> 'quantity')::INTEGER
      = (source_row.value ->> 'inputQuantity')::INTEGER
  AND (source_entry.after_state ->> 'quantity')::INTEGER = 0
  AND jsonb_build_object(
          'quantity', source_entry.before_state -> 'quantity',
          'reservedQuantity', source_entry.before_state -> 'reservedQuantity',
          'status', source_entry.before_state -> 'status',
          'varietyId', source_entry.before_state -> 'varietyId',
          'genus', source_entry.before_state -> 'genus',
          'varietyName', source_entry.before_state -> 'varietyName',
          'ageYear', source_entry.before_state -> 'ageYear',
          'potSizeCode', source_entry.before_state -> 'potSizeCode'
      ) = jsonb_build_object(
          'quantity', result_entry.after_state -> 'quantity',
          'reservedQuantity', result_entry.after_state -> 'reservedQuantity',
          'status', result_entry.after_state -> 'status',
          'varietyId', result_entry.after_state -> 'varietyId',
          'genus', result_entry.after_state -> 'genus',
          'varietyName', result_entry.after_state -> 'varietyName',
          'ageYear', result_entry.after_state -> 'ageYear',
          'potSizeCode', result_entry.after_state -> 'potSizeCode'
      )
  AND NOT EXISTS (
      SELECT 1
      FROM orchid_group_mutation_entries later_entry
      WHERE later_entry.orchid_group_id = result_entry.orchid_group_id
        AND later_entry.id <> result_entry.id
  );

DELETE FROM v37_identity_pairs pair
WHERE pair.mutation_id NOT IN (
    SELECT candidate.mutation_id
    FROM v37_identity_pairs candidate
    JOIN work_applied_effects effect ON effect.id = candidate.effect_id
    GROUP BY candidate.mutation_id, effect.id, effect.command_details, effect.result_details
    HAVING count(*) = jsonb_array_length(effect.command_details -> 'sources')
       AND count(*) = jsonb_array_length(effect.command_details -> 'results')
       AND count(*) = jsonb_array_length(effect.result_details -> 'results')
       AND count(DISTINCT candidate.source_group_id) = count(*)
       AND count(DISTINCT candidate.result_group_id) = count(*)
       AND count(*) = (
           SELECT count(*) FROM orchid_group_mutation_entries entry
           WHERE entry.mutation_id = candidate.mutation_id AND entry.role = 'SOURCE'
       )
       AND count(*) = (
           SELECT count(*) FROM orchid_group_mutation_entries entry
           WHERE entry.mutation_id = candidate.mutation_id AND entry.role = 'RESULT'
       )
);

ALTER TABLE orchid_groups DISABLE TRIGGER USER;

DO $$
DECLARE
    selected_mutation_id BIGINT;
    mapping RECORD;
BEGIN
    LOOP
        SELECT pair.mutation_id
          INTO selected_mutation_id
          FROM v37_identity_pairs pair
          JOIN orchid_group_mutations mutation ON mutation.id = pair.mutation_id
         ORDER BY mutation.occurred_at, pair.mutation_id
         LIMIT 1;
        EXIT WHEN selected_mutation_id IS NULL;

        IF EXISTS (
            SELECT 1
            FROM v37_identity_pairs pair
            JOIN work_operation_targets result_target
              ON result_target.orchid_group_id = pair.result_group_id
            JOIN work_operation_targets source_target
              ON source_target.work_operation_id = result_target.work_operation_id
             AND source_target.orchid_group_id = pair.source_group_id
            WHERE pair.mutation_id = selected_mutation_id
        ) THEN
            RAISE EXCEPTION 'V37 found conflicting work targets for mutation %', selected_mutation_id;
        END IF;

        IF EXISTS (
            SELECT 1
            FROM v37_identity_pairs pair
            JOIN work_effect_orchid_groups result_link
              ON result_link.orchid_group_id = pair.result_group_id
            JOIN work_effect_orchid_groups source_link
              ON source_link.work_applied_effect_id = result_link.work_applied_effect_id
             AND source_link.orchid_group_id = pair.source_group_id
             AND source_link.relation_type = result_link.relation_type
            WHERE pair.mutation_id = selected_mutation_id
        ) THEN
            RAISE EXCEPTION 'V37 found conflicting work effect links for mutation %', selected_mutation_id;
        END IF;

        INSERT INTO orchid_group_identity_migrations (
            work_operation_id, mutation_id, preserved_orchid_group_id,
            removed_orchid_group_id, removed_group_snapshot
        )
        SELECT pair.work_operation_id, pair.mutation_id, pair.source_group_id,
               pair.result_group_id, to_jsonb(result_group)
        FROM v37_identity_pairs pair
        JOIN orchid_groups result_group ON result_group.id = pair.result_group_id
        WHERE pair.mutation_id = selected_mutation_id;

        -- Keep every original source attribute and apply only the recorded
        -- destination placement from the legacy replacement snapshot.
        UPDATE orchid_group_mutation_entries source_entry
        SET role = 'AFFECTED',
            after_state = source_entry.before_state || jsonb_build_object(
                'bedZoneId', result_entry.after_state -> 'bedZoneId',
                'sortOrder', result_entry.after_state -> 'sortOrder',
                'startPosition', result_entry.after_state -> 'startPosition',
                'endPosition', result_entry.after_state -> 'endPosition'
            )
        FROM v37_identity_pairs pair
        JOIN orchid_group_mutation_entries result_entry ON result_entry.id = pair.result_entry_id
        WHERE pair.mutation_id = selected_mutation_id
          AND source_entry.id = pair.source_entry_id;

        DELETE FROM orchid_group_mutation_entries result_entry
        USING v37_identity_pairs pair
        WHERE pair.mutation_id = selected_mutation_id
          AND result_entry.id = pair.result_entry_id;

        UPDATE orchid_group_mutations SET mutation_type = 'MOVE'
        WHERE id = selected_mutation_id;

        DELETE FROM orchid_group_lineage lineage
        USING v37_identity_pairs pair
        WHERE pair.mutation_id = selected_mutation_id
          AND lineage.work_operation_id = pair.work_operation_id
          AND lineage.source_orchid_group_id = pair.source_group_id
          AND lineage.result_orchid_group_id = pair.result_group_id;

        FOR mapping IN
            SELECT * FROM v37_identity_pairs
            WHERE mutation_id = selected_mutation_id
            ORDER BY source_group_id
        LOOP
            DELETE FROM orchid_group_collection_members result_member
            USING orchid_group_collection_members source_member
            WHERE result_member.orchid_group_id = mapping.result_group_id
              AND result_member.removed_at IS NULL
              AND source_member.collection_id = result_member.collection_id
              AND source_member.orchid_group_id = mapping.source_group_id
              AND source_member.removed_at IS NULL;

            UPDATE orchid_group_collection_members SET orchid_group_id = mapping.source_group_id
            WHERE orchid_group_id = mapping.result_group_id;
            UPDATE work_operation_targets SET orchid_group_id = mapping.source_group_id
            WHERE orchid_group_id = mapping.result_group_id;
            UPDATE work_effect_orchid_groups SET orchid_group_id = mapping.source_group_id
            WHERE orchid_group_id = mapping.result_group_id;
            DELETE FROM orchid_group_lineage
            WHERE (source_orchid_group_id = mapping.result_group_id
                    AND result_orchid_group_id = mapping.source_group_id)
               OR (source_orchid_group_id = mapping.source_group_id
                    AND result_orchid_group_id = mapping.result_group_id);
            UPDATE orchid_group_lineage SET source_orchid_group_id = mapping.source_group_id
            WHERE source_orchid_group_id = mapping.result_group_id;
            UPDATE orchid_group_lineage SET result_orchid_group_id = mapping.source_group_id
            WHERE result_orchid_group_id = mapping.result_group_id;
            UPDATE sales_inventory_movements SET orchid_group_id = mapping.source_group_id
            WHERE orchid_group_id = mapping.result_group_id;
            UPDATE sales_slip_item_allocations SET orchid_group_id = mapping.source_group_id
            WHERE orchid_group_id = mapping.result_group_id;
            UPDATE sales_orchid_group_snapshots SET orchid_group_id = mapping.source_group_id
            WHERE orchid_group_id = mapping.result_group_id;

            UPDATE orchid_groups source_group
            SET updated_at = result_group.updated_at,
                bed_zone_id = result_group.bed_zone_id,
                quantity = (source_entry.after_state ->> 'quantity')::INTEGER,
                reserved_quantity = (source_entry.after_state ->> 'reservedQuantity')::INTEGER,
                status = source_entry.after_state ->> 'status',
                sort_order = result_group.sort_order,
                start_position = result_group.start_position,
                end_position = result_group.end_position,
                version = greatest(source_group.version, result_group.version) + 1,
                state_revision = source_entry.state_revision_after
            FROM orchid_groups result_group,
                 orchid_group_mutation_entries source_entry
            WHERE source_group.id = mapping.source_group_id
              AND result_group.id = mapping.result_group_id
              AND source_entry.id = mapping.source_entry_id;

            DELETE FROM orchid_groups WHERE id = mapping.result_group_id;
        END LOOP;

        DELETE FROM v37_identity_pairs WHERE mutation_id = selected_mutation_id;
    END LOOP;
END $$;

ALTER TABLE orchid_groups ENABLE TRIGGER USER;

CREATE OR REPLACE FUNCTION v37_rewrite_orchid_group_ids(value JSONB, parent_key TEXT DEFAULT NULL)
RETURNS JSONB
LANGUAGE plpgsql
STABLE
AS $$
DECLARE
    rewritten JSONB;
BEGIN
    IF value IS NULL THEN RETURN NULL; END IF;
    IF jsonb_typeof(value) = 'object' THEN
        SELECT jsonb_object_agg(
                   CASE
                       WHEN parent_key = 'sourceInputQuantities' AND key ~ '^[0-9]+$'
                       THEN COALESCE((
                           SELECT migration.preserved_orchid_group_id::TEXT
                           FROM orchid_group_identity_migrations migration
                           WHERE migration.removed_orchid_group_id = key::BIGINT
                       ), key)
                       ELSE key
                   END,
                   v37_rewrite_orchid_group_ids(child, key)
               )
          INTO rewritten
          FROM jsonb_each(value) item(key, child);
        RETURN COALESCE(rewritten, '{}'::jsonb);
    END IF;
    IF jsonb_typeof(value) = 'array' THEN
        SELECT jsonb_agg(v37_rewrite_orchid_group_ids(child, parent_key) ORDER BY position)
          INTO rewritten
          FROM jsonb_array_elements(value) WITH ORDINALITY item(child, position);
        RETURN COALESCE(rewritten, '[]'::jsonb);
    END IF;
    IF jsonb_typeof(value) = 'number'
       AND parent_key IN (
           'orchidGroupId', 'sourceOrchidGroupId', 'attributeSourceOrchidGroupId',
           'resultOrchidGroupId', 'orchidGroupIds', 'sourceOrchidGroupIds',
           'resultOrchidGroupIds', 'createdOrchidGroupIds'
       ) THEN
        RETURN COALESCE((
            SELECT to_jsonb(migration.preserved_orchid_group_id)
            FROM orchid_group_identity_migrations migration
            WHERE migration.removed_orchid_group_id = (value #>> '{}')::BIGINT
        ), value);
    END IF;
    RETURN value;
END $$;

UPDATE work_applied_effects
SET command_details = v37_rewrite_orchid_group_ids(command_details),
    result_details = v37_rewrite_orchid_group_ids(result_details);

UPDATE work_target_executions
SET result_details = v37_rewrite_orchid_group_ids(result_details)
WHERE result_details IS NOT NULL;

UPDATE work_operations
SET details = v37_rewrite_orchid_group_ids(details)
WHERE details IS NOT NULL;

UPDATE work_applied_effects effect
SET result_details = jsonb_set(effect.result_details, '{identityPreserved}', 'true'::jsonb, TRUE)
FROM orchid_group_identity_migrations migration
WHERE migration.work_operation_id = effect.work_operation_id
  AND migration.mutation_id = effect.mutation_id;

UPDATE work_target_executions execution
SET result_details = jsonb_set(execution.result_details, '{identityPreserved}', 'true'::jsonb, TRUE)
FROM work_operation_targets target
JOIN orchid_group_identity_migrations migration
  ON migration.work_operation_id = target.work_operation_id
WHERE execution.work_operation_target_id = target.id;

DROP FUNCTION v37_rewrite_orchid_group_ids(JSONB, TEXT);
