CREATE TABLE orchid_group_lineage (
    id BIGSERIAL PRIMARY KEY,
    source_orchid_group_id BIGINT NOT NULL REFERENCES orchid_groups(id),
    result_orchid_group_id BIGINT NOT NULL REFERENCES orchid_groups(id),
    relation_type VARCHAR(30) NOT NULL,
    work_operation_id BIGINT NOT NULL REFERENCES work_operations(id),
    source_quantity INTEGER NOT NULL CHECK (source_quantity > 0),
    result_quantity INTEGER NOT NULL CHECK (result_quantity > 0),
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT ck_orchid_group_lineage_distinct_groups
        CHECK (source_orchid_group_id <> result_orchid_group_id),
    CONSTRAINT uk_orchid_group_lineage_operation_groups_relation
        UNIQUE (work_operation_id, source_orchid_group_id, result_orchid_group_id, relation_type)
);

CREATE INDEX idx_orchid_group_lineage_source
    ON orchid_group_lineage(source_orchid_group_id, created_at);

CREATE INDEX idx_orchid_group_lineage_result
    ON orchid_group_lineage(result_orchid_group_id, created_at);
