DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM orchid_group_mutation_entries
        WHERE entry_kind = 'HISTORICAL'
    ) THEN
        RAISE EXCEPTION 'HISTORICAL Entry를 complete state-chain으로 이관한 뒤 V28을 적용해야 합니다.';
    END IF;
END;
$$;

DROP INDEX IF EXISTS idx_orchid_group_mutation_entries_migration_run;

ALTER TABLE orchid_group_mutation_entries
    DROP CONSTRAINT ck_orchid_group_mutation_entry_kind,
    DROP CONSTRAINT ck_orchid_group_mutation_entry_revision,
    DROP CONSTRAINT orchid_group_mutation_entries_orchid_group_id_fkey,
    DROP CONSTRAINT orchid_group_mutation_entries_migration_run_id_fkey,
    DROP COLUMN migration_run_id,
    ALTER COLUMN state_revision_after SET NOT NULL,
    ALTER COLUMN after_state DROP NOT NULL,
    ADD CONSTRAINT ck_orchid_group_mutation_entry_kind
        CHECK (entry_kind IN ('BASELINE', 'CREATE', 'CHANGE', 'DELETE')),
    ADD CONSTRAINT ck_orchid_group_mutation_entry_revision
        CHECK (
            (entry_kind = 'BASELINE'
                AND state_revision_before IS NULL
                AND state_revision_after = 0
                AND before_state IS NULL
                AND after_state IS NOT NULL)
            OR
            (entry_kind = 'CREATE'
                AND state_revision_before IS NULL
                AND state_revision_after = 1
                AND before_state IS NULL
                AND after_state IS NOT NULL)
            OR
            (entry_kind = 'CHANGE'
                AND state_revision_before >= 0
                AND state_revision_after = state_revision_before + 1
                AND before_state IS NOT NULL
                AND after_state IS NOT NULL)
            OR
            (entry_kind = 'DELETE'
                AND state_revision_before >= 0
                AND state_revision_after = state_revision_before + 1
                AND before_state IS NOT NULL
                AND after_state IS NULL)
        );

DROP TABLE orchid_group_history_migration_runs;
DROP SEQUENCE IF EXISTS orchid_group_history_migration_runs_id_seq;

ALTER TABLE orchid_group_ledger_coverages
    ADD COLUMN import_fingerprint VARCHAR(64),
    ADD CONSTRAINT ck_orchid_group_ledger_coverage_import_fingerprint
        CHECK (import_fingerprint IS NULL OR import_fingerprint ~ '^[0-9a-f]{64}$');
