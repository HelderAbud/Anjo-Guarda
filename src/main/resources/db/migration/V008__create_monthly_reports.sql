CREATE TABLE monthly_reports (
    id                UUID PRIMARY KEY,
    child_id          UUID        NOT NULL REFERENCES children (id),
    year              INT         NOT NULL,
    month             INT         NOT NULL,
    version           INT         NOT NULL,
    generated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    content_snapshot  JSONB       NOT NULL,
    CONSTRAINT uq_monthly_reports_version UNIQUE (child_id, year, month, version),
    CONSTRAINT ck_monthly_reports_month CHECK (month BETWEEN 1 AND 12),
    CONSTRAINT ck_monthly_reports_version CHECK (version >= 1)
);

CREATE INDEX idx_monthly_reports_child_period
    ON monthly_reports (child_id, year, month, version);
