ALTER TABLE orchid_groups
    ADD COLUMN state_revision BIGINT;

ALTER TABLE orchid_groups
    ADD CONSTRAINT ck_orchid_groups_state_revision
        CHECK (state_revision IS NULL OR state_revision >= 0) NOT VALID;

CREATE SEQUENCE orchid_group_mutations_id_seq
    START WITH 1 INCREMENT BY 50 NO MINVALUE NO MAXVALUE CACHE 1;

CREATE TABLE orchid_group_mutations (
    id BIGINT PRIMARY KEY DEFAULT nextval('orchid_group_mutations_id_seq'),
    mutation_type VARCHAR(50) NOT NULL,
    source_domain VARCHAR(30) NOT NULL,
    source_type VARCHAR(50) NOT NULL,
    source_reference_id VARCHAR(100) NOT NULL,
    source_operation_key VARCHAR(200) NOT NULL,
    correlation_id UUID NOT NULL,
    command_fingerprint VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    recorded_at TIMESTAMPTZ NOT NULL,
    effective_business_date DATE NOT NULL,
    reason TEXT,
    schema_version INTEGER NOT NULL,
    CONSTRAINT uk_orchid_group_mutation_source
        UNIQUE (source_domain, source_type, source_reference_id, source_operation_key),
    CONSTRAINT ck_orchid_group_mutation_schema_version CHECK (schema_version > 0),
    CONSTRAINT ck_orchid_group_mutation_fingerprint
        CHECK (command_fingerprint ~ '^[0-9a-f]{64}$')
);

ALTER SEQUENCE orchid_group_mutations_id_seq
    OWNED BY orchid_group_mutations.id;

CREATE INDEX idx_orchid_group_mutations_correlation
    ON orchid_group_mutations(correlation_id, id);

CREATE INDEX idx_orchid_group_mutations_occurred_at
    ON orchid_group_mutations(occurred_at, id);

CREATE INDEX idx_orchid_group_mutations_recorded_at
    ON orchid_group_mutations(recorded_at, id);

CREATE SEQUENCE orchid_group_mutation_entries_id_seq
    START WITH 1 INCREMENT BY 50 NO MINVALUE NO MAXVALUE CACHE 1;

CREATE TABLE orchid_group_mutation_entries (
    id BIGINT PRIMARY KEY DEFAULT nextval('orchid_group_mutation_entries_id_seq'),
    mutation_id BIGINT NOT NULL REFERENCES orchid_group_mutations(id),
    orchid_group_id BIGINT NOT NULL,
    entry_kind VARCHAR(20) NOT NULL,
    role VARCHAR(20) NOT NULL,
    state_revision_before BIGINT,
    state_revision_after BIGINT NOT NULL,
    before_state JSONB,
    after_state JSONB,
    CONSTRAINT uk_orchid_group_mutation_entry_group
        UNIQUE (mutation_id, orchid_group_id),
    CONSTRAINT uk_orchid_group_mutation_entry_revision
        UNIQUE (orchid_group_id, state_revision_after),
    CONSTRAINT ck_orchid_group_mutation_entry_kind
        CHECK (entry_kind IN ('BASELINE', 'CREATE', 'CHANGE', 'DELETE')),
    CONSTRAINT ck_orchid_group_mutation_entry_role
        CHECK (role IN ('SOURCE', 'RESULT', 'AFFECTED')),
    CONSTRAINT ck_orchid_group_mutation_entry_revision
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
        )
);

ALTER SEQUENCE orchid_group_mutation_entries_id_seq
    OWNED BY orchid_group_mutation_entries.id;

CREATE INDEX idx_orchid_group_mutation_entries_group
    ON orchid_group_mutation_entries(orchid_group_id, state_revision_after DESC);

CREATE SEQUENCE orchid_group_mutation_relations_id_seq
    START WITH 1 INCREMENT BY 50 NO MINVALUE NO MAXVALUE CACHE 1;

CREATE TABLE orchid_group_mutation_relations (
    id BIGINT PRIMARY KEY DEFAULT nextval('orchid_group_mutation_relations_id_seq'),
    mutation_id BIGINT NOT NULL REFERENCES orchid_group_mutations(id),
    related_mutation_id BIGINT NOT NULL REFERENCES orchid_group_mutations(id),
    relation_type VARCHAR(20) NOT NULL,
    CONSTRAINT uk_orchid_group_mutation_relation
        UNIQUE (mutation_id, related_mutation_id, relation_type),
    CONSTRAINT ck_orchid_group_mutation_relation_distinct
        CHECK (mutation_id <> related_mutation_id),
    CONSTRAINT ck_orchid_group_mutation_relation_type
        CHECK (relation_type IN ('CORRECTS', 'COMPENSATES', 'SUPERSEDES'))
);

ALTER SEQUENCE orchid_group_mutation_relations_id_seq
    OWNED BY orchid_group_mutation_relations.id;

CREATE INDEX idx_orchid_group_mutation_relations_related
    ON orchid_group_mutation_relations(related_mutation_id, mutation_id);

CREATE SEQUENCE orchid_group_ledger_coverages_id_seq
    START WITH 1 INCREMENT BY 50 NO MINVALUE NO MAXVALUE CACHE 1;

CREATE TABLE orchid_group_ledger_coverages (
    id BIGINT PRIMARY KEY DEFAULT nextval('orchid_group_ledger_coverages_id_seq'),
    cutover_key UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    engine_schema_version INTEGER NOT NULL,
    snapshot_schema_version INTEGER NOT NULL,
    baseline_started_at TIMESTAMPTZ,
    baseline_completed_at TIMESTAMPTZ,
    effective_business_date DATE NOT NULL,
    baseline_group_count BIGINT,
    baseline_fingerprint VARCHAR(64),
    import_fingerprint VARCHAR(64),
    minimum_writer_version VARCHAR(50) NOT NULL,
    CONSTRAINT uk_orchid_group_ledger_coverage_cutover UNIQUE (cutover_key),
    CONSTRAINT ck_orchid_group_ledger_coverage_status
        CHECK (status IN ('PREPARING', 'ACTIVE', 'FAILED')),
    CONSTRAINT ck_orchid_group_ledger_coverage_versions
        CHECK (engine_schema_version > 0 AND snapshot_schema_version > 0),
    CONSTRAINT ck_orchid_group_ledger_coverage_count
        CHECK (baseline_group_count IS NULL OR baseline_group_count >= 0),
    CONSTRAINT ck_orchid_group_ledger_coverage_fingerprint
        CHECK (baseline_fingerprint IS NULL OR baseline_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_orchid_group_ledger_coverage_import_fingerprint
        CHECK (import_fingerprint IS NULL OR import_fingerprint ~ '^[0-9a-f]{64}$')
);

ALTER SEQUENCE orchid_group_ledger_coverages_id_seq
    OWNED BY orchid_group_ledger_coverages.id;

CREATE UNIQUE INDEX uk_orchid_group_ledger_coverage_open
    ON orchid_group_ledger_coverages ((1))
    WHERE status IN ('PREPARING', 'ACTIVE');

ALTER TABLE work_applied_effects
    ADD COLUMN mutation_id BIGINT REFERENCES orchid_group_mutations(id),
    ADD COLUMN correlation_id UUID;

CREATE INDEX idx_work_applied_effects_mutation
    ON work_applied_effects(mutation_id)
    WHERE mutation_id IS NOT NULL;

ALTER TABLE sales_inventory_movements
    ADD COLUMN mutation_id BIGINT REFERENCES orchid_group_mutations(id),
    ADD COLUMN correlation_id UUID;

CREATE INDEX idx_sales_inventory_movements_mutation
    ON sales_inventory_movements(mutation_id)
    WHERE mutation_id IS NOT NULL;

ALTER TABLE orchid_group_lineage
    ADD COLUMN mutation_id BIGINT REFERENCES orchid_group_mutations(id);

CREATE INDEX idx_orchid_group_lineage_mutation
    ON orchid_group_lineage(mutation_id)
    WHERE mutation_id IS NOT NULL;
