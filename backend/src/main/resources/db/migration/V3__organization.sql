CREATE TABLE organization.organization_units (
    id            UUID PRIMARY KEY,
    parent_id     UUID,
    unit_code     VARCHAR(50) NOT NULL,
    unit_name     VARCHAR(160) NOT NULL,
    description   TEXT,
    unit_status   VARCHAR(20) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL,
    created_by    UUID,
    updated_by    UUID,
    row_version   BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT organization_units_status_chk CHECK (unit_status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT organization_units_not_self_parent_chk CHECK (parent_id IS NULL OR parent_id <> id),
    CONSTRAINT organization_units_parent_fk FOREIGN KEY (parent_id) REFERENCES organization.organization_units (id),
    CONSTRAINT organization_units_created_by_fk FOREIGN KEY (created_by) REFERENCES identity.users (id),
    CONSTRAINT organization_units_updated_by_fk FOREIGN KEY (updated_by) REFERENCES identity.users (id)
);

CREATE UNIQUE INDEX organization_units_code_lower_uq ON organization.organization_units (LOWER(unit_code));
CREATE INDEX organization_units_parent_idx ON organization.organization_units (parent_id);

CREATE TABLE organization.user_organization_assignments (
    id                     UUID PRIMARY KEY,
    user_id                UUID NOT NULL,
    organization_unit_id   UUID NOT NULL,
    assignment_type        VARCHAR(20) NOT NULL,
    valid_from             TIMESTAMPTZ NOT NULL,
    valid_until            TIMESTAMPTZ,
    is_active              BOOLEAN NOT NULL DEFAULT TRUE,
    assigned_by            UUID NOT NULL,
    created_at             TIMESTAMPTZ NOT NULL,
    CONSTRAINT uoa_assignment_type_chk CHECK (assignment_type IN ('PRIMARY', 'SECONDARY')),
    CONSTRAINT uoa_validity_chk CHECK (valid_until IS NULL OR valid_until > valid_from),
    CONSTRAINT uoa_user_fk FOREIGN KEY (user_id) REFERENCES identity.users (id),
    CONSTRAINT uoa_unit_fk FOREIGN KEY (organization_unit_id) REFERENCES organization.organization_units (id),
    CONSTRAINT uoa_assigned_by_fk FOREIGN KEY (assigned_by) REFERENCES identity.users (id)
);

CREATE UNIQUE INDEX uoa_one_active_primary_uq ON organization.user_organization_assignments (user_id)
    WHERE assignment_type = 'PRIMARY' AND is_active;
CREATE INDEX uoa_user_active_idx ON organization.user_organization_assignments (user_id, is_active);
CREATE INDEX uoa_unit_active_idx ON organization.user_organization_assignments (organization_unit_id, is_active);
