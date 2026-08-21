ALTER TABLE orchid_group_mutations
    ADD COLUMN occurred_at TIMESTAMPTZ;

UPDATE orchid_group_mutations
SET occurred_at = recorded_at
WHERE occurred_at IS NULL;

ALTER TABLE orchid_group_mutations
    ALTER COLUMN occurred_at SET DEFAULT CURRENT_TIMESTAMP,
    ALTER COLUMN occurred_at SET NOT NULL;

CREATE INDEX idx_orchid_group_mutations_occurred_at
    ON orchid_group_mutations(occurred_at, id);

CREATE SEQUENCE orchid_group_history_migration_runs_id_seq
    START WITH 1 INCREMENT BY 50 NO MINVALUE NO MAXVALUE CACHE 1;

CREATE TABLE orchid_group_history_migration_runs (
    id BIGINT PRIMARY KEY DEFAULT nextval('orchid_group_history_migration_runs_id_seq'),
    run_key UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    source_cutoff TIMESTAMPTZ NOT NULL,
    backup_fingerprint VARCHAR(64) NOT NULL,
    manifest_fingerprint VARCHAR(64) NOT NULL,
    source_state_fingerprint VARCHAR(64) NOT NULL,
    effective_business_date DATE NOT NULL,
    source_counts JSONB NOT NULL,
    planned_counts JSONB NOT NULL,
    imported_counts JSONB,
    verification_result JSONB,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_orchid_group_history_migration_run_key UNIQUE (run_key),
    CONSTRAINT ck_orchid_group_history_migration_status
        CHECK (status IN ('DRY_RUN', 'PREPARING', 'IMPORTED', 'VERIFIED', 'FAILED')),
    CONSTRAINT ck_orchid_group_history_migration_backup_fingerprint
        CHECK (backup_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_orchid_group_history_migration_manifest_fingerprint
        CHECK (manifest_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_orchid_group_history_migration_state_fingerprint
        CHECK (source_state_fingerprint ~ '^[0-9a-f]{64}$')
);

ALTER SEQUENCE orchid_group_history_migration_runs_id_seq
    OWNED BY orchid_group_history_migration_runs.id;

CREATE INDEX idx_orchid_group_history_migration_runs_status
    ON orchid_group_history_migration_runs(status, id);

ALTER TABLE orchid_group_mutation_entries
    DROP CONSTRAINT ck_orchid_group_mutation_entry_kind,
    DROP CONSTRAINT ck_orchid_group_mutation_entry_revision,
    ALTER COLUMN state_revision_after DROP NOT NULL,
    ALTER COLUMN after_state DROP NOT NULL,
    ADD COLUMN migration_run_id BIGINT
        REFERENCES orchid_group_history_migration_runs(id),
    ADD CONSTRAINT ck_orchid_group_mutation_entry_kind
        CHECK (entry_kind IN ('BASELINE', 'CREATE', 'CHANGE', 'HISTORICAL')),
    ADD CONSTRAINT ck_orchid_group_mutation_entry_revision
        CHECK (
            (entry_kind = 'BASELINE'
                AND state_revision_before IS NULL
                AND state_revision_after = 0
                AND before_state IS NULL
                AND after_state IS NOT NULL
                AND migration_run_id IS NULL)
            OR
            (entry_kind = 'CREATE'
                AND state_revision_before IS NULL
                AND state_revision_after = 1
                AND before_state IS NULL
                AND after_state IS NOT NULL
                AND migration_run_id IS NULL)
            OR
            (entry_kind = 'CHANGE'
                AND state_revision_before >= 0
                AND state_revision_after = state_revision_before + 1
                AND before_state IS NOT NULL
                AND after_state IS NOT NULL
                AND migration_run_id IS NULL)
            OR
            (entry_kind = 'HISTORICAL'
                AND state_revision_before IS NULL
                AND state_revision_after IS NULL
                AND before_state IS NULL
                AND after_state IS NULL
                AND migration_run_id IS NOT NULL)
        );

CREATE INDEX idx_orchid_group_mutation_entries_migration_run
    ON orchid_group_mutation_entries(migration_run_id, id)
    WHERE migration_run_id IS NOT NULL;

CREATE OR REPLACE FUNCTION verify_orchid_group_ledger_entry()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM orchid_group_ledger_coverages coverage
         WHERE coverage.status = 'ACTIVE'
    ) AND NOT EXISTS (
        SELECT 1
          FROM orchid_group_mutation_entries entry
         WHERE entry.orchid_group_id = NEW.id
           AND entry.state_revision_after = NEW.state_revision
           AND entry.entry_kind IN ('CREATE', 'CHANGE')
    ) THEN
        RAISE EXCEPTION 'OrchidGroup revision에 대응하는 MutationEntry가 없습니다.'
            USING ERRCODE = '23514';
    END IF;
    RETURN NULL;
END;
$$;
