CREATE TABLE audit_events (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    occurred_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    actor_id            VARCHAR(100),
    session_id          VARCHAR(200),
    client_instance_id  VARCHAR(100),
    request_id          VARCHAR(100),
    action              VARCHAR(100) NOT NULL,
    source              VARCHAR(100) NOT NULL,
    entity_type         VARCHAR(100) NOT NULL,
    entity_id           BIGINT NOT NULL,
    house_id            BIGINT,
    physical_bed_id     BIGINT,
    zone_id             BIGINT,
    variety_id          BIGINT,
    changed_fields      TEXT[] NOT NULL,
    before_data         JSONB,
    after_data          JSONB,
    context_data        JSONB
);

CREATE INDEX idx_audit_events_occurred_at ON audit_events (occurred_at DESC);
CREATE INDEX idx_audit_events_entity ON audit_events (entity_type, entity_id, occurred_at DESC);
CREATE INDEX idx_audit_events_actor ON audit_events (actor_id, occurred_at DESC);
CREATE INDEX idx_audit_events_bed_variety ON audit_events (physical_bed_id, variety_id, occurred_at DESC);
CREATE INDEX idx_audit_events_source ON audit_events (source, occurred_at DESC);
