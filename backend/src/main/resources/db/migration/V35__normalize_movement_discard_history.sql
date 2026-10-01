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
