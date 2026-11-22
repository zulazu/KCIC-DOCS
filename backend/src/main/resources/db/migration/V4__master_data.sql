CREATE TABLE master.document_categories (
    id            UUID PRIMARY KEY,
    parent_id     UUID,
    category_code VARCHAR(60) NOT NULL,
    category_name VARCHAR(140) NOT NULL,
    description   TEXT,
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL,
    created_by    UUID,
    updated_by    UUID,
    row_version   BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT document_categories_code_uq UNIQUE (category_code),
    CONSTRAINT document_categories_not_self_parent_chk CHECK (parent_id IS NULL OR parent_id <> id),
    CONSTRAINT document_categories_parent_fk FOREIGN KEY (parent_id) REFERENCES master.document_categories (id),
    CONSTRAINT document_categories_created_by_fk FOREIGN KEY (created_by) REFERENCES identity.users (id),
    CONSTRAINT document_categories_updated_by_fk FOREIGN KEY (updated_by) REFERENCES identity.users (id)
);

CREATE TABLE master.document_types (
    id          UUID PRIMARY KEY,
    type_code   VARCHAR(60) NOT NULL,
    type_name   VARCHAR(140) NOT NULL,
    description TEXT,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    created_by  UUID,
    updated_by  UUID,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT document_types_code_uq UNIQUE (type_code),
    CONSTRAINT document_types_created_by_fk FOREIGN KEY (created_by) REFERENCES identity.users (id),
    CONSTRAINT document_types_updated_by_fk FOREIGN KEY (updated_by) REFERENCES identity.users (id)
);

CREATE TABLE master.document_classifications (
    id                  UUID PRIMARY KEY,
    classification_code VARCHAR(40) NOT NULL,
    classification_name VARCHAR(100) NOT NULL,
    sensitivity_rank    SMALLINT NOT NULL,
    description         TEXT,
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    created_by          UUID,
    updated_by          UUID,
    row_version         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT document_classifications_code_uq UNIQUE (classification_code),
    CONSTRAINT document_classifications_created_by_fk FOREIGN KEY (created_by) REFERENCES identity.users (id),
    CONSTRAINT document_classifications_updated_by_fk FOREIGN KEY (updated_by) REFERENCES identity.users (id)
);

CREATE TABLE master.tags (
    id         UUID PRIMARY KEY,
    tag_code   VARCHAR(60) NOT NULL,
    tag_name   VARCHAR(100) NOT NULL,
    is_active  BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT tags_code_uq UNIQUE (tag_code)
);

CREATE TABLE master.file_policies (
    id                 UUID PRIMARY KEY,
    policy_code        VARCHAR(60) NOT NULL,
    policy_name        VARCHAR(120) NOT NULL,
    allowed_extensions TEXT[] NOT NULL,
    allowed_mime_types TEXT[] NOT NULL,
    max_file_size_bytes BIGINT NOT NULL,
    is_default         BOOLEAN NOT NULL DEFAULT FALSE,
    is_active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,
    updated_by         UUID,
    CONSTRAINT file_policies_code_uq UNIQUE (policy_code),
    CONSTRAINT file_policies_max_size_chk CHECK (max_file_size_bytes > 0),
    CONSTRAINT file_policies_updated_by_fk FOREIGN KEY (updated_by) REFERENCES identity.users (id)
);

CREATE UNIQUE INDEX file_policies_single_default_uq ON master.file_policies (is_default)
    WHERE is_default AND is_active;
