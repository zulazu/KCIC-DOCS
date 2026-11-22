CREATE TABLE "authorization".roles (
    id             UUID PRIMARY KEY,
    role_code      VARCHAR(80) NOT NULL UNIQUE,
    role_name      VARCHAR(120) NOT NULL,
    description    TEXT,
    is_system_role BOOLEAN NOT NULL DEFAULT FALSE,
    role_status    VARCHAR(20) NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL,
    created_by     UUID,
    updated_by     UUID,
    row_version    BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT roles_status_chk CHECK (role_status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT roles_created_by_fk FOREIGN KEY (created_by) REFERENCES identity.users (id),
    CONSTRAINT roles_updated_by_fk FOREIGN KEY (updated_by) REFERENCES identity.users (id)
);

CREATE TABLE "authorization".permissions (
    id              UUID PRIMARY KEY,
    permission_code VARCHAR(120) NOT NULL UNIQUE,
    permission_name VARCHAR(160) NOT NULL,
    description     TEXT,
    module_code     VARCHAR(60) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL
);

CREATE TABLE "authorization".role_permissions (
    role_id       UUID NOT NULL,
    permission_id UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL,
    CONSTRAINT role_permissions_pk PRIMARY KEY (role_id, permission_id),
    CONSTRAINT role_permissions_role_fk FOREIGN KEY (role_id) REFERENCES "authorization".roles (id),
    CONSTRAINT role_permissions_permission_fk FOREIGN KEY (permission_id) REFERENCES "authorization".permissions (id)
);

CREATE TABLE "authorization".user_role_assignments (
    id                   UUID PRIMARY KEY,
    user_id              UUID NOT NULL,
    role_id              UUID NOT NULL,
    scope_type           VARCHAR(30) NOT NULL,
    organization_unit_id UUID,
    workspace_id         UUID,
    valid_from           TIMESTAMPTZ NOT NULL,
    valid_until          TIMESTAMPTZ,
    assignment_status    VARCHAR(20) NOT NULL,
    assigned_by          UUID NOT NULL,
    revoked_by           UUID,
    revoked_at           TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL,
    CONSTRAINT ura_scope_type_chk CHECK (scope_type IN ('GLOBAL', 'ORGANIZATION_UNIT', 'WORKSPACE')),
    CONSTRAINT ura_scope_shape_chk CHECK (
        (scope_type = 'GLOBAL' AND organization_unit_id IS NULL AND workspace_id IS NULL)
        OR (scope_type = 'ORGANIZATION_UNIT' AND organization_unit_id IS NOT NULL AND workspace_id IS NULL)
        OR (scope_type = 'WORKSPACE' AND workspace_id IS NOT NULL AND organization_unit_id IS NULL)
    ),
    CONSTRAINT ura_status_chk CHECK (assignment_status IN ('ACTIVE', 'REVOKED', 'EXPIRED')),
    CONSTRAINT ura_validity_chk CHECK (valid_until IS NULL OR valid_until > valid_from),
    CONSTRAINT ura_user_fk FOREIGN KEY (user_id) REFERENCES identity.users (id),
    CONSTRAINT ura_role_fk FOREIGN KEY (role_id) REFERENCES "authorization".roles (id),
    CONSTRAINT ura_unit_fk FOREIGN KEY (organization_unit_id) REFERENCES organization.organization_units (id),
    CONSTRAINT ura_workspace_fk FOREIGN KEY (workspace_id) REFERENCES repository.workspaces (id),
    CONSTRAINT ura_assigned_by_fk FOREIGN KEY (assigned_by) REFERENCES identity.users (id),
    CONSTRAINT ura_revoked_by_fk FOREIGN KEY (revoked_by) REFERENCES identity.users (id)
);

CREATE INDEX ura_user_active_idx ON "authorization".user_role_assignments (user_id, assignment_status, valid_from, valid_until);
CREATE INDEX ura_role_idx ON "authorization".user_role_assignments (role_id);

CREATE TABLE "authorization".resource_permissions (
    id                              UUID PRIMARY KEY,
    resource_type                   VARCHAR(20) NOT NULL,
    resource_id                     UUID NOT NULL,
    principal_type                  VARCHAR(30) NOT NULL,
    principal_user_id               UUID,
    principal_role_id               UUID,
    principal_organization_unit_id  UUID,
    permission_id                   UUID NOT NULL,
    effect                          VARCHAR(10) NOT NULL,
    granted_by                      UUID NOT NULL,
    grant_source                    VARCHAR(20) NOT NULL,
    granted_at                      TIMESTAMPTZ NOT NULL,
    expires_at                      TIMESTAMPTZ,
    revoked_by                      UUID,
    revoked_at                      TIMESTAMPTZ,
    created_at                      TIMESTAMPTZ NOT NULL,
    CONSTRAINT resource_permissions_resource_type_chk CHECK (resource_type IN ('WORKSPACE', 'FOLDER', 'DOCUMENT')),
    CONSTRAINT resource_permissions_principal_type_chk CHECK (principal_type IN ('USER', 'ROLE', 'ORGANIZATION_UNIT')),
    CONSTRAINT resource_permissions_principal_shape_chk CHECK (
        (principal_type = 'USER' AND principal_user_id IS NOT NULL AND principal_role_id IS NULL AND principal_organization_unit_id IS NULL)
        OR (principal_type = 'ROLE' AND principal_role_id IS NOT NULL AND principal_user_id IS NULL AND principal_organization_unit_id IS NULL)
        OR (principal_type = 'ORGANIZATION_UNIT' AND principal_organization_unit_id IS NOT NULL AND principal_user_id IS NULL AND principal_role_id IS NULL)
    ),
    CONSTRAINT resource_permissions_effect_chk CHECK (effect IN ('ALLOW', 'DENY')),
    CONSTRAINT resource_permissions_source_chk CHECK (grant_source IN ('MANUAL', 'REQUEST', 'DISTRIBUTION', 'SYSTEM')),
    CONSTRAINT resource_permissions_expiry_chk CHECK (expires_at IS NULL OR expires_at > granted_at),
    CONSTRAINT resource_permissions_permission_fk FOREIGN KEY (permission_id) REFERENCES "authorization".permissions (id),
    CONSTRAINT resource_permissions_user_fk FOREIGN KEY (principal_user_id) REFERENCES identity.users (id),
    CONSTRAINT resource_permissions_role_fk FOREIGN KEY (principal_role_id) REFERENCES "authorization".roles (id),
    CONSTRAINT resource_permissions_unit_fk FOREIGN KEY (principal_organization_unit_id) REFERENCES organization.organization_units (id),
    CONSTRAINT resource_permissions_granted_by_fk FOREIGN KEY (granted_by) REFERENCES identity.users (id),
    CONSTRAINT resource_permissions_revoked_by_fk FOREIGN KEY (revoked_by) REFERENCES identity.users (id)
);

CREATE INDEX resource_permissions_resource_idx ON "authorization".resource_permissions (resource_type, resource_id);
CREATE INDEX resource_permissions_user_idx ON "authorization".resource_permissions (principal_user_id);
CREATE INDEX resource_permissions_role_idx ON "authorization".resource_permissions (principal_role_id);
CREATE INDEX resource_permissions_unit_idx ON "authorization".resource_permissions (principal_organization_unit_id);
CREATE INDEX resource_permissions_permission_idx ON "authorization".resource_permissions (permission_id);
CREATE INDEX resource_permissions_active_idx ON "authorization".resource_permissions (resource_type, resource_id, revoked_at)
    WHERE revoked_at IS NULL;
