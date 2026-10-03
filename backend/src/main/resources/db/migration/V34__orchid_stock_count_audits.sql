-- Consolidated from V43__orchid_stock_count_audits.sql: preserve this stage's SQL order.
CREATE TABLE orchid_stock_counts (
    request_key VARCHAR(100) PRIMARY KEY,
    request_fingerprint VARCHAR(64) NOT NULL,
    orchid_group_id BIGINT NOT NULL REFERENCES orchid_groups(id),
    recorded_at TIMESTAMP NOT NULL,
    business_date DATE NOT NULL,
    worker VARCHAR(100),
    reason VARCHAR(1000) NOT NULL,
    memo VARCHAR(1000),
    before_quantity INTEGER,
    actual_quantity INTEGER,
    mutation_id BIGINT REFERENCES orchid_group_mutations(id),
    CONSTRAINT ck_stock_count_completion CHECK
      ((before_quantity IS NULL AND actual_quantity IS NULL AND mutation_id IS NULL)
       OR (before_quantity IS NOT NULL AND actual_quantity IS NOT NULL
           AND before_quantity >= 0 AND actual_quantity >= 0 AND mutation_id IS NOT NULL)),
    CONSTRAINT uq_stock_count_mutation UNIQUE (mutation_id)
);
CREATE INDEX idx_stock_count_group_time ON orchid_stock_counts(orchid_group_id, recorded_at DESC);
