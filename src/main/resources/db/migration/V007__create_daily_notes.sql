CREATE TABLE daily_notes (
    id          UUID PRIMARY KEY,
    child_id    UUID         NOT NULL REFERENCES children (id),
    date        DATE         NOT NULL,
    category    VARCHAR(32)  NOT NULL,
    text        TEXT         NOT NULL,
    created_by  UUID         NOT NULL REFERENCES users (id),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ,
    CONSTRAINT ck_daily_notes_category CHECK (
        category IN (
            'ROTINA',
            'ESCOLA',
            'ATIVIDADE',
            'SAUDE',
            'VIAGEM',
            'TROCA_DE_HORARIO',
            'OUTRO'
        )
    ),
    CONSTRAINT ck_daily_notes_text CHECK (char_length(btrim(text)) > 0)
);

CREATE UNIQUE INDEX uq_daily_notes_active_child_date
    ON daily_notes (child_id, date)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_daily_notes_child_date
    ON daily_notes (child_id, date);
