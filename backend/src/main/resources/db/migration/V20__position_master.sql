-- Position/Jabatan master catalog and employee attribute (DEC-076).
-- Additive only: organization hierarchy stays COMPANY/DIRECTORATE/DEPARTMENT/DIVISION;
-- jabatan is a job-title catalog assigned to an employee, not an organization unit.
CREATE TABLE master.positions (
    id             UUID PRIMARY KEY,
    position_code  VARCHAR(40) NOT NULL,
    position_name  VARCHAR(140) NOT NULL,
    description    VARCHAR(4000),
    is_active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL,
    created_by     UUID,
    updated_by     UUID,
    row_version    BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT positions_created_by_fk FOREIGN KEY (created_by) REFERENCES identity.users (id),
    CONSTRAINT positions_updated_by_fk FOREIGN KEY (updated_by) REFERENCES identity.users (id)
);

CREATE UNIQUE INDEX positions_code_lower_uq ON master.positions (LOWER(position_code));
CREATE INDEX positions_active_idx ON master.positions (is_active);

ALTER TABLE identity.employees
    ADD COLUMN position_id UUID,
    ADD CONSTRAINT employees_position_fk FOREIGN KEY (position_id) REFERENCES master.positions (id);

CREATE INDEX employees_position_idx ON identity.employees (position_id);

-- Baseline job-title catalog (labels only, no employee data). Idempotent.
INSERT INTO master.positions (id, position_code, position_name, description, created_at, updated_at)
VALUES
    (gen_random_uuid(), 'DIREKTUR', 'Direktur', 'Pimpinan direktorat.', now(), now()),
    (gen_random_uuid(), 'KEPALA_DEPARTEMEN', 'Kepala Departemen', 'Pimpinan departemen.', now(), now()),
    (gen_random_uuid(), 'KEPALA_BIRO', 'Kepala Biro', 'Pimpinan biro/division.', now(), now()),
    (gen_random_uuid(), 'MANAGER', 'Manager', 'Manager pada unit kerja.', now(), now()),
    (gen_random_uuid(), 'SUPERVISOR', 'Supervisor', 'Supervisor pada unit kerja.', now(), now()),
    (gen_random_uuid(), 'STAFF', 'Staff', 'Staf pelaksana pada unit kerja.', now(), now())
ON CONFLICT DO NOTHING;