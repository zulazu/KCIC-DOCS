-- E1: typed organization hierarchy. Existing units remain NULL until the
-- business owner maps them; no type is guessed during migration.
ALTER TABLE organization.organization_units
    ADD COLUMN unit_type VARCHAR(20);

ALTER TABLE organization.organization_units
    ADD CONSTRAINT organization_units_type_chk
        CHECK (unit_type IS NULL OR unit_type IN ('COMPANY', 'DIRECTORATE', 'DEPARTMENT', 'DIVISION'));

CREATE INDEX organization_units_type_status_idx
    ON organization.organization_units (unit_type, unit_status);
