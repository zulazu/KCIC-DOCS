-- Employee master and placement history (SCHEMA.md §21.2–21.3).
-- Additive only: no existing organization data is converted or backfilled.
CREATE TABLE identity.employees (
    id                 UUID PRIMARY KEY,
    nip                VARCHAR(30) NOT NULL,
    full_name          VARCHAR(160) NOT NULL,
    email              VARCHAR(254) NOT NULL,
    employment_status  VARCHAR(20) NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,
    created_by         UUID,
    updated_by         UUID,
    row_version        BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT employees_employment_status_chk CHECK (employment_status IN ('ACTIVE', 'INACTIVE', 'TERMINATED')),
    CONSTRAINT employees_created_by_fk FOREIGN KEY (created_by) REFERENCES identity.users (id),
    CONSTRAINT employees_updated_by_fk FOREIGN KEY (updated_by) REFERENCES identity.users (id)
);

CREATE UNIQUE INDEX employees_nip_lower_uq ON identity.employees (LOWER(nip));
CREATE UNIQUE INDEX employees_email_lower_uq ON identity.employees (LOWER(email));
CREATE INDEX employees_status_idx ON identity.employees (employment_status);

CREATE TABLE organization.employee_placements (
    id                UUID PRIMARY KEY,
    employee_id       UUID NOT NULL,
    division_unit_id  UUID NOT NULL,
    valid_from        TIMESTAMPTZ NOT NULL,
    valid_until       TIMESTAMPTZ,
    is_active         BOOLEAN NOT NULL DEFAULT TRUE,
    assigned_by       UUID NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL,
    CONSTRAINT employee_placements_validity_chk CHECK (valid_until IS NULL OR valid_until > valid_from),
    CONSTRAINT employee_placements_employee_fk FOREIGN KEY (employee_id) REFERENCES identity.employees (id),
    CONSTRAINT employee_placements_division_fk FOREIGN KEY (division_unit_id)
        REFERENCES organization.organization_units (id),
    CONSTRAINT employee_placements_assigned_by_fk FOREIGN KEY (assigned_by) REFERENCES identity.users (id)
);

CREATE UNIQUE INDEX employee_placements_one_active_uq
    ON organization.employee_placements (employee_id) WHERE is_active;
CREATE INDEX employee_placements_division_active_idx
    ON organization.employee_placements (division_unit_id, is_active);
CREATE INDEX employee_placements_employee_valid_from_idx
    ON organization.employee_placements (employee_id, valid_from DESC);

-- Employee permissions. SCHEMA §21.7 lists them under V16 together with the
-- account link; they are seeded here because the V15 administration API is
-- unusable without them. V16 keeps an idempotent re-insert for upgrade safety.
INSERT INTO "authorization".permissions (id, permission_code, permission_name, description, module_code, created_at)
VALUES
    (gen_random_uuid(), 'employee.view', 'Lihat employee',
     'Melihat data employee, placement aktif, dan history placement.', 'identity', now()),
    (gen_random_uuid(), 'employee.manage', 'Kelola employee',
     'Membuat, memperbarui, dan memindahkan employee pada Biro/Division.', 'identity', now())
ON CONFLICT (permission_code) DO NOTHING;

INSERT INTO "authorization".role_permissions (role_id, permission_id, created_at)
SELECT r.id, p.id, now()
FROM "authorization".roles r
JOIN "authorization".permissions p ON p.permission_code IN ('employee.view', 'employee.manage')
WHERE r.role_code = 'SYSTEM_ADMIN'
ON CONFLICT DO NOTHING;
