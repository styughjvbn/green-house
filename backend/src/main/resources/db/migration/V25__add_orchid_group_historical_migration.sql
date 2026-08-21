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

CREATE SEQUENCE orchid_group_historical_evidence_id_seq
    START WITH 1 INCREMENT BY 50 NO MINVALUE NO MAXVALUE CACHE 1;

CREATE TABLE orchid_group_historical_evidence (
    id BIGINT PRIMARY KEY DEFAULT nextval('orchid_group_historical_evidence_id_seq'),
    migration_run_id BIGINT NOT NULL REFERENCES orchid_group_history_migration_runs(id),
    mutation_id BIGINT NOT NULL REFERENCES orchid_group_mutations(id),
    orchid_group_id BIGINT NOT NULL REFERENCES orchid_groups(id),
    role VARCHAR(20) NOT NULL,
    evidence_kind VARCHAR(20) NOT NULL,
    evidence_quality VARCHAR(20) NOT NULL,
    known_fields TEXT[] NOT NULL,
    before_fragment JSONB,
    after_fragment JSONB,
    change_set JSONB,
    source_payload_fingerprint VARCHAR(64) NOT NULL,
    CONSTRAINT uk_orchid_group_historical_evidence_group
        UNIQUE (mutation_id, orchid_group_id),
    CONSTRAINT ck_orchid_group_historical_evidence_role
        CHECK (role IN ('SOURCE', 'RESULT', 'AFFECTED')),
    CONSTRAINT ck_orchid_group_historical_evidence_kind
        CHECK (evidence_kind IN ('ORIGIN', 'CREATE', 'CHANGE', 'GAP')),
    CONSTRAINT ck_orchid_group_historical_evidence_quality
        CHECK (evidence_quality IN ('VERIFIED', 'DERIVED', 'ATTESTED', 'GAP')),
    CONSTRAINT ck_orchid_group_historical_evidence_gap
        CHECK ((evidence_kind = 'GAP') = (evidence_quality = 'GAP')),
    CONSTRAINT ck_orchid_group_historical_evidence_fingerprint
        CHECK (source_payload_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_orchid_group_historical_evidence_content
        CHECK (
            before_fragment IS NOT NULL
            OR after_fragment IS NOT NULL
            OR change_set IS NOT NULL
        )
);

ALTER SEQUENCE orchid_group_historical_evidence_id_seq
    OWNED BY orchid_group_historical_evidence.id;

CREATE INDEX idx_orchid_group_historical_evidence_group
    ON orchid_group_historical_evidence(orchid_group_id, mutation_id);

CREATE INDEX idx_orchid_group_historical_evidence_run
    ON orchid_group_historical_evidence(migration_run_id, id);
