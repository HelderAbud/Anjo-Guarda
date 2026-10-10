CREATE TABLE imports (
    id              UUID PRIMARY KEY,
    child_id        UUID         NOT NULL REFERENCES children (id),
    filename        VARCHAR(255) NOT NULL,
    storage_key     VARCHAR(80)  NOT NULL,
    source_type     VARCHAR(8)   NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    extracted_text  TEXT         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_imports_storage_key UNIQUE (storage_key),
    CONSTRAINT ck_imports_filename CHECK (
        char_length(btrim(filename)) > 0
        AND filename !~ '[\\/]'
    ),
    CONSTRAINT ck_imports_source_type CHECK (source_type IN ('PDF', 'DOCX')),
    CONSTRAINT ck_imports_status CHECK (status = 'EXTRAIDO')
);

CREATE INDEX idx_imports_child ON imports (child_id);
