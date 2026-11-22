-- Read-only unit inventory to support the typed-hierarchy mapping decision.
-- Run: docker exec -i kcic-postgres psql -X -U kcic -d kcic_connect < tools/preflight/e6_unit_inventory.sql

\echo '== all units with type/parent and dependency counts =='
SELECT u.unit_code,
       u.unit_name,
       u.unit_type,
       u.unit_status,
       p.unit_code AS parent_code,
       (SELECT count(*) FROM organization.organization_units c WHERE c.parent_id = u.id) AS children,
       (SELECT count(*) FROM organization.employee_placements ep
         WHERE ep.division_unit_id = u.id AND ep.is_active) AS active_placements,
       (SELECT count(*) FROM organization.user_organization_assignments a
         WHERE a.organization_unit_id = u.id AND a.is_active) AS active_legacy_assignments
FROM organization.organization_units u
LEFT JOIN organization.organization_units p ON p.id = u.parent_id
ORDER BY u.unit_status, u.unit_type NULLS FIRST, u.unit_code;

\echo '== units without a type (mapping worklist; V18 requires every unit typed) =='
SELECT u.unit_code, u.unit_name, u.description, u.unit_status
FROM organization.organization_units u
WHERE u.unit_type IS NULL
ORDER BY u.unit_status, u.unit_code;

\echo '== active companies (must be exactly one after mapping) =='
SELECT unit_code, unit_name, parent_id
FROM organization.organization_units
WHERE unit_type = 'COMPANY' AND unit_status = 'ACTIVE'
ORDER BY unit_code;
