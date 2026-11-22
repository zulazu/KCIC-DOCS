CREATE TABLE repository.workspaces (
    id                        UUID PRIMARY KEY,
    workspace_code            VARCHAR(80) NOT NULL,
    workspace_name            VARCHAR(160) NOT NULL,
    description               TEXT,
    owner_organization_unit_id UUID NOT NULL,
    owner_user_id             UUID,
    workspace_status          VARCHAR(20) NOT NULL,
    created_at                TIMESTAMPTZ NOT NULL,
    updated_at                TIMESTAMPTZ NOT NULL,
    created_by                UUID,
    updated_by                UUID,
    row_version               BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT workspaces_status_chk CHECK (workspace_status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED')),
    CONSTRAINT workspaces_owner_unit_fk FOREIGN KEY (owner_organization_unit_id) REFERENCES organization.organization_units (id),
    CONSTRAINT workspaces_owner_user_fk FOREIGN KEY (owner_user_id) REFERENCES identity.users (id),
    CONSTRAINT workspaces_created_by_fk FOREIGN KEY (created_by) REFERENCES identity.users (id),
    CONSTRAINT workspaces_updated_by_fk FOREIGN KEY (updated_by) REFERENCES identity.users (id)
);

CREATE UNIQUE INDEX workspaces_code_lower_uq ON repository.workspaces (LOWER(workspace_code));
CREATE INDEX workspaces_owner_unit_status_idx ON repository.workspaces (owner_organization_unit_id, workspace_status);

CREATE TABLE repository.folders (
    id               UUID PRIMARY KEY,
    workspace_id     UUID NOT NULL,
    parent_folder_id UUID,
    folder_name      VARCHAR(160) NOT NULL,
    description      TEXT,
    folder_status    VARCHAR(20) NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL,
    created_by       UUID,
    updated_by       UUID,
    row_version      BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT folders_status_chk CHECK (folder_status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT folders_id_workspace_uq UNIQUE (id, workspace_id),
    CONSTRAINT folders_workspace_fk FOREIGN KEY (workspace_id) REFERENCES repository.workspaces (id),
    CONSTRAINT folders_parent_fk FOREIGN KEY (parent_folder_id, workspace_id)
        REFERENCES repository.folders (id, workspace_id),
    CONSTRAINT folders_created_by_fk FOREIGN KEY (created_by) REFERENCES identity.users (id),
    CONSTRAINT folders_updated_by_fk FOREIGN KEY (updated_by) REFERENCES identity.users (id)
);

CREATE INDEX folders_tree_idx ON repository.folders (workspace_id, parent_folder_id, folder_status);
CREATE UNIQUE INDEX folders_sibling_name_uq ON repository.folders (workspace_id, parent_folder_id, LOWER(folder_name))
    WHERE parent_folder_id IS NOT NULL AND folder_status = 'ACTIVE';
CREATE UNIQUE INDEX folders_root_name_uq ON repository.folders (workspace_id, LOWER(folder_name))
    WHERE parent_folder_id IS NULL AND folder_status = 'ACTIVE';

CREATE TABLE repository.documents (
    id                         UUID PRIMARY KEY,
    document_code              VARCHAR(100),
    title                      VARCHAR(240) NOT NULL,
    description                TEXT,
    workspace_id               UUID NOT NULL,
    folder_id                  UUID,
    category_id                UUID NOT NULL,
    document_type_id           UUID NOT NULL,
    classification_id          UUID NOT NULL,
    owner_user_id              UUID,
    owner_organization_unit_id UUID,
    document_status            VARCHAR(20) NOT NULL,
    effective_date             DATE,
    review_date                DATE,
    expiry_date                DATE,
    created_at                 TIMESTAMPTZ NOT NULL,
    updated_at                 TIMESTAMPTZ NOT NULL,
    created_by                 UUID NOT NULL,
    updated_by                 UUID,
    row_version                BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT documents_status_chk CHECK (document_status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    CONSTRAINT documents_owner_xor_chk CHECK (
        (owner_user_id IS NOT NULL AND owner_organization_unit_id IS NULL)
        OR (owner_user_id IS NULL AND owner_organization_unit_id IS NOT NULL)
    ),
    CONSTRAINT documents_workspace_fk FOREIGN KEY (workspace_id) REFERENCES repository.workspaces (id),
    CONSTRAINT documents_folder_fk FOREIGN KEY (folder_id, workspace_id)
        REFERENCES repository.folders (id, workspace_id),
    CONSTRAINT documents_category_fk FOREIGN KEY (category_id) REFERENCES master.document_categories (id),
    CONSTRAINT documents_type_fk FOREIGN KEY (document_type_id) REFERENCES master.document_types (id),
    CONSTRAINT documents_classification_fk FOREIGN KEY (classification_id) REFERENCES master.document_classifications (id),
    CONSTRAINT documents_owner_user_fk FOREIGN KEY (owner_user_id) REFERENCES identity.users (id),
    CONSTRAINT documents_owner_unit_fk FOREIGN KEY (owner_organization_unit_id) REFERENCES organization.organization_units (id),
    CONSTRAINT documents_created_by_fk FOREIGN KEY (created_by) REFERENCES identity.users (id),
    CONSTRAINT documents_updated_by_fk FOREIGN KEY (updated_by) REFERENCES identity.users (id)
);

CREATE UNIQUE INDEX documents_code_lower_uq ON repository.documents (LOWER(document_code)) WHERE document_code IS NOT NULL;
CREATE INDEX documents_workspace_status_idx ON repository.documents (workspace_id, document_status, updated_at DESC);
CREATE INDEX documents_owner_user_idx ON repository.documents (owner_user_id);
CREATE INDEX documents_owner_unit_idx ON repository.documents (owner_organization_unit_id);
CREATE INDEX documents_classification_idx ON repository.documents (classification_id);
CREATE INDEX documents_category_idx ON repository.documents (category_id);
CREATE INDEX documents_type_idx ON repository.documents (document_type_id);

CREATE TABLE repository.document_owner_history (
    id                         UUID PRIMARY KEY,
    document_id                UUID NOT NULL,
    owner_user_id              UUID,
    owner_organization_unit_id UUID,
    valid_from                 TIMESTAMPTZ NOT NULL,
    valid_until                TIMESTAMPTZ,
    changed_by                 UUID NOT NULL,
    change_reason              TEXT,
    created_at                 TIMESTAMPTZ NOT NULL,
    CONSTRAINT doh_owner_xor_chk CHECK (
        (owner_user_id IS NOT NULL AND owner_organization_unit_id IS NULL)
        OR (owner_user_id IS NULL AND owner_organization_unit_id IS NOT NULL)
    ),
    CONSTRAINT doh_validity_chk CHECK (valid_until IS NULL OR valid_until > valid_from),
    CONSTRAINT doh_document_fk FOREIGN KEY (document_id) REFERENCES repository.documents (id),
    CONSTRAINT doh_owner_user_fk FOREIGN KEY (owner_user_id) REFERENCES identity.users (id),
    CONSTRAINT doh_owner_unit_fk FOREIGN KEY (owner_organization_unit_id) REFERENCES organization.organization_units (id),
    CONSTRAINT doh_changed_by_fk FOREIGN KEY (changed_by) REFERENCES identity.users (id)
);

CREATE UNIQUE INDEX doh_one_active_uq ON repository.document_owner_history (document_id) WHERE valid_until IS NULL;
CREATE INDEX doh_document_idx ON repository.document_owner_history (document_id, valid_from DESC);

CREATE TABLE repository.storage_objects (
    id                UUID PRIMARY KEY,
    storage_provider  VARCHAR(30) NOT NULL,
    bucket_name       VARCHAR(120) NOT NULL,
    object_key        VARCHAR(700) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type      VARCHAR(160) NOT NULL,
    size_bytes        BIGINT NOT NULL,
    checksum_sha256   CHAR(64) NOT NULL,
    upload_status     VARCHAR(20) NOT NULL,
    uploaded_by       UUID NOT NULL,
    uploaded_at       TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL,
    CONSTRAINT storage_objects_size_chk CHECK (size_bytes >= 0),
    CONSTRAINT storage_objects_status_chk CHECK (upload_status IN ('PENDING', 'AVAILABLE', 'DELETE_PENDING', 'DELETED', 'FAILED')),
    CONSTRAINT storage_objects_uploaded_by_fk FOREIGN KEY (uploaded_by) REFERENCES identity.users (id)
);

CREATE UNIQUE INDEX storage_objects_locator_uq ON repository.storage_objects (storage_provider, bucket_name, object_key);
CREATE INDEX storage_objects_status_idx ON repository.storage_objects (upload_status);

CREATE TABLE repository.external_resources (
    id              UUID PRIMARY KEY,
    url             TEXT NOT NULL,
    normalized_host VARCHAR(255) NOT NULL,
    provider_code   VARCHAR(40),
    link_title      VARCHAR(240),
    created_by      UUID NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT external_resources_created_by_fk FOREIGN KEY (created_by) REFERENCES identity.users (id)
);

CREATE TABLE repository.document_versions (
    id                   UUID PRIMARY KEY,
    document_id          UUID NOT NULL,
    version_number       INTEGER NOT NULL,
    version_label        VARCHAR(60),
    content_source       VARCHAR(20) NOT NULL,
    storage_object_id    UUID,
    external_resource_id UUID,
    version_status       VARCHAR(20) NOT NULL,
    change_summary       TEXT,
    created_by           UUID NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL,
    published_at         TIMESTAMPTZ,
    CONSTRAINT document_versions_number_chk CHECK (version_number > 0),
    CONSTRAINT document_versions_source_chk CHECK (content_source IN ('STORAGE_OBJECT', 'EXTERNAL_LINK')),
    CONSTRAINT document_versions_status_chk CHECK (version_status IN ('DRAFT', 'CURRENT', 'SUPERSEDED', 'WITHDRAWN')),
    CONSTRAINT document_versions_content_xor_chk CHECK (
        (content_source = 'STORAGE_OBJECT' AND storage_object_id IS NOT NULL AND external_resource_id IS NULL)
        OR (content_source = 'EXTERNAL_LINK' AND storage_object_id IS NULL AND external_resource_id IS NOT NULL)
    ),
    CONSTRAINT document_versions_document_fk FOREIGN KEY (document_id) REFERENCES repository.documents (id),
    CONSTRAINT document_versions_storage_fk FOREIGN KEY (storage_object_id) REFERENCES repository.storage_objects (id),
    CONSTRAINT document_versions_external_fk FOREIGN KEY (external_resource_id) REFERENCES repository.external_resources (id),
    CONSTRAINT document_versions_created_by_fk FOREIGN KEY (created_by) REFERENCES identity.users (id),
    CONSTRAINT document_versions_id_document_uq UNIQUE (id, document_id)
);

CREATE UNIQUE INDEX document_versions_number_uq ON repository.document_versions (document_id, version_number);
CREATE UNIQUE INDEX document_versions_one_current_uq ON repository.document_versions (document_id)
    WHERE version_status = 'CURRENT';

CREATE TABLE repository.document_tag_assignments (
    document_id UUID NOT NULL,
    tag_id      UUID NOT NULL,
    assigned_by UUID NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT document_tag_assignments_pk PRIMARY KEY (document_id, tag_id),
    CONSTRAINT document_tag_assignments_document_fk FOREIGN KEY (document_id) REFERENCES repository.documents (id),
    CONSTRAINT document_tag_assignments_tag_fk FOREIGN KEY (tag_id) REFERENCES master.tags (id),
    CONSTRAINT document_tag_assignments_assigned_by_fk FOREIGN KEY (assigned_by) REFERENCES identity.users (id)
);

CREATE TABLE repository.document_favorites (
    user_id     UUID NOT NULL,
    document_id UUID NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT document_favorites_pk PRIMARY KEY (user_id, document_id),
    CONSTRAINT document_favorites_user_fk FOREIGN KEY (user_id) REFERENCES identity.users (id),
    CONSTRAINT document_favorites_document_fk FOREIGN KEY (document_id) REFERENCES repository.documents (id)
);

CREATE INDEX document_favorites_user_idx ON repository.document_favorites (user_id, created_at DESC);
