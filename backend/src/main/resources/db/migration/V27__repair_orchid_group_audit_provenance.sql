ALTER TABLE audit_events
    ADD COLUMN mutation_id BIGINT;

ALTER TABLE audit_events
    ADD CONSTRAINT fk_audit_events_orchid_group_mutation
        FOREIGN KEY (mutation_id) REFERENCES orchid_group_mutations(id);

CREATE INDEX idx_audit_events_mutation
    ON audit_events(mutation_id)
    WHERE mutation_id IS NOT NULL;

-- Preserve the complete pre-migration state so the targeted rebuild can prove
-- that no current OrchidGroup business value or unrelated ledger row changed.
CREATE TEMP TABLE v27_orchid_group_snapshot_before ON COMMIT DROP AS
SELECT id,
       jsonb_build_object(
           'quantity', quantity,
           'reservedQuantity', reserved_quantity,
           'status', status,
           'bedZoneId', bed_zone_id,
           'sortOrder', sort_order,
           'startPosition', start_position,
           'endPosition', end_position,
           'varietyId', variety_id,
           'genus', genus,
           'varietyName', variety_name,
           'ageYear', age_year,
           'potSizeCode', pot_size_code,
           'placementType', placement_type,
           'trayCount', tray_count,
           'splitPlacementAllowed', split_placement_allowed,
           'inboundRecordId', inbound_record_id,
           'memo', memo
       ) AS snapshot
FROM orchid_groups;

CREATE TEMP TABLE v27_mutations_before ON COMMIT DROP AS
SELECT id, to_jsonb(mutation_row) AS payload
FROM orchid_group_mutations mutation_row;

CREATE TEMP TABLE v27_entries_before ON COMMIT DROP AS
SELECT id, to_jsonb(entry_row) AS payload
FROM orchid_group_mutation_entries entry_row;

CREATE TEMP TABLE v27_work_links_before ON COMMIT DROP AS
SELECT id, mutation_id, correlation_id
FROM work_applied_effects
WHERE mutation_id IS NOT NULL OR correlation_id IS NOT NULL;

CREATE TEMP TABLE v27_lineage_links_before ON COMMIT DROP AS
SELECT id, mutation_id
FROM orchid_group_lineage
WHERE mutation_id IS NOT NULL;

CREATE TEMP TABLE v27_repair_context (
    apply_repair BOOLEAN NOT NULL,
    audit_correction_mutation_id BIGINT
) ON COMMIT DROP;

INSERT INTO v27_repair_context (apply_repair) VALUES (FALSE);

DO $$
DECLARE
    mutation_count BIGINT;
    entry_count BIGINT;
    group_count BIGINT;
    audit_count BIGINT;
    active_coverage_count BIGINT;
BEGIN
    LOCK TABLE orchid_group_ledger_coverages, orchid_group_mutations,
        orchid_group_mutation_entries, orchid_groups, audit_events,
        work_applied_effects, orchid_group_lineage IN SHARE ROW EXCLUSIVE MODE;

    SELECT count(*) INTO mutation_count FROM orchid_group_mutations;
    SELECT count(*) INTO entry_count FROM orchid_group_mutation_entries;
    SELECT count(*) INTO group_count FROM orchid_groups;
    SELECT count(*) INTO audit_count FROM audit_events;
    SELECT count(*) INTO active_coverage_count
    FROM orchid_group_ledger_coverages WHERE status = 'ACTIVE';

    -- A brand-new database has no historical cutover to repair. Any partially
    -- populated database must match the reviewed production backup exactly.
    IF mutation_count = 0 AND entry_count = 0 AND group_count = 0
            AND audit_count = 0 AND active_coverage_count = 0 THEN
        RETURN;
    END IF;

    IF mutation_count <> 314 OR entry_count <> 348 OR group_count <> 269
            OR audit_count <> 5 OR active_coverage_count <> 1 THEN
        RAISE EXCEPTION
            'V27 precondition count mismatch: mutations=%, entries=%, groups=%, audits=%, active_coverages=%',
            mutation_count, entry_count, group_count, audit_count, active_coverage_count;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM orchid_group_ledger_coverages
        WHERE cutover_key = 'deb9b0c1-d3a1-4b91-8a5e-202609090001'::uuid
          AND status = 'ACTIVE'
          AND engine_schema_version = 1
          AND snapshot_schema_version = 1
          AND baseline_group_count = 114
          AND import_fingerprint = '19aad9ae5abeb90b25eaf4da6f4b9084a8335034a4275df67df75f7e5793341b'
    ) THEN
        RAISE EXCEPTION 'V27 target ACTIVE ledger coverage does not match the reviewed cutover';
    END IF;

    IF (SELECT count(*) FROM orchid_groups WHERE state_revision = 0) <> 101
            OR (SELECT count(*) FROM orchid_groups WHERE state_revision = 1) <> 123
            OR (SELECT count(*) FROM orchid_groups WHERE state_revision = 2) <> 36
            OR (SELECT count(*) FROM orchid_groups WHERE state_revision = 3) <> 9
            OR EXISTS (SELECT 1 FROM orchid_groups WHERE state_revision NOT IN (0, 1, 2, 3)) THEN
        RAISE EXCEPTION 'V27 OrchidGroup revision distribution differs from the reviewed backup';
    END IF;

    IF (SELECT count(*) FROM audit_events
        WHERE id = 1 AND entity_id = 267 AND entity_type = 'ORCHID_GROUP'
          AND action = 'CREATED' AND source = 'ORCHID_GROUP_MANAGEMENT'
          AND mutation_id IS NULL AND before_data IS NULL
          AND after_data = '{"status":"정상","zoneId":7,"ageYear":1,"houseId":2,"potSize":"2\"","quantity":1,"varietyId":86,"endPosition":1.00,"physicalBedId":4,"startPosition":0.00}'::jsonb) <> 1
       OR (SELECT count(*) FROM audit_events
        WHERE id = 2 AND entity_id = 272 AND entity_type = 'ORCHID_GROUP'
          AND action = 'UPDATED' AND source = 'ORCHID_GROUP_CORRECTION'
          AND mutation_id IS NULL
          AND before_data = '{"status":"정상","zoneId":12,"ageYear":null,"houseId":2,"potSize":"2\"","quantity":810,"varietyId":105,"endPosition":5.00,"physicalBedId":6,"startPosition":0.00}'::jsonb
          AND after_data = '{"status":"정상","zoneId":12,"ageYear":2,"houseId":2,"potSize":"3.5\"","quantity":810,"varietyId":105,"endPosition":5.00,"physicalBedId":6,"startPosition":0.00}'::jsonb) <> 1
       OR (SELECT count(*) FROM audit_events
        WHERE id = 3 AND entity_id = 256 AND entity_type = 'ORCHID_GROUP'
          AND action = 'UPDATED' AND source = 'ORCHID_GROUP_CORRECTION'
          AND mutation_id IS NULL
          AND before_data -> 'quantity' = '259'::jsonb
          AND after_data -> 'quantity' = '574'::jsonb) <> 1
       OR (SELECT count(*) FROM audit_events
        WHERE id = 4 AND entity_id = 252 AND entity_type = 'ORCHID_GROUP'
          AND action = 'UPDATED' AND source = 'ORCHID_GROUP_CORRECTION'
          AND mutation_id IS NULL
          AND before_data -> 'quantity' = '100'::jsonb
          AND after_data -> 'quantity' = '378'::jsonb) <> 1
       OR (SELECT count(*) FROM audit_events
        WHERE id = 5 AND entity_id = 266 AND entity_type = 'ORCHID_GROUP'
          AND action = 'UPDATED' AND source = 'ORCHID_GROUP_CORRECTION'
          AND mutation_id IS NULL
          AND changed_fields = ARRAY['varietyId']::text[]
          AND before_data -> 'varietyId' = '60'::jsonb
          AND after_data -> 'varietyId' = '59'::jsonb) <> 1 THEN
        RAISE EXCEPTION 'V27 audit event identity or before/after payload mismatch';
    END IF;

    IF (SELECT count(*)
        FROM orchid_group_mutation_entries entry
        JOIN orchid_group_mutations mutation ON mutation.id = entry.mutation_id
        WHERE entry.id = 35 AND entry.mutation_id = 34 AND entry.orchid_group_id = 267
          AND entry.entry_kind = 'CREATE' AND entry.state_revision_after = 1
          AND mutation.mutation_type = 'CREATE'
          AND mutation.source_type = 'SYNTHETIC_FIRST_OBSERVED') <> 1
       OR (SELECT count(*)
        FROM orchid_group_mutation_entries entry
        JOIN orchid_group_mutations mutation ON mutation.id = entry.mutation_id
        WHERE entry.id = 113 AND entry.mutation_id = 109 AND entry.orchid_group_id = 256
          AND entry.state_revision_before = 1 AND entry.state_revision_after = 2
          AND entry.before_state -> 'quantity' = '259'::jsonb
          AND entry.after_state -> 'quantity' = '574'::jsonb
          AND mutation.mutation_type = 'CORRECTION'
          AND mutation.source_type = 'OPERATOR_ATTESTATION') <> 1
       OR (SELECT count(*)
        FROM orchid_group_mutation_entries entry
        JOIN orchid_group_mutations mutation ON mutation.id = entry.mutation_id
        WHERE entry.id = 15 AND entry.mutation_id = 15 AND entry.orchid_group_id = 252
          AND entry.state_revision_before = 1 AND entry.state_revision_after = 2
          AND entry.before_state -> 'quantity' = '100'::jsonb
          AND entry.after_state -> 'quantity' = '378'::jsonb
          AND mutation.mutation_type = 'CORRECTION'
          AND mutation.source_type = 'OPERATOR_ATTESTATION') <> 1
       OR (SELECT count(*)
        FROM orchid_group_mutation_entries entry
        JOIN orchid_group_mutations mutation ON mutation.id = entry.mutation_id
        WHERE entry.id = 144 AND entry.mutation_id = 137 AND entry.orchid_group_id = 266
          AND entry.state_revision_before = 1 AND entry.state_revision_after = 2
          AND entry.before_state -> 'varietyId' = '60'::jsonb
          AND entry.after_state -> 'varietyId' = '59'::jsonb
          AND entry.before_state ->> 'memo' = '10동에서 일부 이동'
          AND entry.after_state -> 'memo' = 'null'::jsonb
          AND mutation.mutation_type = 'CORRECTION'
          AND mutation.source_type = 'OPERATOR_ATTESTATION') <> 1 THEN
        RAISE EXCEPTION 'V27 existing Audit-to-Mutation target mismatch';
    END IF;

    IF (SELECT count(*)
        FROM orchid_group_mutation_entries entry
        JOIN orchid_group_mutations mutation ON mutation.id = entry.mutation_id
        WHERE entry.id = 222 AND entry.mutation_id = 203 AND entry.orchid_group_id = 272
          AND entry.entry_kind = 'CREATE' AND entry.role = 'RESULT'
          AND entry.state_revision_before IS NULL AND entry.state_revision_after = 1
          AND entry.before_state IS NULL
          AND entry.after_state = '{"memo":null,"genus":"카틀레야","status":"정상","ageYear":2,"quantity":810,"bedZoneId":12,"sortOrder":10,"trayCount":null,"varietyId":105,"endPosition":5.00,"potSizeCode":"POT_3_5","varietyName":"CA12","placementType":null,"startPosition":0.00,"inboundRecordId":null,"reservedQuantity":0,"splitPlacementAllowed":false}'::jsonb
          AND mutation.command_fingerprint = 'd4a1de89ba2d7a54d3579919c6c419621b08b368985afd64efc75e97daaaa1a8'
          AND mutation.source_domain = 'WORK' AND mutation.source_type = 'WORK_EFFECT'
          AND mutation.source_reference_id = '56'
          AND mutation.source_operation_key = 'EXECUTION:f66026e5-617c-40d9-9943-ad5e29497ab5') <> 1
       OR (SELECT count(*) FROM orchid_group_mutation_entries WHERE orchid_group_id = 272) <> 1
       OR (SELECT count(*) FROM orchid_groups
           WHERE id = 272 AND state_revision = 1 AND quantity = 810
             AND age_year = 2 AND pot_size_code = 'POT_3_5') <> 1 THEN
        RAISE EXCEPTION 'V27 group 272 Work CREATE or current state mismatch';
    END IF;

    IF (SELECT count(*) FROM work_applied_effects WHERE mutation_id IS NOT NULL) <> 42
       OR (SELECT count(*) FROM orchid_group_lineage WHERE mutation_id IS NOT NULL) <> 16
       OR (SELECT count(*) FROM work_applied_effects
           WHERE id = 50 AND work_operation_id = 56 AND mutation_id = 203
             AND effect_key = 'EXECUTION:f66026e5-617c-40d9-9943-ad5e29497ab5') <> 1
       OR (SELECT count(*) FROM orchid_group_lineage
           WHERE id IN (14, 15, 16) AND mutation_id = 203) <> 3 THEN
        RAISE EXCEPTION 'V27 Work or Lineage provenance precondition mismatch';
    END IF;

    UPDATE v27_repair_context SET apply_repair = TRUE;
END $$;

DO $$
DECLARE
    correction_mutation_id BIGINT;
    affected_rows BIGINT;
BEGIN
    IF NOT (SELECT apply_repair FROM v27_repair_context) THEN
        RETURN;
    END IF;

    SET CONSTRAINTS ALL DEFERRED;

    -- This is a reviewed, one-row historical rebuild. The Work source identity,
    -- Mutation ID, Entry ID, Work link and Lineage links remain unchanged.
    UPDATE orchid_group_mutations
    SET command_fingerprint = '55e3e08a1989d6946a1c010e5ff38ca639b4a68fa28314283fedfb129dd3dd69'
    WHERE id = 203
      AND command_fingerprint = 'd4a1de89ba2d7a54d3579919c6c419621b08b368985afd64efc75e97daaaa1a8';
    GET DIAGNOSTICS affected_rows = ROW_COUNT;
    IF affected_rows <> 1 THEN
        RAISE EXCEPTION 'V27 expected exactly one Work Mutation fingerprint rebuild';
    END IF;

    UPDATE orchid_group_mutation_entries
    SET after_state = '{"memo":null,"genus":"카틀레야","status":"정상","ageYear":null,"quantity":810,"bedZoneId":12,"sortOrder":10,"trayCount":null,"varietyId":105,"endPosition":5.00,"potSizeCode":"POT_2","varietyName":"CA12","placementType":null,"startPosition":0.00,"inboundRecordId":null,"reservedQuantity":0,"splitPlacementAllowed":false}'::jsonb
    WHERE id = 222 AND mutation_id = 203 AND orchid_group_id = 272;
    GET DIAGNOSTICS affected_rows = ROW_COUNT;
    IF affected_rows <> 1 THEN
        RAISE EXCEPTION 'V27 expected exactly one Work CREATE Entry rebuild';
    END IF;

    INSERT INTO orchid_group_mutations (
        mutation_type, source_domain, source_type, source_reference_id,
        source_operation_key, correlation_id, command_fingerprint,
        occurred_at, recorded_at, effective_business_date, reason, schema_version
    ) VALUES (
        'CORRECTION', 'MIGRATION', 'AUDIT_EVENT', '2',
        'ORCHID_GROUP_AUDIT:2', '00000000-0000-0000-0000-000000000002'::uuid,
        '0fa6c3bf52f49b8ef020734daac7605a9c44e1ca61afb3dd64558cd3046b0280',
        '2026-08-15 13:32:38.735725+00'::timestamptz, CURRENT_TIMESTAMP,
        DATE '2026-08-15',
        'AuditEvent 2가 입증한 OrchidGroup 272 년생·화분 크기 보정', 1
    ) RETURNING id INTO correction_mutation_id;

    INSERT INTO orchid_group_mutation_entries (
        mutation_id, orchid_group_id, entry_kind, role,
        state_revision_before, state_revision_after, before_state, after_state
    ) VALUES (
        correction_mutation_id, 272, 'CHANGE', 'AFFECTED', 1, 2,
        '{"memo":null,"genus":"카틀레야","status":"정상","ageYear":null,"quantity":810,"bedZoneId":12,"sortOrder":10,"trayCount":null,"varietyId":105,"endPosition":5.00,"potSizeCode":"POT_2","varietyName":"CA12","placementType":null,"startPosition":0.00,"inboundRecordId":null,"reservedQuantity":0,"splitPlacementAllowed":false}'::jsonb,
        '{"memo":null,"genus":"카틀레야","status":"정상","ageYear":2,"quantity":810,"bedZoneId":12,"sortOrder":10,"trayCount":null,"varietyId":105,"endPosition":5.00,"potSizeCode":"POT_3_5","varietyName":"CA12","placementType":null,"startPosition":0.00,"inboundRecordId":null,"reservedQuantity":0,"splitPlacementAllowed":false}'::jsonb
    );

    UPDATE audit_events
    SET mutation_id = CASE id
        WHEN 1 THEN 34
        WHEN 2 THEN correction_mutation_id
        WHEN 3 THEN 109
        WHEN 4 THEN 15
        WHEN 5 THEN 137
    END
    WHERE id IN (1, 2, 3, 4, 5) AND mutation_id IS NULL;
    GET DIAGNOSTICS affected_rows = ROW_COUNT;
    IF affected_rows <> 5 THEN
        RAISE EXCEPTION 'V27 expected exactly five Audit-to-Mutation links';
    END IF;

    PERFORM set_config('greenhouse.orchid_group_mutation',
        'MUTATION:' || correction_mutation_id, TRUE);
    UPDATE orchid_groups
    SET state_revision = 2
    WHERE id = 272 AND state_revision = 1;
    GET DIAGNOSTICS affected_rows = ROW_COUNT;
    IF affected_rows <> 1 THEN
        RAISE EXCEPTION 'V27 expected exactly one current revision update for OrchidGroup 272';
    END IF;

    UPDATE v27_repair_context
    SET audit_correction_mutation_id = correction_mutation_id;
END $$;

DO $$
DECLARE
    correction_mutation_id BIGINT;
BEGIN
    IF NOT (SELECT apply_repair FROM v27_repair_context) THEN
        RETURN;
    END IF;
    SELECT audit_correction_mutation_id INTO correction_mutation_id
    FROM v27_repair_context;

    IF (SELECT count(*) FROM orchid_group_mutations) <> 315
       OR (SELECT count(*) FROM orchid_group_mutation_entries) <> 349
       OR (SELECT count(*) FROM orchid_groups) <> 269
       OR (SELECT count(*) FROM audit_events WHERE mutation_id IS NOT NULL) <> 5 THEN
        RAISE EXCEPTION 'V27 postcondition row counts do not match';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM v27_orchid_group_snapshot_before before_row
        FULL JOIN (
            SELECT id, jsonb_build_object(
                'quantity', quantity, 'reservedQuantity', reserved_quantity,
                'status', status, 'bedZoneId', bed_zone_id, 'sortOrder', sort_order,
                'startPosition', start_position, 'endPosition', end_position,
                'varietyId', variety_id, 'genus', genus, 'varietyName', variety_name,
                'ageYear', age_year, 'potSizeCode', pot_size_code,
                'placementType', placement_type, 'trayCount', tray_count,
                'splitPlacementAllowed', split_placement_allowed,
                'inboundRecordId', inbound_record_id, 'memo', memo
            ) AS snapshot
            FROM orchid_groups
        ) after_row USING (id)
        WHERE before_row.id IS NULL OR after_row.id IS NULL
           OR before_row.snapshot IS DISTINCT FROM after_row.snapshot
    ) THEN
        RAISE EXCEPTION 'V27 changed an OrchidGroup canonical business snapshot';
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
        RAISE EXCEPTION 'V27 produced a revision or snapshot chain discontinuity';
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
        WHERE latest.state_revision_after IS NULL
           OR current_group.state_revision IS DISTINCT FROM latest.state_revision_after
           OR latest.after_state IS NULL
    ) THEN
        RAISE EXCEPTION 'V27 current OrchidGroup revision does not match its last Entry';
    END IF;

    IF EXISTS (
        SELECT * FROM v27_work_links_before
        EXCEPT SELECT id, mutation_id, correlation_id FROM work_applied_effects
              WHERE mutation_id IS NOT NULL OR correlation_id IS NOT NULL
    ) OR EXISTS (
        SELECT id, mutation_id, correlation_id FROM work_applied_effects
        WHERE mutation_id IS NOT NULL OR correlation_id IS NOT NULL
        EXCEPT SELECT * FROM v27_work_links_before
    ) OR EXISTS (
        SELECT * FROM v27_lineage_links_before
        EXCEPT SELECT id, mutation_id FROM orchid_group_lineage WHERE mutation_id IS NOT NULL
    ) OR EXISTS (
        SELECT id, mutation_id FROM orchid_group_lineage WHERE mutation_id IS NOT NULL
        EXCEPT SELECT * FROM v27_lineage_links_before
    ) THEN
        RAISE EXCEPTION 'V27 changed an existing Work or Lineage link';
    END IF;

    IF EXISTS (
        SELECT 1 FROM v27_mutations_before old
        LEFT JOIN orchid_group_mutations current USING (id)
        WHERE current.id IS NULL
           OR (old.id <> 203 AND old.payload IS DISTINCT FROM to_jsonb(current))
           OR (old.id = 203 AND old.payload - 'command_fingerprint'
                  IS DISTINCT FROM to_jsonb(current) - 'command_fingerprint')
    ) OR (SELECT count(*) FROM orchid_group_mutations current
          LEFT JOIN v27_mutations_before old USING (id) WHERE old.id IS NULL) <> 1 THEN
        RAISE EXCEPTION 'V27 changed an unexpected Mutation';
    END IF;

    IF EXISTS (
        SELECT 1 FROM v27_entries_before old
        LEFT JOIN orchid_group_mutation_entries current USING (id)
        WHERE current.id IS NULL
           OR (old.id <> 222 AND old.payload IS DISTINCT FROM to_jsonb(current))
           OR (old.id = 222 AND old.payload - 'after_state'
                  IS DISTINCT FROM to_jsonb(current) - 'after_state')
    ) OR (SELECT count(*) FROM orchid_group_mutation_entries current
          LEFT JOIN v27_entries_before old USING (id) WHERE old.id IS NULL) <> 1 THEN
        RAISE EXCEPTION 'V27 changed an unexpected MutationEntry';
    END IF;

    IF (SELECT count(*) FROM audit_events
        WHERE (id, mutation_id) IN ((1, 34), (2, correction_mutation_id),
                                    (3, 109), (4, 15), (5, 137))) <> 5
       OR (SELECT count(*) FROM orchid_group_mutation_entries
           WHERE mutation_id = correction_mutation_id AND orchid_group_id = 272
             AND state_revision_before = 1 AND state_revision_after = 2
             AND before_state -> 'ageYear' = 'null'::jsonb
             AND before_state ->> 'potSizeCode' = 'POT_2'
             AND after_state -> 'ageYear' = '2'::jsonb
             AND after_state ->> 'potSizeCode' = 'POT_3_5') <> 1 THEN
        RAISE EXCEPTION 'V27 Audit links or group 272 correction snapshots are incorrect';
    END IF;

    IF (SELECT count(*) FROM orchid_groups WHERE state_revision = 0) <> 101
       OR (SELECT count(*) FROM orchid_groups WHERE state_revision = 1) <> 122
       OR (SELECT count(*) FROM orchid_groups WHERE state_revision = 2) <> 37
       OR (SELECT count(*) FROM orchid_groups WHERE state_revision = 3) <> 9 THEN
        RAISE EXCEPTION 'V27 postcondition revision distribution mismatch';
    END IF;
END $$;
