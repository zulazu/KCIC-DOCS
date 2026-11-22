-- Apply the signed E6 typed-hierarchy mapping for legacy units.
-- Data change (NOT a schema migration). Runs in one transaction and aborts if the
-- result would violate the typed-hierarchy invariants.
--
-- Current recommendation (see docs/organization-identity/E6_UNIT_MAPPING.md):
--   KCIC-HO  (Head Office / Kantor pusat) -> COMPANY root
--   KCIC-OPS (Operations)                 -> DIRECTORATE under KCIC-HO
--
-- Do not run against an environment that still contains another ACTIVE COMPANY
-- fixture (for example UAT-CO): the single-root invariant would fail.
-- Preconditions are validated before any UPDATE; on failure the transaction aborts.
BEGIN;

DO $$
DECLARE
    ho_id  uuid;
    ops_id uuid;
    active_company_count integer;
BEGIN
    SELECT id INTO ho_id FROM organization.organization_units
     WHERE lower(unit_code) = 'kcic-ho' AND unit_status = 'ACTIVE';
    SELECT id INTO ops_id FROM organization.organization_units
     WHERE lower(unit_code) = 'kcic-ops' AND unit_status = 'ACTIVE';

    IF ho_id IS NULL OR ops_id IS NULL THEN
        RAISE EXCEPTION 'E6 mapping aborted: KCIC-HO and KCIC-OPS must both exist and be ACTIVE';
    END IF;

    SELECT count(*) INTO active_company_count
      FROM organization.organization_units
     WHERE unit_type = 'COMPANY' AND unit_status = 'ACTIVE'
       AND id <> ho_id;
    IF active_company_count > 0 THEN
        RAISE EXCEPTION 'E6 mapping aborted: % other ACTIVE COMPANY unit(s) exist; resolve the root first',
            active_company_count;
    END IF;

    UPDATE organization.organization_units
       SET unit_type = 'COMPANY', parent_id = NULL, updated_at = now()
     WHERE id = ho_id AND unit_type IS DISTINCT FROM 'COMPANY';

    UPDATE organization.organization_units
       SET unit_type = 'DIRECTORATE', parent_id = ho_id, updated_at = now()
     WHERE id = ops_id AND (unit_type IS DISTINCT FROM 'DIRECTORATE' OR parent_id IS DISTINCT FROM ho_id);
END $$;

-- Verification inside the same transaction: abort if the invariants do not hold.
DO $$
DECLARE
    company_roots integer;
    bad_children integer;
BEGIN
    SELECT count(*) INTO company_roots
      FROM organization.organization_units
     WHERE unit_type = 'COMPANY' AND unit_status = 'ACTIVE';
    IF company_roots <> 1 THEN
        RAISE EXCEPTION 'E6 mapping aborted: expected exactly 1 ACTIVE COMPANY, found %', company_roots;
    END IF;

    SELECT count(*) INTO bad_children
      FROM organization.organization_units c
      LEFT JOIN organization.organization_units p ON p.id = c.parent_id
     WHERE c.unit_type IS NOT NULL
       AND c.unit_status = 'ACTIVE'
       AND (
            (c.unit_type = 'COMPANY' AND c.parent_id IS NOT NULL)
         OR (c.unit_type = 'DIRECTORATE' AND p.unit_type IS DISTINCT FROM 'COMPANY')
         OR (c.unit_type = 'DEPARTMENT' AND p.unit_type IS DISTINCT FROM 'DIRECTORATE')
         OR (c.unit_type = 'DIVISION' AND p.unit_type IS DISTINCT FROM 'DEPARTMENT')
         OR (c.unit_type <> 'COMPANY' AND c.parent_id IS NULL)
       );
    IF bad_children > 0 THEN
        RAISE EXCEPTION 'E6 mapping aborted: % typed unit(s) violate the hierarchy shape', bad_children;
    END IF;
END $$;

SELECT unit_code, unit_name, unit_type, parent_id
FROM organization.organization_units
WHERE lower(unit_code) IN ('kcic-ho', 'kcic-ops')
ORDER BY unit_code;

COMMIT;

-- Reversal (only if the mapping was wrong and no dependent data changed):
-- BEGIN;
-- UPDATE organization.organization_units SET unit_type = NULL
--  WHERE lower(unit_code) IN ('kcic-ho', 'kcic-ops');
-- UPDATE organization.organization_units SET parent_id = NULL
--  WHERE lower(unit_code) = 'kcic-ops';
-- COMMIT;
