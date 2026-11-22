-- One-to-one Employee–User linkage (SCHEMA.md §21.4).
-- Nullable until the V18 cutover; a filled value is unique so one Employee has
-- at most one account and one account maps to at most one Employee.
ALTER TABLE identity.users
    ADD COLUMN employee_id UUID;

ALTER TABLE identity.users
    ADD CONSTRAINT users_employee_fk FOREIGN KEY (employee_id) REFERENCES identity.employees (id);

CREATE UNIQUE INDEX users_employee_uq ON identity.users (employee_id) WHERE employee_id IS NOT NULL;

-- Re-assert the employee permissions so upgrades that skipped V15 seeding still
-- converge on the same catalog.
INSERT INTO "authorization".permissions (id, permission_code, permission_name, description, module_code, created_at)
VALUES
    (gen_random_uuid(), 'employee.view', 'Lihat employee',
     'Melihat data employee, placement aktif, dan history placement.', 'identity', now()),
    (gen_random_uuid(), 'employee.manage', 'Kelola employee',
     'Membuat, memperbarui, dan memindahkan employee pada Biro/Division.', 'identity', now())
ON CONFLICT (permission_code) DO NOTHING;

INSERT INTO "authorization".role_permissions (role_id, permission_id, created_at)
SELECT r.id, p.id, now()
FROM "authorization".roles r
JOIN "authorization".permissions p ON p.permission_code IN ('employee.view', 'employee.manage')
WHERE r.role_code = 'SYSTEM_ADMIN'
ON CONFLICT DO NOTHING;
