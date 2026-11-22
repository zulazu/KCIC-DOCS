-- E6 cutover preflight. READ-ONLY: no INSERT/UPDATE/DELETE/DDL.
-- Gate for V18 (identity.users.employee_id NOT NULL and
-- organization.organization_units.unit_type NOT NULL).
-- Any check returning violations > 0 blocks V18. No account exception is
-- approved for V18 (DEC-068): every user row must link to a verified Employee.
-- A future non-human identity requires a different enforcement design and an
-- explicit decision; do not relax this file silently.
--
-- Run: tools/preflight/run-e6-preflight.sh
-- Evidence: docs/organization-identity/E6_PREFLIGHT_<env>_<date>.txt

\echo '== E6-01 unlinked accounts (users.employee_id IS NULL) =='
SELECT u.username, u.account_status, u.created_at
FROM identity.users u
WHERE u.employee_id IS NULL
ORDER BY u.username
LIMIT 50;
SELECT 'E6-01' AS check_code, 'BLOCKER' AS severity, count(*) AS violations
FROM identity.users u
WHERE u.employee_id IS NULL;

\echo '== E6-02 employees without exactly one active placement =='
SELECT e.nip, e.full_name, coalesce(p.active_count, 0) AS active_placements
FROM identity.employees e
LEFT JOIN (
    SELECT employee_id, count(*) AS active_count
    FROM organization.employee_placements
    WHERE is_active
    GROUP BY employee_id
) p ON p.employee_id = e.id
WHERE coalesce(p.active_count, 0) <> 1
ORDER BY e.nip
LIMIT 50;
SELECT 'E6-02' AS check_code, 'BLOCKER' AS severity, count(*) AS violations
FROM identity.employees e
LEFT JOIN (
    SELECT employee_id, count(*) AS active_count
    FROM organization.employee_placements
    WHERE is_active
    GROUP BY employee_id
) p ON p.employee_id = e.id
WHERE coalesce(p.active_count, 0) <> 1;

\echo '== E6-03 duplicate NIP or email (defense in depth; unique indexes exist) =='
SELECT 'nip' AS field, lower(nip) AS value, count(*) AS occurrences
FROM identity.employees
GROUP BY lower(nip) HAVING count(*) > 1
UNION ALL
SELECT 'email', lower(email), count(*)
FROM identity.employees
GROUP BY lower(email) HAVING count(*) > 1;
SELECT 'E6-03' AS check_code, 'BLOCKER' AS severity, count(*) AS violations
FROM (
    SELECT lower(nip) FROM identity.employees GROUP BY lower(nip) HAVING count(*) > 1
    UNION ALL
    SELECT lower(email) FROM identity.employees GROUP BY lower(email) HAVING count(*) > 1
) duplicates;

\echo '== E6-04 active placements on missing, inactive, or non-DIVISION units =='
SELECT p.id, e.nip, u.unit_code, u.unit_type, u.unit_status
FROM organization.employee_placements p
JOIN organization.organization_units u ON u.id = p.division_unit_id
JOIN identity.employees e ON e.id = p.employee_id
WHERE p.is_active
  AND (u.unit_type IS DISTINCT FROM 'DIVISION' OR u.unit_status <> 'ACTIVE')
LIMIT 50;
SELECT 'E6-04' AS check_code, 'BLOCKER' AS severity, count(*) AS violations
FROM organization.employee_placements p
JOIN organization.organization_units u ON u.id = p.division_unit_id
WHERE p.is_active
  AND (u.unit_type IS DISTINCT FROM 'DIVISION' OR u.unit_status <> 'ACTIVE');

\echo '== E6-05 units without unit_type (V18 is table-wide NOT NULL, including inactive units) =='
SELECT id, unit_code, unit_name, unit_status
FROM organization.organization_units
WHERE unit_type IS NULL
ORDER BY unit_status, unit_code
LIMIT 50;
SELECT 'E6-05' AS check_code, 'BLOCKER' AS severity, count(*) AS violations
FROM organization.organization_units
WHERE unit_type IS NULL;

\echo '== E6-06 hierarchy shape violations =='
SELECT c.id, c.unit_code, c.unit_type, c.parent_id, p.unit_type AS parent_type
FROM organization.organization_units c
LEFT JOIN organization.organization_units p ON p.id = c.parent_id
WHERE c.unit_type IS NOT NULL
  AND (
      (c.unit_type = 'COMPANY' AND c.parent_id IS NOT NULL)
      OR (c.unit_type = 'DIRECTORATE' AND p.unit_type IS DISTINCT FROM 'COMPANY')
      OR (c.unit_type = 'DEPARTMENT' AND p.unit_type IS DISTINCT FROM 'DIRECTORATE')
      OR (c.unit_type = 'DIVISION' AND p.unit_type IS DISTINCT FROM 'DEPARTMENT')
      OR (c.unit_type <> 'COMPANY' AND c.parent_id IS NULL)
  )
ORDER BY c.unit_code
LIMIT 50;
SELECT 'E6-06' AS check_code, 'BLOCKER' AS severity, count(*) AS violations
FROM organization.organization_units c
LEFT JOIN organization.organization_units p ON p.id = c.parent_id
WHERE c.unit_type IS NOT NULL
  AND (
      (c.unit_type = 'COMPANY' AND c.parent_id IS NOT NULL)
      OR (c.unit_type = 'DIRECTORATE' AND p.unit_type IS DISTINCT FROM 'COMPANY')
      OR (c.unit_type = 'DEPARTMENT' AND p.unit_type IS DISTINCT FROM 'DIRECTORATE')
      OR (c.unit_type = 'DIVISION' AND p.unit_type IS DISTINCT FROM 'DEPARTMENT')
      OR (c.unit_type <> 'COMPANY' AND c.parent_id IS NULL)
  );

\echo '== E6-07 active COMPANY roots (must be exactly one) =='
SELECT id, unit_code, unit_name, unit_status
FROM organization.organization_units
WHERE unit_type = 'COMPANY' AND unit_status = 'ACTIVE'
ORDER BY unit_code;
SELECT 'E6-07' AS check_code, 'BLOCKER' AS severity,
       CASE WHEN count(*) = 1 THEN 0 ELSE abs(count(*) - 1) END AS violations
FROM organization.organization_units
WHERE unit_type = 'COMPANY' AND unit_status = 'ACTIVE';

\echo '== E6-08 ACTIVE accounts linked to non-ACTIVE employees =='
SELECT u.username, u.account_status, e.nip, e.employment_status
FROM identity.users u
JOIN identity.employees e ON e.id = u.employee_id
WHERE u.account_status = 'ACTIVE'
  AND e.employment_status <> 'ACTIVE'
ORDER BY u.username
LIMIT 50;
SELECT 'E6-08' AS check_code, 'BLOCKER' AS severity, count(*) AS violations
FROM identity.users u
JOIN identity.employees e ON e.id = u.employee_id
WHERE u.account_status = 'ACTIVE'
  AND e.employment_status <> 'ACTIVE';

\echo '== E6-09 employee master data quality (blank, placeholder, untrimmed, malformed email) =='
SELECT e.id, e.nip, e.full_name, e.email
FROM identity.employees e
WHERE coalesce(trim(e.nip), '') = ''
   OR coalesce(trim(e.full_name), '') = ''
   OR coalesce(trim(e.email), '') = ''
   OR e.nip <> trim(e.nip) OR e.full_name <> trim(e.full_name) OR e.email <> trim(e.email)
   OR e.nip LIKE '%<%>%' OR e.full_name LIKE '%<%>%' OR e.email LIKE '%<%>%'
   OR e.email !~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$'
ORDER BY e.nip
LIMIT 50;
SELECT 'E6-09' AS check_code, 'BLOCKER' AS severity, count(*) AS violations
FROM identity.employees e
WHERE coalesce(trim(e.nip), '') = ''
   OR coalesce(trim(e.full_name), '') = ''
   OR coalesce(trim(e.email), '') = ''
   OR e.nip <> trim(e.nip) OR e.full_name <> trim(e.full_name) OR e.email <> trim(e.email)
   OR e.nip LIKE '%<%>%' OR e.full_name LIKE '%<%>%' OR e.email LIKE '%<%>%'
   OR e.email !~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$';

\echo '== E6-GATE summary (BLOCKED while any BLOCKER has violations) =='
WITH checks(check_code, severity, violations) AS (
    SELECT 'E6-01', 'BLOCKER', count(*) FROM identity.users WHERE employee_id IS NULL
    UNION ALL
    SELECT 'E6-02', 'BLOCKER', count(*)
    FROM identity.employees e
    LEFT JOIN (
        SELECT employee_id, count(*) AS active_count
        FROM organization.employee_placements
        WHERE is_active
        GROUP BY employee_id
    ) p ON p.employee_id = e.id
    WHERE coalesce(p.active_count, 0) <> 1
    UNION ALL
    SELECT 'E6-03', 'BLOCKER', count(*)
    FROM (
        SELECT lower(nip) FROM identity.employees GROUP BY lower(nip) HAVING count(*) > 1
        UNION ALL
        SELECT lower(email) FROM identity.employees GROUP BY lower(email) HAVING count(*) > 1
    ) duplicates
    UNION ALL
    SELECT 'E6-04', 'BLOCKER', count(*)
    FROM organization.employee_placements p
    JOIN organization.organization_units u ON u.id = p.division_unit_id
    WHERE p.is_active
      AND (u.unit_type IS DISTINCT FROM 'DIVISION' OR u.unit_status <> 'ACTIVE')
    UNION ALL
    SELECT 'E6-05', 'BLOCKER', count(*)
    FROM organization.organization_units
    WHERE unit_type IS NULL
    UNION ALL
    SELECT 'E6-06', 'BLOCKER', count(*)
    FROM organization.organization_units c
    LEFT JOIN organization.organization_units p ON p.id = c.parent_id
    WHERE c.unit_type IS NOT NULL
      AND (
          (c.unit_type = 'COMPANY' AND c.parent_id IS NOT NULL)
          OR (c.unit_type = 'DIRECTORATE' AND p.unit_type IS DISTINCT FROM 'COMPANY')
          OR (c.unit_type = 'DEPARTMENT' AND p.unit_type IS DISTINCT FROM 'DIRECTORATE')
          OR (c.unit_type = 'DIVISION' AND p.unit_type IS DISTINCT FROM 'DEPARTMENT')
          OR (c.unit_type <> 'COMPANY' AND c.parent_id IS NULL)
      )
    UNION ALL
    SELECT 'E6-07', 'BLOCKER',
           CASE WHEN count(*) = 1 THEN 0 ELSE abs(count(*) - 1) END
    FROM organization.organization_units
    WHERE unit_type = 'COMPANY' AND unit_status = 'ACTIVE'
    UNION ALL
    SELECT 'E6-08', 'BLOCKER', count(*)
    FROM identity.users u
    JOIN identity.employees e ON e.id = u.employee_id
    WHERE u.account_status = 'ACTIVE'
      AND e.employment_status <> 'ACTIVE'
    UNION ALL
    SELECT 'E6-09', 'BLOCKER', count(*)
    FROM identity.employees e
    WHERE coalesce(trim(e.nip), '') = ''
       OR coalesce(trim(e.full_name), '') = ''
       OR coalesce(trim(e.email), '') = ''
       OR e.nip <> trim(e.nip) OR e.full_name <> trim(e.full_name) OR e.email <> trim(e.email)
       OR e.nip LIKE '%<%>%' OR e.full_name LIKE '%<%>%' OR e.email LIKE '%<%>%'
       OR e.email !~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$'
)
SELECT check_code,
       severity,
       violations,
       CASE WHEN violations = 0 THEN 'PASS' ELSE 'FAIL' END AS result
FROM checks
UNION ALL
SELECT 'E6-GATE',
       'BLOCKER',
       sum(violations),
       CASE WHEN sum(violations) = 0 THEN 'PASS' ELSE 'BLOCKED' END
FROM checks;

\echo '== E6-INFO descendant widening review (must be reviewed, not necessarily zero) =='
SELECT 'resource_permissions' AS source, count(*) AS include_descendants_rows
FROM "authorization".resource_permissions
WHERE include_descendants
UNION ALL
SELECT 'user_role_assignments', count(*)
FROM "authorization".user_role_assignments
WHERE include_descendants;

\echo '== E6-INFO active legacy unit assignments on linked accounts (review; do not auto-revoke) =='
-- Unit scope is the union of active legacy assignments and the active placement.
-- A legacy row that differs from the placement can widen scoped permissions; decide
-- per row whether it is intended access or redundant residue. No bulk delete.
SELECT u.username, e.nip,
       lu.unit_code AS legacy_unit_code, lu.unit_type AS legacy_unit_type,
       a.assignment_type,
       pu.unit_code AS placement_unit_code,
       CASE WHEN pu.id = a.organization_unit_id THEN 'MATCHES_PLACEMENT'
            ELSE 'DIFFERS_FROM_PLACEMENT' END AS review
FROM organization.user_organization_assignments a
JOIN identity.users u ON u.id = a.user_id
JOIN identity.employees e ON e.id = u.employee_id
JOIN organization.organization_units lu ON lu.id = a.organization_unit_id
LEFT JOIN organization.employee_placements p ON p.employee_id = e.id AND p.is_active
LEFT JOIN organization.organization_units pu ON pu.id = p.division_unit_id
WHERE a.is_active
ORDER BY u.username, lu.unit_code;

SELECT 'E6-INFO-legacy-linked' AS check_code,
       count(*) AS active_rows,
       count(*) FILTER (WHERE pu.id IS DISTINCT FROM a.organization_unit_id) AS differ_from_placement
FROM organization.user_organization_assignments a
JOIN identity.users u ON u.id = a.user_id
JOIN identity.employees e ON e.id = u.employee_id
LEFT JOIN organization.employee_placements p ON p.employee_id = e.id AND p.is_active
LEFT JOIN organization.organization_units pu ON pu.id = p.division_unit_id
WHERE a.is_active;
