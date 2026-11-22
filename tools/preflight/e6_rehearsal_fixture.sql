-- REHEARSAL FIXTURES ONLY. Loaded into a throwaway database by
-- tools/preflight/run-e6-rehearsal.sh. Never run this against a shared/dev database.
-- Values are deliberately labeled rehearsal data; they are not HR records.
-- KCIC-HO/KCIC-OPS are untyped on purpose: the rehearsal then runs the real
-- e6_apply_unit_mapping.sql to prove the recommended mapping.
BEGIN;

INSERT INTO organization.organization_units
    (id, parent_id, unit_code, unit_name, description, unit_status, created_at, updated_at)
VALUES
    ('00000000-0000-4000-8000-000000000001', NULL,
     'KCIC-HO', 'Head Office', 'Kantor pusat (rehearsal copy)', 'ACTIVE', now(), now()),
    ('00000000-0000-4000-8000-000000000002', NULL,
     'KCIC-OPS', 'Operations', 'Rehearsal copy', 'ACTIVE', now(), now()),
    ('00000000-0000-4000-8000-000000000003', '00000000-0000-4000-8000-000000000002',
     'REH-DEPT', 'Rehearsal Department', 'E6 rehearsal only', 'ACTIVE', now(), now()),
    ('00000000-0000-4000-8000-000000000004', '00000000-0000-4000-8000-000000000003',
     'REH-BIRO', 'Rehearsal Biro/Division', 'E6 rehearsal only', 'ACTIVE', now(), now());

INSERT INTO identity.employees
    (id, nip, full_name, email, employment_status, created_at, updated_at)
VALUES
    ('00000000-0000-4000-8000-000000000101', 'REH-NIP-001', 'Rehearsal Employee One',
     'reh.one@rehearsal.invalid', 'ACTIVE', now(), now()),
    ('00000000-0000-4000-8000-000000000102', 'REH-NIP-002', 'Rehearsal Employee Two',
     'reh.two@rehearsal.invalid', 'ACTIVE', now(), now());

INSERT INTO identity.users
    (id, username, email, display_name, password_hash, account_status, must_change_password,
     employee_id, created_at, updated_at)
VALUES
    ('00000000-0000-4000-8000-000000000201', 'reh.one', 'reh.one@rehearsal.invalid',
     'Rehearsal Employee One', 'rehearsal-placeholder-not-a-login', 'ACTIVE', true,
     '00000000-0000-4000-8000-000000000101', now(), now()),
    ('00000000-0000-4000-8000-000000000202', 'reh.two', 'reh.two@rehearsal.invalid',
     'Rehearsal Employee Two', 'rehearsal-placeholder-not-a-login', 'ACTIVE', true,
     '00000000-0000-4000-8000-000000000102', now(), now());

INSERT INTO organization.employee_placements
    (id, employee_id, division_unit_id, valid_from, valid_until, is_active, assigned_by, created_at)
VALUES
    ('00000000-0000-4000-8000-000000000301', '00000000-0000-4000-8000-000000000101',
     '00000000-0000-4000-8000-000000000004', now(), NULL, true,
     '00000000-0000-4000-8000-000000000201', now()),
    ('00000000-0000-4000-8000-000000000302', '00000000-0000-4000-8000-000000000102',
     '00000000-0000-4000-8000-000000000004', now(), NULL, true,
     '00000000-0000-4000-8000-000000000201', now());

-- These are the units below the mapped directorate (the mapping script owns
-- KCIC-HO/KCIC-OPS type and parent).
UPDATE organization.organization_units SET unit_type = 'DEPARTMENT'
 WHERE id = '00000000-0000-4000-8000-000000000003';
UPDATE organization.organization_units SET unit_type = 'DIVISION'
 WHERE id = '00000000-0000-4000-8000-000000000004';

COMMIT;
