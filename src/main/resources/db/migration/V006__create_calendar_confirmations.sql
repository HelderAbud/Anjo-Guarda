CREATE TABLE calendar_confirmations (
    id                    UUID PRIMARY KEY,
    child_id              UUID        NOT NULL REFERENCES children (id),
    date                  DATE        NOT NULL,
    status                VARCHAR(32) NOT NULL,
    realized_guardian_id  UUID        REFERENCES guardians (id),
    realized_start_time   TIME,
    realized_end_time     TIME,
    note                  VARCHAR(500),
    created_by            UUID        NOT NULL REFERENCES users (id),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_calendar_confirmations_child_date UNIQUE (child_id, date),
    CONSTRAINT ck_calendar_confirmations_status CHECK (
        status IN ('REALIZADO', 'ALTERADO', 'NAO_REALIZADO')
    ),
    CONSTRAINT ck_calendar_confirmations_times CHECK (
        realized_end_time IS NULL
        OR realized_start_time IS NULL
        OR realized_end_time >= realized_start_time
    )
);

CREATE INDEX idx_calendar_confirmations_child_date
    ON calendar_confirmations (child_id, date);
