CREATE TABLE custody_plans (
    id         UUID PRIMARY KEY,
    child_id   UUID         NOT NULL REFERENCES children (id),
    name       VARCHAR(255) NOT NULL,
    start_date DATE         NOT NULL,
    end_date   DATE,
    active     BOOLEAN      NOT NULL DEFAULT true,
    CONSTRAINT ck_custody_plans_period CHECK (end_date IS NULL OR end_date >= start_date)
);

CREATE UNIQUE INDEX uq_custody_plans_active_child
    ON custody_plans (child_id)
    WHERE active;

CREATE TABLE schedule_rules (
    id                   UUID PRIMARY KEY,
    plan_id              UUID         NOT NULL REFERENCES custody_plans (id),
    rule_type            VARCHAR(32)  NOT NULL,
    configuration_json   JSONB        NOT NULL,
    start_date           DATE         NOT NULL,
    end_date             DATE,
    CONSTRAINT ck_schedule_rules_type CHECK (
        rule_type IN ('ALTERNATE_DAYS', 'WEEKENDS', 'ALTERNATE_WEEKS', 'CUSTOM')
    ),
    CONSTRAINT ck_schedule_rules_period CHECK (end_date IS NULL OR end_date >= start_date)
);
