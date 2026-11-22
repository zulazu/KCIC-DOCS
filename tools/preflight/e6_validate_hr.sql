-- Validate an HR employee extract before import/cutover. READ-ONLY against domain data.
-- Loads the CSV into a TEMP table only; nothing is inserted into identity/organization.
-- The CSV is copied into the container by run-hr-validation.sh to /tmp/e6_hr.csv.
--
-- Required header, exact order (run-hr-validation.sh rejects any other header):
--   nip,full_name,email,employment_status,division_unit_code
--
-- Gate rule: E6-HR-GATE must be PASS before any import or V18 planning.
-- A header-only file is a BLOCKER (E6-HR-00); it must never pass the gate.

\echo '== input =='
CREATE TEMP TABLE hr_staging (
    nip                text,
    full_name          text,
    email              text,
    employment_status  text,
    division_unit_code text
);

\copy hr_staging (nip, full_name, email, employment_status, division_unit_code) FROM '/tmp/e6_hr.csv' WITH (FORMAT csv, HEADER true)

SELECT count(*) AS staged_rows FROM hr_staging;

\echo '== E6-HR-00 the extract must contain at least one data row =='
SELECT 'E6-HR-00' AS check_code, 'BLOCKER' AS severity,
       CASE WHEN count(*) = 0 THEN 1 ELSE 0 END AS violations
FROM hr_staging;

\echo '== E6-HR-08 identity keys must not carry leading/trailing whitespace =='
SELECT nip, division_unit_code
FROM hr_staging
WHERE nip <> trim(nip) OR division_unit_code <> trim(division_unit_code)
LIMIT 100;
SELECT 'E6-HR-08' AS check_code, 'BLOCKER' AS severity, count(*) AS violations
FROM hr_staging
WHERE nip <> trim(nip) OR division_unit_code <> trim(division_unit_code);

\echo '== E6-HR-01 rows that are blank or still contain <placeholders> =='
SELECT nip, full_name, email, employment_status, division_unit_code
FROM hr_staging
WHERE coalesce(trim(nip), '') = ''
   OR coalesce(trim(full_name), '') = ''
   OR coalesce(trim(email), '') = ''
   OR coalesce(trim(employment_status), '') = ''
   OR coalesce(trim(division_unit_code), '') = ''
   OR nip LIKE '%<%>%' OR full_name LIKE '%<%>%' OR email LIKE '%<%>%'
   OR division_unit_code LIKE '%<%>%'
LIMIT 100;
SELECT 'E6-HR-01' AS check_code, 'BLOCKER' AS severity, count(*) AS violations
FROM hr_staging
WHERE coalesce(trim(nip), '') = ''
   OR coalesce(trim(full_name), '') = ''
   OR coalesce(trim(email), '') = ''
   OR coalesce(trim(employment_status), '') = ''
   OR coalesce(trim(division_unit_code), '') = ''
   OR nip LIKE '%<%>%' OR full_name LIKE '%<%>%' OR email LIKE '%<%>%'
   OR division_unit_code LIKE '%<%>%';

\echo '== E6-HR-02 invalid employment_status (ACTIVE|INACTIVE|TERMINATED) =='
SELECT nip, employment_status
FROM hr_staging
WHERE employment_status NOT IN ('ACTIVE', 'INACTIVE', 'TERMINATED')
LIMIT 100;
SELECT 'E6-HR-02' AS check_code, 'BLOCKER' AS severity, count(*) AS violations
FROM hr_staging
WHERE employment_status NOT IN ('ACTIVE', 'INACTIVE', 'TERMINATED');

\echo '== E6-HR-03 malformed email =='
SELECT nip, email
FROM hr_staging
WHERE email !~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$'
LIMIT 100;
SELECT 'E6-HR-03' AS check_code, 'BLOCKER' AS severity, count(*) AS violations
FROM hr_staging
WHERE email !~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$';

\echo '== E6-HR-04 duplicate NIP/email inside the file =='
SELECT 'nip' AS field, lower(trim(nip)) AS value, count(*) AS occurrences
FROM hr_staging GROUP BY lower(trim(nip)) HAVING count(*) > 1
UNION ALL
SELECT 'email', lower(trim(email)), count(*)
FROM hr_staging GROUP BY lower(trim(email)) HAVING count(*) > 1;
SELECT 'E6-HR-04' AS check_code, 'BLOCKER' AS severity, count(*) AS violations
FROM (
    SELECT lower(trim(nip)) FROM hr_staging GROUP BY lower(trim(nip)) HAVING count(*) > 1
    UNION ALL
    SELECT lower(trim(email)) FROM hr_staging GROUP BY lower(trim(email)) HAVING count(*) > 1
) duplicates;

\echo '== E6-HR-05 division codes missing, inactive, or not DIVISION =='
SELECT s.nip, s.division_unit_code,
       u.unit_code AS matched_code, u.unit_type, u.unit_status
FROM hr_staging s
LEFT JOIN organization.organization_units u
       ON lower(u.unit_code) = lower(trim(s.division_unit_code))
WHERE u.id IS NULL
   OR u.unit_status <> 'ACTIVE'
   OR u.unit_type IS DISTINCT FROM 'DIVISION'
LIMIT 100;
SELECT 'E6-HR-05' AS check_code, 'BLOCKER' AS severity, count(*) AS violations
FROM hr_staging s
LEFT JOIN organization.organization_units u
       ON lower(u.unit_code) = lower(trim(s.division_unit_code))
WHERE u.id IS NULL
   OR u.unit_status <> 'ACTIVE'
   OR u.unit_type IS DISTINCT FROM 'DIVISION';

\echo '== E6-HR-06 conflicts with existing employees (review before upsert) =='
SELECT s.nip AS csv_nip, e.nip AS existing_nip,
       s.full_name AS csv_name, e.full_name AS existing_name,
       s.email AS csv_email, e.email AS existing_email,
       CASE WHEN lower(e.email) <> lower(trim(s.email)) THEN 'EMAIL_CHANGED' ELSE 'MATCH' END AS note
FROM hr_staging s
JOIN identity.employees e ON lower(e.nip) = lower(trim(s.nip))
WHERE lower(e.email) <> lower(trim(s.email))
   OR lower(e.full_name) <> lower(trim(s.full_name))
LIMIT 100;
SELECT 'E6-HR-06' AS check_code, 'WARN' AS severity, count(*) AS violations
FROM hr_staging s
JOIN identity.employees e ON lower(e.nip) = lower(trim(s.nip))
WHERE lower(e.email) <> lower(trim(s.email))
   OR lower(e.full_name) <> lower(trim(s.full_name));

\echo '== E6-HR-07 email used by a different existing employee =='
SELECT s.nip AS csv_nip, e.nip AS existing_nip, lower(trim(s.email)) AS email
FROM hr_staging s
JOIN identity.employees e ON lower(e.email) = lower(trim(s.email))
WHERE lower(e.nip) <> lower(trim(s.nip))
LIMIT 100;
SELECT 'E6-HR-07' AS check_code, 'BLOCKER' AS severity, count(*) AS violations
FROM hr_staging s
JOIN identity.employees e ON lower(e.email) = lower(trim(s.email))
WHERE lower(e.nip) <> lower(trim(s.nip));

\echo '== E6-HR-GATE summary (import may proceed only when PASS) =='
WITH checks(check_code, severity, violations) AS (
    SELECT 'E6-HR-00', 'BLOCKER', CASE WHEN count(*) = 0 THEN 1 ELSE 0 END FROM hr_staging
    UNION ALL
    SELECT 'E6-HR-08', 'BLOCKER', count(*) FROM hr_staging
    WHERE nip <> trim(nip) OR division_unit_code <> trim(division_unit_code)
    UNION ALL
    SELECT 'E6-HR-01', 'BLOCKER', count(*) FROM hr_staging
    WHERE coalesce(trim(nip), '') = '' OR coalesce(trim(full_name), '') = ''
       OR coalesce(trim(email), '') = '' OR coalesce(trim(employment_status), '') = ''
       OR coalesce(trim(division_unit_code), '') = ''
       OR nip LIKE '%<%>%' OR full_name LIKE '%<%>%' OR email LIKE '%<%>%'
       OR division_unit_code LIKE '%<%>%'
    UNION ALL
    SELECT 'E6-HR-02', 'BLOCKER', count(*) FROM hr_staging
    WHERE employment_status NOT IN ('ACTIVE', 'INACTIVE', 'TERMINATED')
    UNION ALL
    SELECT 'E6-HR-03', 'BLOCKER', count(*) FROM hr_staging
    WHERE email !~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$'
    UNION ALL
    SELECT 'E6-HR-04', 'BLOCKER', count(*) FROM (
        SELECT lower(trim(nip)) FROM hr_staging GROUP BY lower(trim(nip)) HAVING count(*) > 1
        UNION ALL
        SELECT lower(trim(email)) FROM hr_staging GROUP BY lower(trim(email)) HAVING count(*) > 1
    ) duplicates
    UNION ALL
    SELECT 'E6-HR-05', 'BLOCKER', count(*)
    FROM hr_staging s
    LEFT JOIN organization.organization_units u
           ON lower(u.unit_code) = lower(trim(s.division_unit_code))
    WHERE u.id IS NULL OR u.unit_status <> 'ACTIVE' OR u.unit_type IS DISTINCT FROM 'DIVISION'
    UNION ALL
    SELECT 'E6-HR-06', 'WARN', count(*)
    FROM hr_staging s
    JOIN identity.employees e ON lower(e.nip) = lower(trim(s.nip))
    WHERE lower(e.email) <> lower(trim(s.email)) OR lower(e.full_name) <> lower(trim(s.full_name))
    UNION ALL
    SELECT 'E6-HR-07', 'BLOCKER', count(*)
    FROM hr_staging s
    JOIN identity.employees e ON lower(e.email) = lower(trim(s.email))
    WHERE lower(e.nip) <> lower(trim(s.nip))
)
SELECT check_code, severity, violations,
       CASE WHEN violations = 0 THEN 'PASS' ELSE 'FAIL' END AS result
FROM checks
UNION ALL
SELECT 'E6-HR-GATE', 'BLOCKER',
       sum(violations) FILTER (WHERE severity = 'BLOCKER'),
       CASE WHEN sum(violations) FILTER (WHERE severity = 'BLOCKER') = 0
            THEN 'PASS' ELSE 'FAIL' END
FROM checks;
