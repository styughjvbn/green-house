CREATE TABLE orchid_group_collections (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    purpose VARCHAR(200),
    status VARCHAR(20) NOT NULL,
    created_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE orchid_group_collection_members (
    id BIGSERIAL PRIMARY KEY,
    collection_id BIGINT NOT NULL REFERENCES orchid_group_collections(id),
    orchid_group_id BIGINT NOT NULL REFERENCES orchid_groups(id),
    joined_at TIMESTAMP NOT NULL,
    removed_at TIMESTAMP,
    created_by VARCHAR(100)
);

CREATE UNIQUE INDEX uk_orchid_collection_active_member
    ON orchid_group_collection_members(collection_id, orchid_group_id)
    WHERE removed_at IS NULL;

CREATE INDEX idx_orchid_collection_members_orchid_group
    ON orchid_group_collection_members(orchid_group_id, collection_id)
    WHERE removed_at IS NULL;
