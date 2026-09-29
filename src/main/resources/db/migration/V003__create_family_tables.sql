CREATE TABLE families (
    id         UUID PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE children (
    id         UUID PRIMARY KEY,
    family_id  UUID         NOT NULL REFERENCES families (id),
    name       VARCHAR(255) NOT NULL,
    birth_date DATE,
    active     BOOLEAN      NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE guardians (
    id           UUID PRIMARY KEY,
    child_id     UUID         NOT NULL REFERENCES children (id),
    name         VARCHAR(255) NOT NULL,
    relationship VARCHAR(100) NOT NULL,
    user_id      UUID         REFERENCES users (id)
);

CREATE UNIQUE INDEX uq_guardians_child_user
    ON guardians (child_id, user_id)
    WHERE user_id IS NOT NULL;

CREATE TABLE family_access (
    id          UUID PRIMARY KEY,
    family_id   UUID        NOT NULL REFERENCES families (id),
    user_id     UUID        NOT NULL REFERENCES users (id),
    role        VARCHAR(32) NOT NULL,
    invited_by  UUID,
    accepted_at TIMESTAMPTZ,
    CONSTRAINT uq_family_access_family_user UNIQUE (family_id, user_id)
);
