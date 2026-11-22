-- E6 unlinked-account worklist. READ-ONLY: no INSERT/UPDATE/DELETE/DDL.
-- Purpose: hand this output to HR/org owner to obtain a verified NIP, official
-- name/email, employment status, and Biro/Division for every account.
-- Username and account email are NOT identity keys; they must not be copied
-- into Employee fields without HR confirmation (DEC-064, DEC-068).
--
-- Run: docker exec -i kcic-postgres psql -X -U kcic -d kcic_connect \
--        < tools/preflight/e6_unlinked_worklist.sql

\echo '== 1. unlinked accounts (V18 worklist) =='
SELECT u.id AS user_id,
       u.username,
       u.display_name,
       u.email AS account_email,
       coalesce(string_agg(DISTINCT ou.unit_code || ' (' || a.assignment_type || ')', ', '), '') AS legacy_units,
       u.created_at
FROM identity.users u
LEFT JOIN organization.user_organization_assignments a
       ON a.user_id = u.id AND a.is_active
LEFT JOIN organization.organization_units ou
       ON ou.id = a.organization_unit_id
WHERE u.employee_id IS NULL
GROUP BY u.id, u.username, u.display_name, u.email, u.created_at
ORDER BY u.username;

\echo '== 2. Employee master rows already available for linking =='
SELECT e.id AS employee_id,
       e.nip,
       e.full_name,
       e.email,
       e.employment_status,
       u.username AS linked_username
FROM identity.employees e
LEFT JOIN identity.users u ON u.employee_id = e.id
ORDER BY e.nip;

\echo '== 3. automatic email/username candidates (still require HR confirmation) =='
SELECT u.username,
       u.email AS account_email,
       e.nip,
       e.full_name,
       e.email AS employee_email
FROM identity.users u
JOIN identity.employees e
  ON lower(coalesce(u.email, '')) = lower(e.email)
  OR lower(u.username) = lower(e.nip)
WHERE u.employee_id IS NULL
ORDER BY u.username;

\echo '== 4. HR mapping template (fill confirmed_nip etc.; do not infer) =='
SELECT u.username,
       u.display_name AS account_display_name,
       u.email AS account_email,
       ''::text AS confirmed_nip,
       ''::text AS hr_full_name,
       ''::text AS hr_official_email,
       ''::text AS employment_status,
       ''::text AS division_unit_code,
       ''::text AS hr_decision_note
FROM identity.users u
WHERE u.employee_id IS NULL
ORDER BY u.username;
