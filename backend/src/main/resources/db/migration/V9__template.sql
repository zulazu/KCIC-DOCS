CREATE TABLE template.document_templates (
    id                           UUID PRIMARY KEY,
    document_id                  UUID NOT NULL UNIQUE,
    template_code                VARCHAR(80) NOT NULL,
    template_name                VARCHAR(180) NOT NULL,
    description                  TEXT,
    template_status              VARCHAR(20) NOT NULL,
    current_published_version_id UUID,
    published_by                 UUID,
    published_at                 TIMESTAMPTZ,
    created_at                   TIMESTAMPTZ NOT NULL,
    updated_at                   TIMESTAMPTZ NOT NULL,
    created_by                   UUID,
    updated_by                   UUID,
    row_version                  BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT document_templates_status_chk CHECK (template_status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT document_templates_code_uq UNIQUE (template_code),
    CONSTRAINT document_templates_document_fk FOREIGN KEY (document_id) REFERENCES repository.documents (id),
    CONSTRAINT document_templates_version_same_document_fk FOREIGN KEY (current_published_version_id, document_id)
        REFERENCES repository.document_versions (id, document_id),
    CONSTRAINT document_templates_published_by_fk FOREIGN KEY (published_by) REFERENCES identity.users (id),
    CONSTRAINT document_templates_created_by_fk FOREIGN KEY (created_by) REFERENCES identity.users (id),
    CONSTRAINT document_templates_updated_by_fk FOREIGN KEY (updated_by) REFERENCES identity.users (id)
);

CREATE INDEX document_templates_catalog_idx ON template.document_templates (template_status, template_name);

CREATE TABLE template.template_usage_events (
    id                  UUID PRIMARY KEY,
    template_id         UUID NOT NULL,
    document_version_id UUID NOT NULL,
    user_id             UUID NOT NULL,
    usage_type          VARCHAR(20) NOT NULL,
    occurred_at         TIMESTAMPTZ NOT NULL,
    CONSTRAINT template_usage_events_type_chk CHECK (usage_type IN ('VIEW', 'DOWNLOAD', 'COPY')),
    CONSTRAINT template_usage_events_template_fk FOREIGN KEY (template_id) REFERENCES template.document_templates (id),
    CONSTRAINT template_usage_events_version_fk FOREIGN KEY (document_version_id) REFERENCES repository.document_versions (id),
    CONSTRAINT template_usage_events_user_fk FOREIGN KEY (user_id) REFERENCES identity.users (id)
);

CREATE INDEX template_usage_events_template_idx ON template.template_usage_events (template_id, occurred_at DESC);
