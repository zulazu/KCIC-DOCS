-- Pilot role model: PIC, Admin, Administrator, User (Employee).
-- Baseline roles from V11 are kept so existing assignments and audit history stay valid.
-- ADMINISTRATOR mirrors SYSTEM_ADMIN governance; content access still requires ACL/ownership.

INSERT INTO "authorization".roles (id, role_code, role_name, description, is_system_role, role_status, created_at, updated_at)
VALUES
    (gen_random_uuid(), 'ADMINISTRATOR', 'Administrator', 'Tata kelola penuh aplikasi: akun, role, organisasi, master data, audit, dan operasional konten.', TRUE, 'ACTIVE', now(), now()),
    (gen_random_uuid(), 'ADMIN', 'Admin', 'Operasional dokumen dan workflow: repository, template, request, distribusi, dan audit; tanpa manajemen akun/role/organisasi.', TRUE, 'ACTIVE', now(), now()),
    (gen_random_uuid(), 'PIC', 'PIC', 'Penanggung jawab unit: kelola dokumen dan request dalam scope unit, distribusi, dan template.', TRUE, 'ACTIVE', now(), now())
ON CONFLICT (role_code) DO NOTHING;

UPDATE "authorization".roles
SET role_name = 'User (Employee)',
    description = 'Menemukan, melihat, mengunduh, dan meminta dokumen.',
    updated_at = now()
WHERE role_code = 'EMPLOYEE';

-- ADMINISTRATOR receives the full capability catalog, same invariant as SYSTEM_ADMIN.
INSERT INTO "authorization".role_permissions (role_id, permission_id, created_at)
SELECT r.id, p.id, now()
FROM "authorization".roles r
CROSS JOIN "authorization".permissions p
WHERE r.role_code = 'ADMINISTRATOR'
ON CONFLICT DO NOTHING;

INSERT INTO "authorization".role_permissions (role_id, permission_id, created_at)
SELECT r.id, p.id, now()
FROM "authorization".roles r
JOIN "authorization".permissions p ON p.permission_code IN (
    'employee.view',
    'workspace.view', 'workspace.manage',
    'folder.view', 'folder.create', 'folder.update', 'folder.archive',
    'document.view', 'document.preview', 'document.download', 'document.create', 'document.upload',
    'document.update_metadata', 'document.create_version', 'document.archive', 'document.restore',
    'document.share', 'document.manage_access',
    'request.create', 'request.view_own', 'request.view_assigned',
    'request.approve', 'request.reject', 'request.fulfill',
    'distribution.create', 'distribution.view_own', 'distribution.view_received', 'distribution.revoke',
    'template.view', 'template.create', 'template.publish', 'template.archive',
    'notification.view', 'notification.manage',
    'audit.view'
)
WHERE r.role_code = 'ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO "authorization".role_permissions (role_id, permission_id, created_at)
SELECT r.id, p.id, now()
FROM "authorization".roles r
JOIN "authorization".permissions p ON p.permission_code IN (
    'workspace.view',
    'folder.view', 'folder.create', 'folder.update',
    'document.view', 'document.preview', 'document.download', 'document.create', 'document.upload',
    'document.update_metadata', 'document.create_version', 'document.share',
    'request.create', 'request.view_own', 'request.view_assigned',
    'request.approve', 'request.reject', 'request.fulfill',
    'distribution.create', 'distribution.view_own', 'distribution.view_received', 'distribution.revoke',
    'template.view', 'template.create',
    'notification.view'
)
WHERE r.role_code = 'PIC'
ON CONFLICT DO NOTHING;