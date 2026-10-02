CREATE TABLE calendar_exceptions (
    id                     UUID PRIMARY KEY,
    child_id               UUID         NOT NULL REFERENCES children (id),
    date                   DATE         NOT NULL,
    reason                 VARCHAR(500),
    original_guardian_id   UUID         REFERENCES guardians (id),
    new_guardian_id        UUID         NOT NULL REFERENCES guardians (id),
    created_by             UUID         NOT NULL REFERENCES users (id),
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_calendar_exceptions_child_date UNIQUE (child_id, date)
);

CREATE INDEX idx_calendar_exceptions_child_date
    ON calendar_exceptions (child_id, date);

CREATE TABLE audit_logs (
    id           UUID PRIMARY KEY,
    user_id      UUID         NOT NULL REFERENCES users (id),
    action       VARCHAR(64)  NOT NULL,
    entity_type  VARCHAR(64)  NOT NULL,
    entity_id    UUID         NOT NULL,
    metadata     JSONB,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_logs_entity
    ON audit_logs (entity_type, entity_id);
