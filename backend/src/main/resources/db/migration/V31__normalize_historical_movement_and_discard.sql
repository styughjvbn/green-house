-- Consolidated from V35__normalize_movement_discard_history.sql: preserve this stage's SQL order.
-- Movement histories recorded before the cutover still have a
-- DISCARD -> TRANSFORM state chain. Preserve the recorded
-- per-source quantities and rewrite only the order and intermediate state to
-- the current TRANSFORM -> DISCARD model.
CREATE TEMP TABLE v35_movement_discard_entries ON COMMIT DROP AS
SELECT
    discard_operation.id AS discard_operation_id,
    movement_operation.id AS movement_operation_id,
    discard_effect.id AS discard_effect_id,
    movement_effect.id AS movement_effect_id,
    discard_entry.id AS discard_entry_id,
    movement_entry.id AS movement_entry_id,
    discard_entry.orchid_group_id,
    discard_entry.state_revision_before AS initial_revision,
    discard_entry.state_revision_after AS discarded_revision,
    movement_entry.state_revision_after AS final_revision,
    discard_entry.before_state AS initial_state,
    movement_entry.after_state AS final_state,
    (discard_entry.before_state ->> 'quantity')::INTEGER
        - (discard_entry.after_state ->> 'quantity')::INTEGER AS discard_quantity,
    (movement_entry.after_state ->> 'quantity')::INTEGER
        + ((discard_entry.before_state ->> 'quantity')::INTEGER
            - (discard_entry.after_state ->> 'quantity')::INTEGER) AS movement_remaining_quantity
FROM work_operations discard_operation
JOIN work_operations movement_operation
  ON movement_operation.id = discard_operation.parent_operation_id
JOIN work_types discard_type
  ON discard_type.id = discard_operation.work_type_id
 AND discard_type.code = 'DISCARD'
JOIN work_types movement_type
  ON movement_type.id = movement_operation.work_type_id
 AND movement_type.code = 'MOVEMENT'
JOIN work_applied_effects discard_effect
  ON discard_effect.work_operation_id = discard_operation.id
 AND discard_effect.mutation_id IS NOT NULL
JOIN orchid_group_mutations discard_mutation
  ON discard_mutation.id = discard_effect.mutation_id
 AND discard_mutation.mutation_type = 'DISCARD'
JOIN orchid_group_mutation_entries discard_entry
  ON discard_entry.mutation_id = discard_mutation.id
 AND discard_entry.before_state IS NOT NULL
 AND discard_entry.after_state IS NOT NULL
JOIN work_applied_effects movement_effect
  ON movement_effect.work_operation_id = movement_operation.id
 AND movement_effect.mutation_id IS NOT NULL
JOIN orchid_group_mutations movement_mutation
  ON movement_mutation.id = movement_effect.mutation_id
 AND movement_mutation.mutation_type = 'TRANSFORM'
JOIN orchid_group_mutation_entries movement_entry
  ON movement_entry.mutation_id = movement_mutation.id
 AND movement_entry.orchid_group_id = discard_entry.orchid_group_id
 AND movement_entry.before_state IS NOT NULL
 AND movement_entry.after_state IS NOT NULL
 AND movement_entry.state_revision_before = discard_entry.state_revision_after
 AND movement_entry.before_state = discard_entry.after_state
WHERE discard_operation.relation_type = 'MOVEMENT_DISCARD';

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM v35_movement_discard_entries
        GROUP BY discard_entry_id HAVING count(*) <> 1
    ) OR EXISTS (
        SELECT 1 FROM v35_movement_discard_entries
        GROUP BY movement_entry_id HAVING count(*) <> 1
    ) THEN
        RAISE EXCEPTION 'V35 found an ambiguous movement/discard state chain';
    END IF;
END $$;

-- Move both entries away from their old unique revision keys before assigning
-- the revisions in the opposite order.
UPDATE orchid_group_mutation_entries entry
SET state_revision_before = entry.state_revision_before + 1000000000,
    state_revision_after = entry.state_revision_after + 1000000000
WHERE entry.id IN (
    SELECT discard_entry_id FROM v35_movement_discard_entries
    UNION
    SELECT movement_entry_id FROM v35_movement_discard_entries
);

UPDATE orchid_group_mutation_entries movement_entry
SET state_revision_before = migration.initial_revision,
    state_revision_after = migration.discarded_revision,
    before_state = migration.initial_state,
    after_state = jsonb_set(
        jsonb_set(migration.final_state, '{quantity}',
            to_jsonb(migration.movement_remaining_quantity), FALSE),
        '{status}', migration.initial_state -> 'status', FALSE
    )
FROM v35_movement_discard_entries migration
WHERE movement_entry.id = migration.movement_entry_id;

UPDATE orchid_group_mutation_entries discard_entry
SET state_revision_before = migration.discarded_revision,
    state_revision_after = migration.final_revision,
    before_state = movement_entry.after_state,
    after_state = jsonb_set(
        migration.final_state,
        '{status}',
        CASE
            WHEN (migration.final_state ->> 'quantity')::INTEGER = 0
                THEN '"폐기"'::jsonb
            ELSE migration.final_state -> 'status'
        END,
        FALSE
    )
FROM v35_movement_discard_entries migration
JOIN orchid_group_mutation_entries movement_entry
  ON movement_entry.id = migration.movement_entry_id
WHERE discard_entry.id = migration.discard_entry_id;

-- Preserve the full snapshot connection to a later mutation without changing
-- that mutation's result, revision, or the group's later current state.
UPDATE orchid_group_mutation_entries following_entry
SET before_state = discard_entry.after_state
FROM v35_movement_discard_entries migration
JOIN orchid_group_mutation_entries discard_entry
  ON discard_entry.id = migration.discard_entry_id
WHERE following_entry.orchid_group_id = migration.orchid_group_id
  AND following_entry.state_revision_before = migration.final_revision
  AND following_entry.before_state = migration.final_state
  AND following_entry.before_state IS DISTINCT FROM discard_entry.after_state;

-- This rewrites historical facts, not a new business mutation. Keep revision,
-- quantity, placement, version and timestamps intact. Synchronize the current
-- status only when this rewritten chain is still the group's terminal state.
-- ACTIVE ledgers normally require a new revision on UPDATE, so suspend only
-- the two ledger triggers within Flyway's transaction (as in V36).
ALTER TABLE orchid_groups DISABLE TRIGGER trg_orchid_group_write_fence;
ALTER TABLE orchid_groups DISABLE TRIGGER trg_orchid_group_ledger_entry;

UPDATE orchid_groups orchid_group
SET status = discard_entry.after_state ->> 'status'
FROM v35_movement_discard_entries migration
JOIN orchid_group_mutation_entries discard_entry
  ON discard_entry.id = migration.discard_entry_id
WHERE orchid_group.id = migration.orchid_group_id
  AND orchid_group.state_revision = migration.final_revision
  AND orchid_group.quantity = (migration.final_state ->> 'quantity')::INTEGER
  AND orchid_group.status = migration.final_state ->> 'status'
  AND orchid_group.status IS DISTINCT FROM discard_entry.after_state ->> 'status'
  AND NOT EXISTS (
      SELECT 1 FROM orchid_group_mutation_entries later_entry
      WHERE later_entry.orchid_group_id = orchid_group.id
        AND later_entry.state_revision_after > migration.final_revision
  );

ALTER TABLE orchid_groups ENABLE TRIGGER trg_orchid_group_write_fence;
ALTER TABLE orchid_groups ENABLE TRIGGER trg_orchid_group_ledger_entry;

UPDATE work_applied_effects effect
SET command_details = jsonb_set(effect.command_details, '{reason}',
        '"자리 이동 후 잔여 난 선별 폐기"'::jsonb, TRUE),
    result_details = jsonb_set(
        jsonb_set(
            jsonb_set(
                jsonb_set(effect.result_details, '{reason}',
                    '"자리 이동 후 잔여 난 선별 폐기"'::jsonb, TRUE),
                '{beforeQuantity}', to_jsonb(migration.movement_remaining_quantity), TRUE),
            '{remainingQuantity}', migration.final_state -> 'quantity', TRUE),
        '{status}',
        CASE
            WHEN (migration.final_state ->> 'quantity')::INTEGER = 0
                THEN '"폐기"'::jsonb
            ELSE migration.final_state -> 'status'
        END,
        TRUE
    )
FROM v35_movement_discard_entries migration
WHERE effect.id = migration.discard_effect_id;

UPDATE work_target_executions execution
SET result_details = jsonb_set(
        jsonb_set(
            jsonb_set(
                jsonb_set(execution.result_details, '{reason}',
                    '"자리 이동 후 잔여 난 선별 폐기"'::jsonb, TRUE),
                '{beforeQuantity}', to_jsonb(migration.movement_remaining_quantity), TRUE),
            '{remainingQuantity}', migration.final_state -> 'quantity', TRUE),
        '{status}',
        CASE
            WHEN (migration.final_state ->> 'quantity')::INTEGER = 0
                THEN '"폐기"'::jsonb
            ELSE migration.final_state -> 'status'
        END,
        TRUE
    )
FROM work_operation_targets target
JOIN v35_movement_discard_entries migration
  ON migration.discard_operation_id = target.work_operation_id
 AND migration.orchid_group_id = target.orchid_group_id
WHERE execution.work_operation_target_id = target.id;

UPDATE work_operation_targets target
SET quantity_snapshot = migration.movement_remaining_quantity
FROM v35_movement_discard_entries migration
WHERE target.work_operation_id = migration.discard_operation_id
  AND target.orchid_group_id = migration.orchid_group_id;

-- Single-source movement results expose the remaining source quantity. Align
-- that compatibility field with the rewritten intermediate state.
UPDATE work_applied_effects effect
SET result_details = jsonb_set(effect.result_details, '{remainingQuantity}',
        to_jsonb(migration.movement_remaining_quantity), FALSE)
FROM v35_movement_discard_entries migration
WHERE effect.id = migration.movement_effect_id
  AND effect.result_details ? 'remainingQuantity';

UPDATE work_target_executions execution
SET result_details = jsonb_set(execution.result_details, '{remainingQuantity}',
        to_jsonb(migration.movement_remaining_quantity), FALSE)
FROM work_operation_targets target
JOIN v35_movement_discard_entries migration
  ON migration.movement_operation_id = target.work_operation_id
 AND migration.orchid_group_id = target.orchid_group_id
WHERE execution.work_operation_target_id = target.id
  AND execution.result_details ? 'remainingQuantity';

UPDATE work_operations operation
SET title = CASE
        WHEN operation.title LIKE '% - 이동 전 선별 폐기'
            THEN regexp_replace(operation.title, ' - 이동 전 선별 폐기$', ' - 이동 후 잔여 난 폐기')
        WHEN operation.title LIKE '% - 동시 폐기'
            THEN regexp_replace(operation.title, ' - 동시 폐기$', ' - 이동 후 잔여 난 폐기')
        ELSE operation.title
    END,
    details = (COALESCE(operation.details, '{}'::jsonb) - 'movementOperationId' - 'relation')
        || jsonb_build_object(
            'allocationMethod', COALESCE(
                operation.details ->> 'allocationMethod',
                'LEGACY_RECORDED_SOURCE_ALLOCATION'
            ),
            'totalDiscardQuantity', (
                SELECT sum((effect.result_details ->> 'discardedQuantity')::INTEGER)
                FROM work_applied_effects effect
                WHERE effect.work_operation_id = operation.id
            )
        )
WHERE operation.relation_type = 'MOVEMENT_DISCARD';

-- Consolidated from V36__preserve_identity_for_historical_movements.sql: preserve this stage's SQL order.
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

-- Consolidated from V37__recover_legacy_movement_source_attributes.sql: preserve this stage's SQL order.
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

-- Consolidated from V38__split_multi_variety_discard_operations.sql: preserve this stage's SQL order.
CREATE TEMP TABLE v38_multi_variety_discard_operations ON COMMIT DROP AS
SELECT operation.id AS operation_id
FROM work_operations operation
JOIN work_types work_type
  ON work_type.id = operation.work_type_id
 AND work_type.code = 'DISCARD'
JOIN work_operation_targets target
  ON target.work_operation_id = operation.id
GROUP BY operation.id
HAVING count(DISTINCT COALESCE(
    target.variety_id_snapshot::TEXT,
    'name:' || target.variety_name_snapshot
)) > 1;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM work_operation_targets target
        JOIN v38_multi_variety_discard_operations candidate
          ON candidate.operation_id = target.work_operation_id
        WHERE target.variety_id_snapshot IS NULL
    ) THEN
        RAISE EXCEPTION 'V38 cannot split a multi-variety discard with an unidentified variety';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM work_operation_targets target
        JOIN v38_multi_variety_discard_operations candidate
          ON candidate.operation_id = target.work_operation_id
        GROUP BY target.work_operation_id, target.variety_id_snapshot
        HAVING count(DISTINCT target.variety_name_snapshot) <> 1
    ) THEN
        RAISE EXCEPTION 'V38 found inconsistent variety names in a discard operation';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM work_operations operation
        JOIN v38_multi_variety_discard_operations candidate
          ON candidate.operation_id = operation.id
        WHERE operation.request_key IS NOT NULL
           OR operation.parent_operation_id IS NOT NULL
           OR operation.relation_type IS NOT NULL
           OR operation.void_request_key IS NOT NULL
           OR operation.void_mutation_id IS NOT NULL
           OR operation.voided_at IS NOT NULL
           OR operation.details ? 'sourceInputQuantities'
    ) THEN
        RAISE EXCEPTION 'V38 found a multi-variety discard with unsupported operation metadata';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM work_operations child
        JOIN v38_multi_variety_discard_operations candidate
          ON candidate.operation_id = child.parent_operation_id
    ) OR EXISTS (
        SELECT 1
        FROM work_operation_corrections correction
        JOIN v38_multi_variety_discard_operations candidate
          ON candidate.operation_id IN (
              correction.original_work_operation_id,
              correction.correction_work_operation_id
          )
    ) OR EXISTS (
        SELECT 1
        FROM work_command_receipt_memberships membership
        JOIN v38_multi_variety_discard_operations candidate
          ON candidate.operation_id = membership.operation_id
    ) OR EXISTS (
        SELECT 1
        FROM orchid_group_lineage lineage
        JOIN v38_multi_variety_discard_operations candidate
          ON candidate.operation_id = lineage.work_operation_id
    ) OR EXISTS (
        SELECT 1
        FROM orchid_group_identity_migrations identity_migration
        JOIN v38_multi_variety_discard_operations candidate
          ON candidate.operation_id = identity_migration.work_operation_id
    ) THEN
        RAISE EXCEPTION 'V38 found a multi-variety discard with unsupported work links';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM work_applied_effects effect
        JOIN v38_multi_variety_discard_operations candidate
          ON candidate.operation_id = effect.work_operation_id
        LEFT JOIN work_operation_targets target
          ON target.id = effect.work_operation_target_id
         AND target.work_operation_id = candidate.operation_id
        WHERE target.id IS NULL
    ) THEN
        RAISE EXCEPTION 'V38 found a discard effect without an operation target';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM work_applied_effects effect
        JOIN work_operation_targets target
          ON target.id = effect.work_operation_target_id
        JOIN v38_multi_variety_discard_operations candidate
          ON candidate.operation_id = effect.work_operation_id
        WHERE effect.mutation_id IS NOT NULL
        GROUP BY effect.mutation_id
        HAVING count(DISTINCT target.variety_id_snapshot) > 1
    ) THEN
        RAISE EXCEPTION 'V38 found a Mutation shared by multiple discard varieties';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM work_operations operation
        JOIN v38_multi_variety_discard_operations candidate
          ON candidate.operation_id = operation.id
        JOIN work_operation_targets target
          ON target.work_operation_id = operation.id
        GROUP BY operation.id, operation.title, target.variety_id_snapshot
        HAVING char_length(operation.title) + 3 + char_length(min(target.variety_name_snapshot)) > 150
    ) THEN
        RAISE EXCEPTION 'V38 cannot append a variety name to a discard title longer than 150 characters';
    END IF;
END $$;

CREATE TEMP TABLE v38_discard_variety_operations ON COMMIT DROP AS
SELECT
    grouped.operation_id AS original_operation_id,
    grouped.variety_id,
    grouped.variety_name,
    row_number() OVER (
        PARTITION BY grouped.operation_id
        ORDER BY grouped.first_target_id
    ) AS variety_ordinal,
    NULL::BIGINT AS split_operation_id,
    NULL::UUID AS split_correlation_id
FROM (
    SELECT
        target.work_operation_id AS operation_id,
        target.variety_id_snapshot AS variety_id,
        min(target.variety_name_snapshot) AS variety_name,
        min(target.id) AS first_target_id
    FROM work_operation_targets target
    JOIN v38_multi_variety_discard_operations candidate
      ON candidate.operation_id = target.work_operation_id
    GROUP BY target.work_operation_id, target.variety_id_snapshot
) grouped;

UPDATE v38_discard_variety_operations mapping
SET split_operation_id = CASE
        WHEN mapping.variety_ordinal = 1 THEN mapping.original_operation_id
        ELSE nextval('work_operations_id_seq')
    END;

WITH operation_hashes AS (
    SELECT
        mapping.original_operation_id,
        mapping.variety_id,
        md5('WORK_OPERATION:' || mapping.split_operation_id) AS hash
    FROM v38_discard_variety_operations mapping
)
UPDATE v38_discard_variety_operations mapping
SET split_correlation_id = (
        substring(hash.hash, 1, 8) || '-' ||
        substring(hash.hash, 9, 4) || '-3' ||
        substring(hash.hash, 14, 3) || '-' ||
        to_hex(8 + ((get_byte(decode(hash.hash, 'hex'), 8) >> 4) & 3)) ||
        substring(hash.hash, 18, 3) || '-' ||
        substring(hash.hash, 21, 12)
    )::UUID
FROM operation_hashes hash
WHERE hash.original_operation_id = mapping.original_operation_id
  AND hash.variety_id = mapping.variety_id;

INSERT INTO work_operations (
    id, work_type_id, title, status, planned_start_date, planned_end_date,
    actual_start_at, actual_end_at, source_scope_type, source_scope_id,
    source_condition_snapshot, target_snapshot_at, details, worker, memo,
    request_key, version, created_at, updated_at, voided_at, void_reason,
    void_request_key, void_mutation_id, parent_operation_id, relation_type
)
SELECT
    mapping.split_operation_id,
    original.work_type_id,
    original.title || ' - ' || mapping.variety_name,
    original.status,
    original.planned_start_date,
    original.planned_end_date,
    original.actual_start_at,
    original.actual_end_at,
    original.source_scope_type,
    original.source_scope_id,
    CASE
        WHEN original.source_condition_snapshot ? 'orchidGroupIds' THEN
            jsonb_set(
                original.source_condition_snapshot,
                '{orchidGroupIds}',
                (
                    SELECT jsonb_agg(target.orchid_group_id ORDER BY target.id)
                    FROM work_operation_targets target
                    WHERE target.work_operation_id = mapping.original_operation_id
                      AND target.variety_id_snapshot = mapping.variety_id
                ),
                FALSE
            )
        ELSE original.source_condition_snapshot
    END,
    original.target_snapshot_at,
    original.details,
    original.worker,
    original.memo,
    NULL,
    original.version,
    original.created_at,
    original.updated_at,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
FROM v38_discard_variety_operations mapping
JOIN work_operations original
  ON original.id = mapping.original_operation_id
WHERE mapping.variety_ordinal > 1;

UPDATE work_operations original
SET title = original.title || ' - ' || mapping.variety_name,
    source_condition_snapshot = CASE
        WHEN original.source_condition_snapshot ? 'orchidGroupIds' THEN
            jsonb_set(
                original.source_condition_snapshot,
                '{orchidGroupIds}',
                (
                    SELECT jsonb_agg(target.orchid_group_id ORDER BY target.id)
                    FROM work_operation_targets target
                    WHERE target.work_operation_id = mapping.original_operation_id
                      AND target.variety_id_snapshot = mapping.variety_id
                ),
                FALSE
            )
        ELSE original.source_condition_snapshot
    END
FROM v38_discard_variety_operations mapping
WHERE mapping.variety_ordinal = 1
  AND original.id = mapping.original_operation_id;

UPDATE work_operation_targets target
SET work_operation_id = mapping.split_operation_id
FROM v38_discard_variety_operations mapping
WHERE mapping.variety_ordinal > 1
  AND target.work_operation_id = mapping.original_operation_id
  AND target.variety_id_snapshot = mapping.variety_id;

UPDATE work_applied_effects effect
SET work_operation_id = mapping.split_operation_id
FROM work_operation_targets target,
     v38_discard_variety_operations mapping
WHERE effect.work_operation_target_id = target.id
  AND effect.work_operation_id = mapping.original_operation_id
  AND target.work_operation_id = mapping.split_operation_id
  AND target.variety_id_snapshot = mapping.variety_id
  AND effect.work_operation_id <> mapping.split_operation_id;

UPDATE work_applied_effects effect
SET correlation_id = CASE
        WHEN effect.mutation_id IS NULL AND effect.correlation_id IS NULL THEN NULL
        ELSE mapping.split_correlation_id
    END
FROM v38_discard_variety_operations mapping
WHERE effect.work_operation_id = mapping.split_operation_id;

UPDATE orchid_group_mutations mutation
SET source_reference_id = effect.work_operation_id::TEXT,
    correlation_id = mapping.split_correlation_id
FROM work_applied_effects effect
JOIN v38_discard_variety_operations mapping
  ON mapping.split_operation_id = effect.work_operation_id
WHERE mutation.id = effect.mutation_id
  AND mutation.source_domain = 'WORK'
  AND mutation.source_type = 'WORK_EFFECT';

UPDATE work_operations operation
SET details = jsonb_set(
        operation.details,
        '{totalDiscardQuantity}',
        to_jsonb((
            SELECT sum((effect.result_details ->> 'discardedQuantity')::INTEGER)
            FROM work_applied_effects effect
            WHERE effect.work_operation_id = operation.id
        )),
        FALSE
    )
WHERE operation.id IN (
    SELECT split_operation_id FROM v38_discard_variety_operations
)
  AND operation.details ? 'totalDiscardQuantity';

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM v38_discard_variety_operations mapping
        JOIN work_operation_targets target
          ON target.work_operation_id = mapping.split_operation_id
        WHERE target.variety_id_snapshot <> mapping.variety_id
    ) OR EXISTS (
        SELECT 1
        FROM v38_discard_variety_operations mapping
        LEFT JOIN work_operation_targets target
          ON target.work_operation_id = mapping.split_operation_id
        GROUP BY mapping.original_operation_id, mapping.variety_id
        HAVING count(target.id) = 0
    ) OR EXISTS (
        SELECT 1
        FROM work_applied_effects effect
        JOIN work_operation_targets target
          ON target.id = effect.work_operation_target_id
        JOIN v38_discard_variety_operations mapping
          ON mapping.split_operation_id = target.work_operation_id
        WHERE effect.work_operation_id <> target.work_operation_id
    ) OR EXISTS (
        SELECT 1
        FROM orchid_group_mutations mutation
        JOIN work_applied_effects effect
          ON effect.mutation_id = mutation.id
        JOIN v38_discard_variety_operations mapping
          ON mapping.split_operation_id = effect.work_operation_id
        WHERE mutation.source_reference_id IS DISTINCT FROM effect.work_operation_id::TEXT
           OR mutation.correlation_id IS DISTINCT FROM mapping.split_correlation_id
           OR effect.correlation_id IS DISTINCT FROM mapping.split_correlation_id
    ) THEN
        RAISE EXCEPTION 'V38 failed to normalize multi-variety discard operations';
    END IF;
END $$;

-- Consolidated from V41__repair_movement_discard_processed_quantity.sql: preserve this stage's SQL order.
-- V35 normalized legacy movement-discard targets to the residual quantity,
-- but their completed execution usage still retained the pre-movement source
-- quantity. Align completed usage with the normalized target snapshot.
UPDATE work_target_executions execution
SET processed_quantity = target.quantity_snapshot,
    version = execution.version + 1,
    updated_at = CURRENT_TIMESTAMP
FROM work_operation_targets target
JOIN work_operations operation
  ON operation.id = target.work_operation_id
WHERE execution.work_operation_target_id = target.id
  AND operation.relation_type = 'MOVEMENT_DISCARD'
  AND operation.details ->> 'allocationMethod' = 'LEGACY_RECORDED_SOURCE_ALLOCATION'
  AND execution.status = 'COMPLETED'
  AND execution.processed_quantity IS DISTINCT FROM target.quantity_snapshot;
