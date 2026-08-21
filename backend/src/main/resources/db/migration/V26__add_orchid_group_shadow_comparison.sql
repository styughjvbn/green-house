CREATE SEQUENCE orchid_group_shadow_comparisons_id_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE orchid_group_shadow_comparisons (
    id BIGINT PRIMARY KEY DEFAULT nextval('orchid_group_shadow_comparisons_id_seq'),
    source_domain VARCHAR(30) NOT NULL,
    source_type VARCHAR(50) NOT NULL,
    source_reference_id VARCHAR(100) NOT NULL,
    source_operation_key VARCHAR(200) NOT NULL,
    correlation_id UUID NOT NULL,
    mutation_type VARCHAR(50) NOT NULL,
    command_fingerprint VARCHAR(64) NOT NULL,
    status VARCHAR(30) NOT NULL,
    writer_version VARCHAR(50) NOT NULL,
    command_payload JSONB NOT NULL,
    expected_entries JSONB NOT NULL,
    actual_entries JSONB NOT NULL,
    mismatches JSONB NOT NULL,
    engine_error TEXT,
    recorded_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_orchid_group_shadow_comparison_source UNIQUE (
        source_domain,
        source_type,
        source_reference_id,
        source_operation_key
    ),
    CONSTRAINT ck_orchid_group_shadow_fingerprint
        CHECK (command_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_orchid_group_shadow_status
        CHECK (status IN ('MATCHED', 'MISMATCHED', 'ENGINE_REJECTED'))
);

CREATE INDEX idx_orchid_group_shadow_comparisons_status_recorded
    ON orchid_group_shadow_comparisons (status, recorded_at DESC);
