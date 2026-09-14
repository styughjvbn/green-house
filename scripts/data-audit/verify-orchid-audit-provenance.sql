\set ON_ERROR_STOP on

DO $$
DECLARE
    audit_two_mutation_id BIGINT;
BEGIN
    SELECT mutation_id INTO audit_two_mutation_id FROM audit_events WHERE id = 2;

    IF (SELECT count(*) FROM orchid_groups) <> 269
       OR (SELECT count(*) FROM orchid_group_mutations) <> 315
       OR (SELECT count(*) FROM orchid_group_mutation_entries) <> 349
       OR (SELECT count(*) FROM audit_events) <> 5
       OR audit_two_mutation_id IS NULL THEN
        RAISE EXCEPTION 'Audit provenance verification count mismatch';
    END IF;

    IF (SELECT count(*) FROM audit_events
        WHERE (id, entity_id, mutation_id) IN (
            (1, 267, 34), (2, 272, audit_two_mutation_id), (3, 256, 109),
            (4, 252, 15), (5, 266, 137)
        )) <> 5
       OR NOT EXISTS (
            SELECT 1 FROM orchid_group_mutations
            WHERE id = audit_two_mutation_id
              AND mutation_type = 'CORRECTION'
              AND source_domain = 'MIGRATION'
              AND source_type = 'AUDIT_EVENT'
              AND source_reference_id = '2'
              AND source_operation_key = 'ORCHID_GROUP_AUDIT:2'
       ) THEN
        RAISE EXCEPTION 'Audit-to-Mutation links are incorrect';
    END IF;

    IF EXISTS (
        SELECT 1 FROM (
            SELECT entry.*, lag(state_revision_after) OVER chain AS previous_revision,
                   lag(after_state) OVER chain AS previous_after,
                   row_number() OVER chain AS chain_position
            FROM orchid_group_mutation_entries entry
            WINDOW chain AS (PARTITION BY orchid_group_id ORDER BY state_revision_after)
        ) chain
        WHERE (chain_position = 1 AND NOT (
                  (entry_kind = 'BASELINE' AND state_revision_before IS NULL AND state_revision_after = 0)
               OR (entry_kind = 'CREATE' AND state_revision_before IS NULL AND state_revision_after = 1)))
           OR (chain_position > 1 AND (
                  state_revision_before IS DISTINCT FROM previous_revision
               OR state_revision_after IS DISTINCT FROM previous_revision + 1
               OR before_state IS DISTINCT FROM previous_after))
    ) THEN
        RAISE EXCEPTION 'Revision or snapshot chain discontinuity found';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM orchid_groups current_group
        LEFT JOIN LATERAL (
            SELECT state_revision_after, after_state
            FROM orchid_group_mutation_entries
            WHERE orchid_group_id = current_group.id
            ORDER BY state_revision_after DESC LIMIT 1
        ) latest ON TRUE
        WHERE current_group.state_revision IS DISTINCT FROM latest.state_revision_after
           OR jsonb_build_object(
                'quantity', current_group.quantity,
                'reservedQuantity', current_group.reserved_quantity,
                'status', current_group.status,
                'bedZoneId', current_group.bed_zone_id,
                'sortOrder', current_group.sort_order,
                'startPosition', current_group.start_position,
                'endPosition', current_group.end_position,
                'varietyId', current_group.variety_id,
                'genus', current_group.genus,
                'varietyName', current_group.variety_name,
                'ageYear', current_group.age_year,
                'potSizeCode', current_group.pot_size_code,
                'placementType', current_group.placement_type,
                'trayCount', current_group.tray_count,
                'splitPlacementAllowed', current_group.split_placement_allowed,
                'inboundRecordId', current_group.inbound_record_id,
                'memo', current_group.memo
              ) IS DISTINCT FROM latest.after_state
    ) THEN
        RAISE EXCEPTION 'Current OrchidGroup snapshot or revision differs from its last Entry';
    END IF;

    IF (SELECT count(*) FROM orchid_group_mutation_entries
        WHERE orchid_group_id = 272 AND mutation_id = 203
          AND entry_kind = 'CREATE' AND state_revision_after = 1
          AND after_state -> 'ageYear' = 'null'::jsonb
          AND after_state ->> 'potSizeCode' = 'POT_2') <> 1
       OR (SELECT count(*) FROM orchid_group_mutation_entries
        WHERE orchid_group_id = 272 AND mutation_id = audit_two_mutation_id
          AND entry_kind = 'CHANGE' AND state_revision_before = 1
          AND state_revision_after = 2
          AND before_state -> 'ageYear' = 'null'::jsonb
          AND before_state ->> 'potSizeCode' = 'POT_2'
          AND after_state -> 'ageYear' = '2'::jsonb
          AND after_state ->> 'potSizeCode' = 'POT_3_5') <> 1
       OR (SELECT count(*) FROM orchid_groups
           WHERE id = 272 AND state_revision = 2 AND age_year = 2
             AND pot_size_code = 'POT_3_5') <> 1 THEN
        RAISE EXCEPTION 'OrchidGroup 272 corrected chain is incorrect';
    END IF;

    IF (SELECT count(*) FROM work_applied_effects WHERE mutation_id IS NOT NULL) <> 42
       OR (SELECT count(*) FROM orchid_group_lineage WHERE mutation_id IS NOT NULL) <> 16
       OR (SELECT count(*) FROM work_applied_effects WHERE id = 50 AND mutation_id = 203) <> 1
       OR (SELECT count(*) FROM orchid_group_lineage
           WHERE id IN (14, 15, 16) AND mutation_id = 203) <> 3 THEN
        RAISE EXCEPTION 'Work or Lineage links are incorrect';
    END IF;

    IF (SELECT count(*) FROM orchid_groups WHERE state_revision = 0) <> 101
       OR (SELECT count(*) FROM orchid_groups WHERE state_revision = 1) <> 122
       OR (SELECT count(*) FROM orchid_groups WHERE state_revision = 2) <> 37
       OR (SELECT count(*) FROM orchid_groups WHERE state_revision = 3) <> 9 THEN
        RAISE EXCEPTION 'Final revision distribution mismatch';
    END IF;
END $$;

SELECT 'PASS' AS result,
       (SELECT count(*) FROM orchid_groups) AS orchid_groups,
       (SELECT count(*) FROM orchid_group_mutations) AS mutations,
       (SELECT count(*) FROM orchid_group_mutation_entries) AS entries,
       (SELECT count(*) FROM audit_events WHERE mutation_id IS NOT NULL) AS linked_audits;

SELECT audit.id AS audit_id, audit.entity_id, audit.mutation_id,
       mutation.source_type, mutation.source_reference_id
FROM audit_events audit
JOIN orchid_group_mutations mutation ON mutation.id = audit.mutation_id
ORDER BY audit.id;

SELECT orchid_group_id, state_revision_before, state_revision_after,
       before_state -> 'ageYear' AS before_age_year,
       before_state ->> 'potSizeCode' AS before_pot_size,
       after_state -> 'ageYear' AS after_age_year,
       after_state ->> 'potSizeCode' AS after_pot_size
FROM orchid_group_mutation_entries
WHERE orchid_group_id = 272
ORDER BY state_revision_after;
