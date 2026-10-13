ALTER TABLE imports DROP CONSTRAINT ck_imports_status;
ALTER TABLE imports ADD CONSTRAINT ck_imports_status CHECK (status IN ('EXTRAIDO', 'REVISADO'));

CREATE TABLE import_items (
    id                      UUID PRIMARY KEY,
    import_id               UUID        NOT NULL REFERENCES imports (id),
    position                INT         NOT NULL,
    type                    VARCHAR(20) NOT NULL,
    proposed_data           JSONB       NOT NULL,
    confidence              INT         NOT NULL,
    approved                BOOLEAN,
    resolution              VARCHAR(20),
    resulting_entity_type   VARCHAR(40),
    resulting_entity_id     UUID,
    CONSTRAINT uq_import_items_position UNIQUE (import_id, position),
    CONSTRAINT ck_import_items_type CHECK (type IN ('OBSERVACAO', 'CONFIRMACAO', 'EXCECAO')),
    CONSTRAINT ck_import_items_confidence CHECK (confidence = 1),
    CONSTRAINT ck_import_items_resolution CHECK (
        resolution IS NULL OR resolution IN ('MANTER', 'SUBSTITUIR', 'MESCLAR')
    ),
    CONSTRAINT ck_import_items_result CHECK (
        (resulting_entity_type IS NULL AND resulting_entity_id IS NULL)
        OR (resulting_entity_type IS NOT NULL AND resulting_entity_id IS NOT NULL)
    )
);

CREATE INDEX idx_import_items_import ON import_items (import_id, position);
