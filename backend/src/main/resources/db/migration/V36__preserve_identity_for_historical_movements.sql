-- Before identity-preserving movement was introduced, even a full 1:1 move
-- closed every source OrchidGroup and created a replacement result group.
-- Consolidate only movements whose stored command proves a complete 1:1
-- mapping and whose snapshots differ only by placement. Repoint the complete
-- downstream state chain to the original ID and retain an explicit audit map
-- for every removed replacement ID.

CREATE TABLE orchid_group_identity_migrations (
    id BIGSERIAL PRIMARY KEY,
    work_operation_id BIGINT NOT NULL REFERENCES work_operations(id),
    mutation_id BIGINT NOT NULL REFERENCES orchid_group_mutations(id),
    preserved_orchid_group_id BIGINT NOT NULL REFERENCES orchid_groups(id),
    removed_orchid_group_id BIGINT NOT NULL,
    removed_group_snapshot JSONB NOT NULL,
    migrated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_orchid_group_identity_migration_removed UNIQUE (removed_orchid_group_id),
    CONSTRAINT ck_orchid_group_identity_migration_distinct
        CHECK (preserved_orchid_group_id <> removed_orchid_group_id)
);

CREATE INDEX idx_orchid_group_identity_migrations_preserved
    ON orchid_group_identity_migrations(preserved_orchid_group_id, migrated_at);

CREATE TEMP TABLE v36_identity_pairs ON COMMIT DROP AS
SELECT
    operation.id AS work_operation_id,
    mutation.id AS mutation_id,
    effect.id AS effect_id,
    source_entry.id AS source_entry_id,
    result_entry.id AS result_entry_id,
    source_entry.orchid_group_id AS source_group_id,
    result_entry.orchid_group_id AS result_group_id,
    source_row.value AS source_command,
    result_command.value AS result_command,
    result_row.value AS result_details
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
WHERE operation.status = 'COMPLETED'
  AND jsonb_typeof(effect.command_details -> 'sources') = 'array'
  AND jsonb_typeof(effect.command_details -> 'results') = 'array'
  AND jsonb_typeof(effect.result_details -> 'results') = 'array'
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
  AND source_entry.before_state
        - 'bedZoneId' - 'sortOrder' - 'startPosition' - 'endPosition'
      = result_entry.after_state
        - 'bedZoneId' - 'sortOrder' - 'startPosition' - 'endPosition';

-- A mutation is eligible only when every stored source and result participates
-- exactly once. This rejects partial matches, missing legacy mappings and N:M
-- transformations without relying on operation IDs.
DELETE FROM v36_identity_pairs pair
WHERE pair.mutation_id NOT IN (
    SELECT candidate.mutation_id
    FROM v36_identity_pairs candidate
    JOIN work_applied_effects effect ON effect.id = candidate.effect_id
    JOIN orchid_group_mutations mutation ON mutation.id = candidate.mutation_id
    GROUP BY candidate.mutation_id, effect.id, effect.command_details, effect.result_details
    HAVING count(*) = jsonb_array_length(effect.command_details -> 'sources')
       AND count(*) = jsonb_array_length(effect.command_details -> 'results')
       AND count(*) = jsonb_array_length(effect.result_details -> 'results')
       AND count(DISTINCT candidate.source_group_id) = count(*)
       AND count(DISTINCT candidate.result_group_id) = count(*)
       AND count(*) = (
           SELECT count(*)
           FROM orchid_group_mutation_entries entry
           WHERE entry.mutation_id = candidate.mutation_id
             AND entry.role = 'SOURCE'
       )
       AND count(*) = (
           SELECT count(*)
           FROM orchid_group_mutation_entries entry
           WHERE entry.mutation_id = candidate.mutation_id
             AND entry.role = 'RESULT'
       )
);

ALTER TABLE orchid_groups DISABLE TRIGGER USER;

DO $$
DECLARE
    selected_mutation_id BIGINT;
    mapping RECORD;
BEGIN
    -- Processing in mutation order also supports consecutive historical 1:1
    -- moves. When an earlier replacement ID is removed, later candidate source
    -- rows are redirected to the preserved root ID below.
    LOOP
        SELECT pair.mutation_id
          INTO selected_mutation_id
          FROM v36_identity_pairs pair
          JOIN orchid_group_mutations mutation ON mutation.id = pair.mutation_id
         ORDER BY mutation.occurred_at, pair.mutation_id
         LIMIT 1;
        EXIT WHEN selected_mutation_id IS NULL;

        IF EXISTS (
            SELECT 1
            FROM v36_identity_pairs left_pair
            JOIN v36_identity_pairs right_pair
              ON right_pair.mutation_id = left_pair.mutation_id
             AND right_pair.source_group_id = left_pair.result_group_id
            WHERE left_pair.mutation_id = selected_mutation_id
        ) THEN
            RAISE EXCEPTION 'V36 found a cyclic identity movement mutation %', selected_mutation_id;
        END IF;

        -- Conflicting targets would require merging two independently executed
        -- target rows. Fail closed instead of discarding either historical fact.
        IF EXISTS (
            SELECT 1
            FROM v36_identity_pairs pair
            JOIN work_operation_targets result_target
              ON result_target.orchid_group_id = pair.result_group_id
            JOIN work_operation_targets source_target
              ON source_target.work_operation_id = result_target.work_operation_id
             AND source_target.orchid_group_id = pair.source_group_id
            WHERE pair.mutation_id = selected_mutation_id
        ) THEN
            RAISE EXCEPTION 'V36 found conflicting work targets for mutation %', selected_mutation_id;
        END IF;

        IF EXISTS (
            SELECT 1
            FROM v36_identity_pairs pair
            JOIN orchid_group_mutation_entries result_entry
              ON result_entry.orchid_group_id = pair.result_group_id
             AND result_entry.mutation_id <> pair.mutation_id
            JOIN orchid_group_mutation_entries source_entry
              ON source_entry.mutation_id = result_entry.mutation_id
             AND source_entry.orchid_group_id = pair.source_group_id
            WHERE pair.mutation_id = selected_mutation_id
        ) THEN
            RAISE EXCEPTION 'V36 found conflicting downstream mutation entries for mutation %', selected_mutation_id;
        END IF;

        IF EXISTS (
            SELECT 1
            FROM v36_identity_pairs pair
            JOIN work_effect_orchid_groups result_link
              ON result_link.orchid_group_id = pair.result_group_id
            JOIN work_effect_orchid_groups source_link
              ON source_link.work_applied_effect_id = result_link.work_applied_effect_id
             AND source_link.orchid_group_id = pair.source_group_id
             AND source_link.relation_type = result_link.relation_type
            WHERE pair.mutation_id = selected_mutation_id
        ) THEN
            RAISE EXCEPTION 'V36 found conflicting work effect links for mutation %', selected_mutation_id;
        END IF;

        INSERT INTO orchid_group_identity_migrations (
            work_operation_id, mutation_id, preserved_orchid_group_id,
            removed_orchid_group_id, removed_group_snapshot
        )
        SELECT
            pair.work_operation_id,
            pair.mutation_id,
            pair.source_group_id,
            pair.result_group_id,
            to_jsonb(result_group)
        FROM v36_identity_pairs pair
        JOIN orchid_groups result_group ON result_group.id = pair.result_group_id
        WHERE pair.mutation_id = selected_mutation_id;

        -- The old SOURCE change becomes the current MOVE change. Its after state
        -- is the old result CREATE snapshot, so quantity and attributes remain
        -- continuous while only placement changes.
        UPDATE orchid_group_mutation_entries source_entry
        SET role = 'AFFECTED',
            after_state = result_entry.after_state
        FROM v36_identity_pairs pair
        JOIN orchid_group_mutation_entries result_entry
          ON result_entry.id = pair.result_entry_id
        WHERE pair.mutation_id = selected_mutation_id
          AND source_entry.id = pair.source_entry_id;

        -- Append every later revision of the replacement group to the original
        -- source chain. Revision 1 was the removed CREATE, so later revisions
        -- are shifted by source_revision_after - 1.
        UPDATE orchid_group_mutation_entries later_entry
        SET orchid_group_id = pair.source_group_id,
            state_revision_before = CASE
                WHEN later_entry.state_revision_before IS NULL THEN NULL
                ELSE later_entry.state_revision_before + source_entry.state_revision_after - 1
            END,
            state_revision_after = later_entry.state_revision_after + source_entry.state_revision_after - 1
        FROM v36_identity_pairs pair
        JOIN orchid_group_mutation_entries source_entry
          ON source_entry.id = pair.source_entry_id
        WHERE pair.mutation_id = selected_mutation_id
          AND later_entry.orchid_group_id = pair.result_group_id
          AND later_entry.id <> pair.result_entry_id;

        DELETE FROM orchid_group_mutation_entries result_entry
        USING v36_identity_pairs pair
        WHERE pair.mutation_id = selected_mutation_id
          AND result_entry.id = pair.result_entry_id;

        UPDATE orchid_group_mutations
        SET mutation_type = 'MOVE'
        WHERE id = selected_mutation_id;

        -- The movement's compatibility lineage represented replacement, not a
        -- real structural derivation. Later lineages keep their facts but use
        -- the preserved identity.
        DELETE FROM orchid_group_lineage lineage
        USING v36_identity_pairs pair
        WHERE pair.mutation_id = selected_mutation_id
          AND lineage.work_operation_id = pair.work_operation_id
          AND lineage.source_orchid_group_id = pair.source_group_id
          AND lineage.result_orchid_group_id = pair.result_group_id;

        FOR mapping IN
            SELECT *
            FROM v36_identity_pairs
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

            UPDATE orchid_group_collection_members
            SET orchid_group_id = mapping.source_group_id
            WHERE orchid_group_id = mapping.result_group_id;

            UPDATE work_operation_targets
            SET orchid_group_id = mapping.source_group_id
            WHERE orchid_group_id = mapping.result_group_id;

            UPDATE work_effect_orchid_groups
            SET orchid_group_id = mapping.source_group_id
            WHERE orchid_group_id = mapping.result_group_id;

            DELETE FROM orchid_group_lineage
            WHERE (source_orchid_group_id = mapping.result_group_id
                    AND result_orchid_group_id = mapping.source_group_id)
               OR (source_orchid_group_id = mapping.source_group_id
                    AND result_orchid_group_id = mapping.result_group_id);

            UPDATE orchid_group_lineage
            SET source_orchid_group_id = mapping.source_group_id
            WHERE source_orchid_group_id = mapping.result_group_id;

            UPDATE orchid_group_lineage
            SET result_orchid_group_id = mapping.source_group_id
            WHERE result_orchid_group_id = mapping.result_group_id;

            UPDATE sales_inventory_movements
            SET orchid_group_id = mapping.source_group_id
            WHERE orchid_group_id = mapping.result_group_id;

            UPDATE sales_slip_item_allocations
            SET orchid_group_id = mapping.source_group_id
            WHERE orchid_group_id = mapping.result_group_id;

            UPDATE sales_orchid_group_snapshots
            SET orchid_group_id = mapping.source_group_id
            WHERE orchid_group_id = mapping.result_group_id;

            -- Copy the replacement's current state to the preserved row. The
            -- original creation time remains the identity's creation time.
            UPDATE orchid_groups source_group
            SET updated_at = result_group.updated_at,
                bed_zone_id = result_group.bed_zone_id,
                variety_id = result_group.variety_id,
                inbound_record_id = result_group.inbound_record_id,
                genus = result_group.genus,
                variety_name = result_group.variety_name,
                quantity = result_group.quantity,
                reserved_quantity = result_group.reserved_quantity,
                pot_size = result_group.pot_size,
                pot_size_code = result_group.pot_size_code,
                age_year = result_group.age_year,
                status = result_group.status,
                placement_type = result_group.placement_type,
                tray_count = result_group.tray_count,
                split_placement_allowed = result_group.split_placement_allowed,
                sort_order = result_group.sort_order,
                memo = result_group.memo,
                start_position = result_group.start_position,
                end_position = result_group.end_position,
                version = greatest(source_group.version, result_group.version) + 1,
                state_revision = source_entry.state_revision_after + result_group.state_revision - 1
            FROM orchid_groups result_group,
                 orchid_group_mutation_entries source_entry
            WHERE source_group.id = mapping.source_group_id
              AND result_group.id = mapping.result_group_id
              AND source_entry.id = mapping.source_entry_id;

            DELETE FROM orchid_groups
            WHERE id = mapping.result_group_id;

            -- Later eligible movements may have used the replacement as their
            -- source. Point the pending candidate at the preserved root ID.
            UPDATE v36_identity_pairs
            SET source_group_id = mapping.source_group_id
            WHERE source_group_id = mapping.result_group_id
              AND mutation_id <> selected_mutation_id;
        END LOOP;

        DELETE FROM v36_identity_pairs WHERE mutation_id = selected_mutation_id;
    END LOOP;
END $$;

ALTER TABLE orchid_groups ENABLE TRIGGER USER;

-- Rewrite persisted work JSON only at fields that carry OrchidGroup IDs. Never
-- replace arbitrary equal numbers because quantities can equal an ID.
CREATE OR REPLACE FUNCTION v36_rewrite_orchid_group_ids(value JSONB, parent_key TEXT DEFAULT NULL)
RETURNS JSONB
LANGUAGE plpgsql
STABLE
AS $$
DECLARE
    rewritten JSONB;
BEGIN
    IF value IS NULL THEN
        RETURN NULL;
    END IF;
    IF jsonb_typeof(value) = 'object' THEN
        SELECT jsonb_object_agg(
                   CASE
                       WHEN parent_key = 'sourceInputQuantities'
                            AND key ~ '^[0-9]+$'
                       THEN COALESCE((
                           SELECT migration.preserved_orchid_group_id::TEXT
                           FROM orchid_group_identity_migrations migration
                           WHERE migration.removed_orchid_group_id = key::BIGINT
                       ), key)
                       ELSE key
                   END,
                   v36_rewrite_orchid_group_ids(child, key)
               )
          INTO rewritten
          FROM jsonb_each(value) item(key, child);
        RETURN COALESCE(rewritten, '{}'::jsonb);
    END IF;
    IF jsonb_typeof(value) = 'array' THEN
        SELECT jsonb_agg(v36_rewrite_orchid_group_ids(child, parent_key) ORDER BY position)
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
SET command_details = v36_rewrite_orchid_group_ids(command_details),
    result_details = v36_rewrite_orchid_group_ids(result_details);

UPDATE work_target_executions
SET result_details = v36_rewrite_orchid_group_ids(result_details)
WHERE result_details IS NOT NULL;

UPDATE work_operations
SET details = v36_rewrite_orchid_group_ids(details)
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

DROP FUNCTION v36_rewrite_orchid_group_ids(JSONB, TEXT);
