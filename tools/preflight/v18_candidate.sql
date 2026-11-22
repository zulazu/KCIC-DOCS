-- V18 CANDIDATE — cutover-only. NOT a Flyway migration in this repository.
-- Move to backend/src/main/resources/db/migration/V18__enforce_employee_identity.sql
-- only after the signed E6 preflight PASS and a rehearsal on a production-like
-- restore. Applying it while any user is unlinked or any unit is untyped fails.
--
-- Rehearsed by tools/preflight/run-e6-rehearsal.sh on a throwaway database.
BEGIN;

ALTER TABLE identity.users
    ALTER COLUMN employee_id SET NOT NULL;

ALTER TABLE organization.organization_units
    ALTER COLUMN unit_type SET NOT NULL;

COMMIT;
