-- Operational employment/account lifecycle check. READ-ONLY.
-- Companion to SECURITY.md §11 "Separate lifecycles": the backend denies login and
-- new grants for INACTIVE/TERMINATED employees, but the account row itself must also
-- be deactivated by administration. This check detects accounts that still bypass
-- that policy state.
--
-- Run: tools/ops/run-employment-check.sh
-- Evidence: docs/organization-identity/E8_OPERATIONAL_CHECK_<env>_<date>.txt

\echo '== E8-OPS-01 non-ACTIVE employees with an ACTIVE account (must be zero) =='
SELECT e.nip, e.full_name, e.employment_status, u.username, u.account_status
FROM identity.employees e
JOIN identity.users u ON u.employee_id = e.id
WHERE e.employment_status <> 'ACTIVE'
  AND u.account_status = 'ACTIVE'
ORDER BY e.nip
LIMIT 100;
SELECT 'E8-OPS-01' AS check_code, 'BLOCKER' AS severity, count(*) AS violations
FROM identity.employees e
JOIN identity.users u ON u.employee_id = e.id
WHERE e.employment_status <> 'ACTIVE'
  AND u.account_status = 'ACTIVE';

\echo '== E8-OPS-02 non-ACTIVE employees still holding an active placement (review) =='
SELECT e.nip, e.full_name, e.employment_status, ou.unit_code, p.valid_from
FROM organization.employee_placements p
JOIN identity.employees e ON e.id = p.employee_id
JOIN organization.organization_units ou ON ou.id = p.division_unit_id
WHERE p.is_active
  AND e.employment_status <> 'ACTIVE'
ORDER BY e.nip
LIMIT 100;
SELECT 'E8-OPS-02' AS check_code, 'WARN' AS severity, count(*) AS violations
FROM organization.employee_placements p
JOIN identity.employees e ON e.id = p.employee_id
WHERE p.is_active
  AND e.employment_status <> 'ACTIVE';

\echo '== E8-OPS-03 linked accounts with no employment row (review) =='
SELECT u.username, u.account_status
FROM identity.users u
LEFT JOIN identity.employees e ON e.id = u.employee_id
WHERE u.employee_id IS NOT NULL
  AND e.id IS NULL
ORDER BY u.username
LIMIT 100;
SELECT 'E8-OPS-03' AS check_code, 'WARN' AS severity, count(*) AS violations
FROM identity.users u
LEFT JOIN identity.employees e ON e.id = u.employee_id
WHERE u.employee_id IS NOT NULL
  AND e.id IS NULL;

\echo '== E8-OPS-GATE summary (BLOCKED while E8-OPS-01 has violations) =='
WITH checks(check_code, severity, violations) AS (
    SELECT 'E8-OPS-01', 'BLOCKER', count(*)
    FROM identity.employees e
    JOIN identity.users u ON u.employee_id = e.id
    WHERE e.employment_status <> 'ACTIVE'
      AND u.account_status = 'ACTIVE'
    UNION ALL
    SELECT 'E8-OPS-02', 'WARN', count(*)
    FROM organization.employee_placements p
    JOIN identity.employees e ON e.id = p.employee_id
    WHERE p.is_active
      AND e.employment_status <> 'ACTIVE'
    UNION ALL
    SELECT 'E8-OPS-03', 'WARN', count(*)
    FROM identity.users u
    LEFT JOIN identity.employees e ON e.id = u.employee_id
    WHERE u.employee_id IS NOT NULL
      AND e.id IS NULL
)
SELECT check_code,
       severity,
       violations,
       CASE WHEN violations = 0 THEN 'PASS' ELSE 'REVIEW' END AS result
FROM checks
UNION ALL
SELECT 'E8-OPS-GATE',
       'BLOCKER',
       sum(violations) FILTER (WHERE severity = 'BLOCKER'),
       CASE WHEN sum(violations) FILTER (WHERE severity = 'BLOCKER') = 0
            THEN 'PASS' ELSE 'BLOCKED' END
FROM checks;
