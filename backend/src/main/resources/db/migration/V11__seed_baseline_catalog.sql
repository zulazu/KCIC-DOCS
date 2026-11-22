-- Baseline role and permission catalog (SCHEMA.md §6, §19). Idempotent.
INSERT INTO "authorization".roles (id, role_code, role_name, description, is_system_role, role_status, created_at, updated_at)
VALUES
    (gen_random_uuid(), 'SYSTEM_ADMIN', 'System Administrator', 'Akun, role, permission, organisasi, dan konfigurasi aplikasi.', TRUE, 'ACTIVE', now(), now()),
    (gen_random_uuid(), 'DOCUMENT_ADMIN', 'Document Administrator', 'Tata kelola repository, master data dokumen, dan template.', TRUE, 'ACTIVE', now(), now()),
    (gen_random_uuid(), 'UNIT_MANAGER', 'Unit Manager', 'Pengelolaan workspace dan proses dokumen dalam unit organisasi.', TRUE, 'ACTIVE', now(), now()),
    (gen_random_uuid(), 'CONTRIBUTOR', 'Contributor', 'Menyimpan, memperbarui, membagikan, dan meminta dokumen.', TRUE, 'ACTIVE', now(), now()),
    (gen_random_uuid(), 'EMPLOYEE', 'Employee', 'Menemukan, melihat, mengunduh, dan meminta dokumen.', TRUE, 'ACTIVE', now(), now()),
    (gen_random_uuid(), 'AUDITOR', 'Auditor', 'Menelusuri audit event tanpa otomatis membaca isi dokumen.', TRUE, 'ACTIVE', now(), now())
ON CONFLICT (role_code) DO NOTHING;

INSERT INTO "authorization".permissions (id, permission_code, permission_name, description, module_code, created_at)
VALUES
    (gen_random_uuid(), 'user.manage', 'Kelola user', 'Membuat dan mengubah akun serta status akun.', 'identity', now()),
    (gen_random_uuid(), 'role.manage', 'Kelola role dan permission', 'Mengubah role, mapping permission, dan assignment.', 'authorization', now()),
    (gen_random_uuid(), 'organization.manage', 'Kelola organisasi', 'Mengelola unit organisasi dan assignment user.', 'organization', now()),
    (gen_random_uuid(), 'master_data.manage', 'Kelola master data', 'Kategori, tipe, klasifikasi, tag, dan file policy.', 'masterdata', now()),
    (gen_random_uuid(), 'workspace.view', 'Lihat workspace', 'Melihat workspace yang diizinkan.', 'repository', now()),
    (gen_random_uuid(), 'workspace.manage', 'Kelola workspace', 'Membuat, mengubah, dan mengarsipkan workspace.', 'repository', now()),
    (gen_random_uuid(), 'folder.view', 'Lihat folder', 'Melihat struktur folder.', 'repository', now()),
    (gen_random_uuid(), 'folder.create', 'Buat folder', 'Membuat folder dan sub-folder.', 'repository', now()),
    (gen_random_uuid(), 'folder.update', 'Ubah folder', 'Mengganti nama dan memindahkan folder.', 'repository', now()),
    (gen_random_uuid(), 'folder.archive', 'Arsipkan folder', 'Mengarsipkan folder.', 'repository', now()),
    (gen_random_uuid(), 'document.view', 'Lihat dokumen', 'Melihat detail dan metadata dokumen.', 'repository', now()),
    (gen_random_uuid(), 'document.preview', 'Preview dokumen', 'Membuka preview konten dokumen.', 'repository', now()),
    (gen_random_uuid(), 'document.download', 'Unduh dokumen', 'Mengunduh konten dokumen.', 'repository', now()),
    (gen_random_uuid(), 'document.create', 'Buat dokumen', 'Membuat identitas dokumen.', 'repository', now()),
    (gen_random_uuid(), 'document.upload', 'Unggah dokumen', 'Mengunggah file versi dokumen.', 'repository', now()),
    (gen_random_uuid(), 'document.update_metadata', 'Ubah metadata dokumen', 'Memperbarui metadata dan owner.', 'repository', now()),
    (gen_random_uuid(), 'document.create_version', 'Buat versi dokumen', 'Menambahkan versi atau link baru.', 'repository', now()),
    (gen_random_uuid(), 'document.archive', 'Arsipkan dokumen', 'Mengarsipkan dokumen.', 'repository', now()),
    (gen_random_uuid(), 'document.restore', 'Pulihkan dokumen', 'Memulihkan dokumen dari arsip.', 'repository', now()),
    (gen_random_uuid(), 'document.share', 'Bagikan dokumen', 'Mengirim distribusi dokumen.', 'repository', now()),
    (gen_random_uuid(), 'document.manage_access', 'Kelola akses dokumen', 'Memberi dan mencabut akses resource.', 'repository', now()),
    (gen_random_uuid(), 'request.create', 'Buat request', 'Mengajukan permintaan dokumen atau akses.', 'workflow', now()),
    (gen_random_uuid(), 'request.view_own', 'Lihat request sendiri', 'Melihat request yang diajukan sendiri.', 'workflow', now()),
    (gen_random_uuid(), 'request.view_assigned', 'Lihat request yang ditugaskan', 'Melihat request yang ditugaskan atau dalam scope.', 'workflow', now()),
    (gen_random_uuid(), 'request.approve', 'Setujui request', 'Menyetujui request yang ditugaskan.', 'workflow', now()),
    (gen_random_uuid(), 'request.reject', 'Tolak request', 'Menolak request dengan alasan.', 'workflow', now()),
    (gen_random_uuid(), 'request.fulfill', 'Penuhi request', 'Memenuhi request dengan akses atau dokumen.', 'workflow', now()),
    (gen_random_uuid(), 'distribution.create', 'Kirim distribusi', 'Mengirim dokumen kepada penerima internal.', 'distribution', now()),
    (gen_random_uuid(), 'distribution.view_own', 'Lihat distribusi terkirim', 'Melihat distribusi yang dikirim.', 'distribution', now()),
    (gen_random_uuid(), 'distribution.view_received', 'Lihat distribusi diterima', 'Melihat distribusi yang diterima.', 'distribution', now()),
    (gen_random_uuid(), 'distribution.revoke', 'Cabut distribusi', 'Mencabut distribusi dan grant terkait.', 'distribution', now()),
    (gen_random_uuid(), 'template.view', 'Lihat template', 'Melihat katalog template.', 'template', now()),
    (gen_random_uuid(), 'template.create', 'Buat template', 'Menetapkan dokumen sebagai template.', 'template', now()),
    (gen_random_uuid(), 'template.publish', 'Publikasikan template', 'Menerbitkan template.', 'template', now()),
    (gen_random_uuid(), 'template.archive', 'Arsipkan template', 'Mengarsipkan template.', 'template', now()),
    (gen_random_uuid(), 'notification.view', 'Lihat notifikasi', 'Melihat notifikasi sendiri.', 'notification', now()),
    (gen_random_uuid(), 'notification.manage', 'Kelola notifikasi', 'Menandai notifikasi dibaca.', 'notification', now()),
    (gen_random_uuid(), 'audit.view', 'Lihat audit trail', 'Mencari audit event sesuai scope.', 'audit', now())
ON CONFLICT (permission_code) DO NOTHING;

-- SYSTEM_ADMIN receives the full capability catalog; content access still requires ACL or ownership.
INSERT INTO "authorization".role_permissions (role_id, permission_id, created_at)
SELECT r.id, p.id, now()
FROM "authorization".roles r
CROSS JOIN "authorization".permissions p
WHERE r.role_code = 'SYSTEM_ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO "authorization".role_permissions (role_id, permission_id, created_at)
SELECT r.id, p.id, now()
FROM "authorization".roles r
JOIN "authorization".permissions p ON p.permission_code IN (
    'master_data.manage', 'workspace.view', 'workspace.manage',
    'folder.view', 'folder.create', 'folder.update', 'folder.archive',
    'document.view', 'document.preview', 'document.download', 'document.create', 'document.upload',
    'document.update_metadata', 'document.create_version', 'document.archive', 'document.restore',
    'document.share', 'document.manage_access',
    'request.view_assigned', 'request.approve', 'request.reject', 'request.fulfill',
    'distribution.view_own', 'distribution.revoke',
    'template.view', 'template.create', 'template.publish', 'template.archive',
    'notification.view'
)
WHERE r.role_code = 'DOCUMENT_ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO "authorization".role_permissions (role_id, permission_id, created_at)
SELECT r.id, p.id, now()
FROM "authorization".roles r
JOIN "authorization".permissions p ON p.permission_code IN (
    'workspace.view', 'workspace.manage',
    'folder.view', 'folder.create', 'folder.update', 'folder.archive',
    'document.view', 'document.preview', 'document.download', 'document.create', 'document.upload',
    'document.update_metadata', 'document.create_version', 'document.archive', 'document.restore',
    'document.share', 'document.manage_access',
    'request.view_assigned', 'request.approve', 'request.reject', 'request.fulfill',
    'distribution.create', 'distribution.view_own', 'distribution.revoke',
    'template.view', 'notification.view'
)
WHERE r.role_code = 'UNIT_MANAGER'
ON CONFLICT DO NOTHING;

INSERT INTO "authorization".role_permissions (role_id, permission_id, created_at)
SELECT r.id, p.id, now()
FROM "authorization".roles r
JOIN "authorization".permissions p ON p.permission_code IN (
    'workspace.view', 'folder.view', 'folder.create', 'folder.update',
    'document.view', 'document.preview', 'document.download', 'document.create', 'document.upload',
    'document.update_metadata', 'document.create_version', 'document.archive', 'document.restore',
    'document.share', 'document.manage_access',
    'request.create', 'request.view_own',
    'distribution.create', 'distribution.view_own', 'distribution.revoke',
    'template.view', 'template.create', 'notification.view'
)
WHERE r.role_code = 'CONTRIBUTOR'
ON CONFLICT DO NOTHING;

INSERT INTO "authorization".role_permissions (role_id, permission_id, created_at)
SELECT r.id, p.id, now()
FROM "authorization".roles r
JOIN "authorization".permissions p ON p.permission_code IN (
    'workspace.view', 'folder.view',
    'document.view', 'document.preview', 'document.download',
    'request.create', 'request.view_own',
    'distribution.view_received',
    'template.view', 'notification.view'
)
WHERE r.role_code = 'EMPLOYEE'
ON CONFLICT DO NOTHING;

INSERT INTO "authorization".role_permissions (role_id, permission_id, created_at)
SELECT r.id, p.id, now()
FROM "authorization".roles r
JOIN "authorization".permissions p ON p.permission_code IN ('audit.view', 'notification.view')
WHERE r.role_code = 'AUDITOR'
ON CONFLICT DO NOTHING;

-- Provisional master data so the pilot is operable; final values require business approval.
INSERT INTO master.document_categories (id, parent_id, category_code, category_name, description, is_active, created_at, updated_at)
VALUES (gen_random_uuid(), NULL, 'GENERAL', 'General', 'Kategori umum sementara sampai struktur kategori resmi disetujui.', TRUE, now(), now())
ON CONFLICT (category_code) DO NOTHING;

INSERT INTO master.document_types (id, type_code, type_name, description, is_active, created_at, updated_at)
VALUES
    (gen_random_uuid(), 'SOP', 'SOP', NULL, TRUE, now(), now()),
    (gen_random_uuid(), 'WORK_INSTRUCTION', 'Work Instruction', NULL, TRUE, now(), now()),
    (gen_random_uuid(), 'REPORT', 'Report', NULL, TRUE, now(), now()),
    (gen_random_uuid(), 'MEMO', 'Memo', NULL, TRUE, now(), now()),
    (gen_random_uuid(), 'FORM', 'Form', NULL, TRUE, now(), now()),
    (gen_random_uuid(), 'CONTRACT', 'Contract/Agreement', NULL, TRUE, now(), now()),
    (gen_random_uuid(), 'MINUTES_OF_MEETING', 'Minutes of Meeting', NULL, TRUE, now(), now()),
    (gen_random_uuid(), 'TECHNICAL_DOCUMENT', 'Technical Document', NULL, TRUE, now(), now())
ON CONFLICT (type_code) DO NOTHING;

INSERT INTO master.document_classifications (id, classification_code, classification_name, sensitivity_rank, description, is_active, created_at, updated_at)
VALUES
    (gen_random_uuid(), 'INTERNAL', 'Internal', 1, 'Baseline sementara; menunggu persetujuan kebijakan klasifikasi.', TRUE, now(), now()),
    (gen_random_uuid(), 'CONFIDENTIAL', 'Confidential', 2, 'Baseline sementara; menunggu persetujuan kebijakan klasifikasi.', TRUE, now(), now()),
    (gen_random_uuid(), 'RESTRICTED', 'Restricted', 3, 'Baseline sementara; menunggu persetujuan kebijakan klasifikasi.', TRUE, now(), now())
ON CONFLICT (classification_code) DO NOTHING;

INSERT INTO master.file_policies (id, policy_code, policy_name, allowed_extensions, allowed_mime_types, max_file_size_bytes, is_default, is_active, created_at, updated_at)
VALUES (
    gen_random_uuid(),
    'PILOT_DEFAULT',
    'Pilot default (PROVISIONAL)',
    ARRAY['pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'txt', 'csv'],
    ARRAY[
        'application/pdf',
        'application/msword',
        'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
        'application/vnd.ms-excel',
        'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        'application/vnd.ms-powerpoint',
        'application/vnd.openxmlformats-officedocument.presentationml.presentation',
        'text/plain',
        'text/csv',
        'application/csv'
    ],
    26214400,
    TRUE,
    TRUE,
    now(),
    now()
)
ON CONFLICT (policy_code) DO NOTHING;
