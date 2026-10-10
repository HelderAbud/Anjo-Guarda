CREATE TABLE documents (
    id           UUID PRIMARY KEY,
    child_id     UUID         NOT NULL REFERENCES children (id),
    category     VARCHAR(40)  NOT NULL,
    filename     VARCHAR(255) NOT NULL,
    storage_key  VARCHAR(80)  NOT NULL,
    mime_type    VARCHAR(100) NOT NULL,
    size         BIGINT       NOT NULL,
    created_by   UUID         NOT NULL REFERENCES users (id),
    CONSTRAINT uq_documents_storage_key UNIQUE (storage_key),
    CONSTRAINT ck_documents_category CHECK (char_length(btrim(category)) BETWEEN 1 AND 40),
    CONSTRAINT ck_documents_filename CHECK (
        char_length(btrim(filename)) > 0
        AND filename !~ '[\\/]'
    ),
    CONSTRAINT ck_documents_size CHECK (size > 0 AND size <= 10485760),
    CONSTRAINT ck_documents_mime CHECK (
        mime_type IN ('application/pdf', 'image/jpeg', 'image/png', 'image/webp')
    )
);

CREATE INDEX idx_documents_child ON documents (child_id);
