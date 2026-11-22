# KCIC Connect — Phase E0 Repository Audit (Organization & Identity)

| Metadata | Nilai |
|---|---|
| Dokumen | Organization & Identity Enhancement — E0 Audit |
| Versi | 1.0 |
| Tanggal | 22 November 2026 |
| Status | E0 COMPLETE — audit dan perencanaan; belum ada perubahan aplikasi |
| Baseline yang diaudit | Phase 9 selesai (Flyway V1–V13, backend 40 test, frontend gates hijau) |
| Keputusan baru | Locked Product Decisions pada brief Organization & Identity Enhancement |

## 1. Tujuan dan Batas Audit

Dokumen ini membandingkan implementasi aktual repository dengan keputusan terbaru mengenai:

1. Organization master berjenjang `KCIC → Directorate → Department → Biro/Division`.
2. Employee master wajib NIP, full name, email, employment status, dan satu active placement pada Biro/Division.
3. Satu Employee tepat satu User Account; data Employee tidak diduplikasi sebagai sumber independen di User.
4. Authorization berbasis Directorate/Department/Biro/Division/Employee dengan descendant membership yang eksplisit, tanpa memperluas grant lama dan tanpa melemahkan explicit DENY.

Audit ini **tidak mengubah aplikasi, schema, API, atau data**. Semua perubahan diusulkan untuk fase E1–E8.

## 2. Metode dan Sumber Bukti

1. Membaca `AGENTS.md`, `PRD.md`, `ARCHITECTURE.md`, `SCHEMA.md`, `DESIGN_SYSTEM.md`, `IMPLEMENTATION_PLAN.md`, `docs/DECISIONS.md`, `docs/SECURITY.md`.
2. Membaca seluruh Flyway migration `V1`–`V13` dan memeriksa `flyway_schema_history` pada database dev (`kcic-postgres`): versi terakhir **V13**, semua sukses.
3. Membaca source identity, organization, authorization, repository ACL, workflow routing, distribution, audit, serta unit/integration test.
4. Membaca frontend administration (users, organization, roles, access grant) dan navigation/capability gating.
5. Menjalankan query baca-saja ke database dev untuk inventory data aktual (tidak ada mutasi).
6. Memeriksa dokumentasi terhadap kode untuk menemukan discrepancy.

Tidak ada migration, DDL, seed, atau perubahan data yang dijalankan pada fase ini.

## 3. Snapshot Repository dan Runtime

| Item | Kondisi aktual |
|---|---|
| Backend | Spring Boot 3.5.16, Java release 21, modular monolith |
| Frontend | React 19 + TypeScript strict + Vite 8 + Tailwind 4 |
| Database | PostgreSQL 16; schema `identity`, `organization`, `authorization`, `master`, `repository`, `workflow`, `distribution`, `template`, `notification`, `audit` |
| Flyway | V1–V13 applied |
| Identity | `identity.users` + Spring Session JDBC |
| Organization | `organization.organization_units` + `organization.user_organization_assignments` |
| ACL | RBAC (`roles`, `permissions`, `role_permissions`, `user_role_assignments`) + `resource_permissions` |
| Storage | `StorageAdapter` (MinIO/S3 + filesystem dev) — tidak terpengaruh E-series |
| Test | 40 backend integration tests; 12 frontend unit tests; 7 Playwright smoke tests |

## 4. Kondisi Aktual — Identity

### 4.1 Schema

`identity.users` (`V2__identity_users_and_sessions.sql`) memuat:

- `username`, `email`, `display_name`, `password_hash`, `account_status`, `must_change_password`, lockout fields, timestamps, actor, `row_version`.
- Unique case-insensitive `username` dan partial unique `email`.
- Tidak ada `employee_id`, `nip`, employment status, atau relasi ke Employee.

### 4.2 Perilaku aplikasi

- `UserAdminService.create` membuat User langsung dari `username`, `displayName`, `email`, dan temporary password — tidak ada konsep Employee.
- `User.updateProfile` menjadikan `display_name` dan `email` milik User sebagai sumber data identitas.
- Login memakai username atau email dari `identity.users`.
- Bootstrap admin dibuat dari environment dan tidak memiliki organizational placement.
- Audit enrichment menggunakan nama User.

### 4.3 Inventory data aktual

| Username | Account | Role aktif | Placement aktif | Catatan |
|---|---|---|---|---|
| `admin` | ACTIVE | SYSTEM_ADMIN | Tidak ada | bootstrap; bukan Employee |
| `auditor1` | ACTIVE | AUDITOR | Tidak ada | fixture UAT |
| `employee1` | ACTIVE | EMPLOYEE | PRIMARY `KCIC-HO` | unit untyped |
| `employee2` | ACTIVE, must change password | Tidak ada | Tidak ada | tanpa role dan placement |
| `employee3` | ACTIVE | EMPLOYEE | SECONDARY `KCIC-HO` | bukan PRIMARY; unit untyped |

3 dari 5 user tidak memiliki email. Tidak ada data NIP di seluruh database.

## 5. Kondisi Aktual — Organization

### 5.1 Schema

- `organization_units`: `id`, `parent_id`, `unit_code`, `unit_name`, `description`, `unit_status`, timestamps, actor, `row_version`.
- Hanya constraint anti-self-parent dan FK `parent_id`; **tidak ada `unit_type`**, tidak ada aturan tipe induk-anak, tidak ada jaminan satu root KCIC.
- `user_organization_assignments`: `user_id` (bukan employee), `organization_unit_id`, `assignment_type` (`PRIMARY`/`SECONDARY`), validity, `is_active`, actor.
- Partial unique index hanya membatasi **satu PRIMARY aktif per user**. SECONDARY aktif tidak dibatasi jumlahnya. Tidak ada index/constraint satu placement aktif total.

### 5.2 Perilaku aplikasi

- `OrganizationService.create/update` memvalidasi parent aktif dan mencegah cycle, tetapi tidak memvalidasi tipe atau kedalaman.
- `assignUser` menutup PRIMARY lama saat PRIMARY baru dibuat; SECONDARY dapat ditambah berulang tanpa menutup yang lain.
- `members` dan `unitsOfUser` membaca assignment aktif per user.
- `OrganizationController` menyediakan `/api/v1/organization-units`, `/api/v1/admin/organization-units`, dan member list; tidak ada endpoint Employee.
- Tidak ada derivasi Directorate/Department dari Biro/Division karena tipe unit tidak ada.

### 5.3 Inventory data aktual

- 2 unit, keduanya root dan tidak bertipe: `KCIC-HO` (Head Office), `KCIC-OPS` (Operations).
- 0 unit bertipe Directorate/Department/Biro/Division.
- 2 placement aktif: `employee1` PRIMARY `KCIC-HO`; `employee3` SECONDARY `KCIC-HO`.
- 2 unit-scoped role assignment dan 2 global role assignment aktif.

## 6. Kondisi Aktual — Authorization dan ACL

### 6.1 RBAC

- 6 role sistem, 38 permission (V11).
- `user_role_assignments` mendukung scope `GLOBAL`, `ORGANIZATION_UNIT`, `WORKSPACE`, dengan validity dan status.
- `AuthorizationService.capabilities` menggabungkan seluruh role efektif tanpa memperluas unit scope menjadi descendant.
- `allowsResourceCapability`, `hasContentCapability`, dan `allowsUnitCapability` mencocokkan `organization_unit_id` secara **exact** terhadap unit owner/placement user.

### 6.2 Resource ACL

- `resource_permissions` mendukung principal `USER`, `ROLE`, `ORGANIZATION_UNIT`, effect `ALLOW`/`DENY`, source, revoke, expiry.
- `AuthorizationService.aclDecision` menerapkan DENY precedence dan exact unit matching: `unitIds.contains(grant.principalOrganizationUnitId)`. Tidak ada ekspansi descendant.
- `activeUnitIds` mengembalikan hanya unit placement aktif user; tidak ada ancestor/descendant resolution.
- Folder inheritance (`FolderGrantResolver`) sudah berjalan untuk ancestry folder, tetapi bukan organisasi.
- Data dev: 5 grant aktif, seluruhnya `USER / ALLOW`; tidak ada grant unit/role aktif. Risiko widening tetap relevan untuk environment lain dan untuk grant baru.

## 7. Dampak ke Domain Lain

| Area | Ketergantungan aktual | Dampak perubahan |
|---|---|---|
| Document ownership | `owner_user_id` XOR `owner_organization_unit_id`; history menyimpan keduanya | User id tetap stabil; employee link bersifat tambahan. Unit owner dapat berupa unit apa pun. |
| Catalog visibility | Filter memakai `activeUnitIds` exact + ACL exact | Perlu evaluator hierarchy opt-in agar tidak memperluas grant lama. |
| Workspace/folder access | Scope unit exact; ACL inheritance folder tetap | Sama seperti dokumen. |
| Request routing | Mencari UNIT_MANAGER pada unit target exact, lalu DOCUMENT_ADMIN/SYSTEM_ADMIN | Perlu keputusan apakah routing mewarisi manager dari ancestor; minimal tetap kompatibel. |
| Request requester/approver | FK ke `identity.users` | Tidak berubah; audit dan history tetap memakai user id. |
| Distribution | Recipient snapshot = `recipient_user_id`; grant USER per recipient | Aman jika User tidak diganti/dihapus. Employee merge tidak direncanakan. |
| Template | author/publisher FK user; usage user | Tidak berubah. |
| Audit | `actor_user_id`, `actor_username` snapshot, nama di-enrich dari users | Setelah Employee menjadi sumber nama, enrichment perlu join Employee; data audit lama tetap valid. |

## 8. Kondisi Aktual — Frontend Administration

| Surface | Kondisi | Gap |
|---|---|---|
| `/admin/users` | Membuat akun dari username/display name/email/password; belum ada Employee | Tidak ada NIP, employment status, Biro/Division, atau linkage |
| `/admin/users/:id` | Profil User, role assignment, keanggotaan unit PRIMARY/SECONDARY | Placement diperlakukan sebagai membership, bukan satu placement historis |
| `/admin/organization` | Tree unit bebas tanpa tipe; create/edit parentName; member list | Tidak ada tipe, validasi bentuk hierarchy, Directorate/Department derivation |
| `/admin/roles` | Role/permission mapping; scope unit/global | Tidak ada `include descendants` |
| Access grant dialog | Principal USER/ROLE/ORG_UNIT; unit list flat | Tidak ada opsi "termasuk sub-unit" |
| Dashboard | Menampilkan unit user dan admin links | Tidak ada employee/placement coverage atau administrasi Employee |
| Navigation | `user.manage`, `organization.manage` gates | Tidak ada Employee management route |

## 9. Kondisi Aktual — Test Coverage

- `AuthenticationIntegrationTest` (5): login lockout, forced password change, admin user lifecycle, filters, CSRF.
- `AuthorizationIntegrationTest` (4): admin API denial, unit-scoped role not global, DENY precedence/revoke, hierarchy cycle/duplicate code.
- `AclIntegrationTest` (10): grant/revoke/history, DENY, folder ancestry, workspace visibility, expiry, independent grants, grant validation, SYSTEM_ADMIN content denial.
- `RepositoryIntegrationTest`, `DocumentIntegrationTest`, `RequestWorkflowIntegrationTest`, `DistributionIntegrationTest`, `TemplateIntegrationTest`, `AuditIntegrationTest`.

Tidak ada test untuk: Employee/NIP, typed unit hierarchy, one-active placement, Employee–User 1:1, directorate/department derivation, descendant membership, legacy grant non-widening, employee search, atau migration backfill.

## 10. Matriks Kesesuaian dengan Keputusan Baru

| ID | Keputusan baru | Kondisi aktual | Status |
|---|---|---|---|
| D-01 | Unit bertipe KCIC/Directorate/Department/Biro/Division | Unit bebas tanpa tipe | **GAP** |
| D-02 | Hierarchy tervalidasi per tipe | Hanya anti-cycle | **GAP** |
| D-03 | Employee wajib NIP, full name, email, employment status | Tidak ada Employee | **GAP** |
| D-04 | Satu active placement pada Biro/Division | User boleh banyak SECONDARY; unit bukan Biro/Division | **GAP** |
| D-05 | Placement history | Ada `is_active`/`valid_until`, tetapi per user dan tanpa jaminan one-active | **PARTIAL** |
| D-06 | Directorate/Department derived dari Biro/Division | Tidak ada derivasi/tipe | **GAP** |
| D-07 | Employee tepat satu User Account | Tidak ada relasi; user punya identitas sendiri | **GAP** |
| D-08 | Employee data bukan sumber duplikat di User | `display_name`/`email` ada di User | **GAP** |
| D-09 | Unit access mendukung descendant | Exact match saja | **GAP** |
| D-10 | Grant lama tidak otomatis lebih luas | Belum ada mekanisme opt-in; jika descendant langsung diaktifkan akan widening | **RISK** |
| D-11 | Explicit DENY tetap menang | Sudah diterapkan | **OK** |
| D-12 | Backend satu-satunya otoritas | Sudah diterapkan | **OK** |
| D-13 | User Account mengelola username/password/status/role/session | Sudah ada | **OK** |
| D-14 | Distribution snapshot dipertahankan | Recipient user snapshot + provenance sudah ada | **OK (perlu regression test)** |
| D-15 | Frontend admin Employee dan typed org | Belum ada | **GAP** |
| D-16 | Audit tetap lengkap | Audit ada; enrichment nama perlu adaptasi | **PARTIAL** |

## 11. Dokumentasi vs Implementasi — Discrepancy

| Temuan | Bukti | Dampak |
|---|---|---|
| SCHEMA §5.2 hanya menjamin satu PRIMARY aktif, bukan satu placement aktif total | `V3__organization.sql` partial index `WHERE assignment_type='PRIMARY' AND is_active`; `OrganizationService.assignUser` | Keputusan baru menuntut invariant lebih kuat; perlu tabel/constraint baru, bukan mengandalkan perilaku lama |
| SCHEMA menyebut validasi overlapping assignment, implementasi tidak memvalidasi overlap tanggal | `OrganizationService.assignUser` hanya cek duplicate unit+tipe dan menutup PRIMARY lama | Data lama dapat memiliki overlap; backfill perlu rekonsiliasi |
| PRD USER-02 menyatakan create user mencakup primary unit; implementasi memisahkan create user dan assignment unit | `UserAdminService.create` vs `POST /admin/users/{id}/units` | Alur Employee-first akan mengubah kontrak dan UI |
| ARCHITECTURE §11 menyebut `/users/{id}/organization-assignments`; implementasi memakai `/admin/users/{id}/units` | `UserAdminController` dan `OrganizationController` | Perlu dokumentasi API aktual + kontrak baru |
| PRD §17 mencantumkan `positions` opsional; tidak ada tabel/endpoint | Schema aktual | Tidak menjadi blocker; jangan diimplementasikan tanpa kebutuhan |
| README menyebut Testcontainers PostgreSQL + MinIO; tidak ditemukan MinIO container pada test | `backend/src/test` hanya PostgreSQL Testcontainers | Residual gap MinIO live verification (sudah tercatat di PROGRESS/SECURITY) |

## 12. Data Readiness untuk Migrasi

| Data yang dibutuhkan | Kondisi | Konsekuensi jika tidak tersedia |
|---|---|---|
| NIP untuk setiap Employee | Tidak ada | Tidak boleh membuat NIP sintetis; E2/E3 cutover tertahan |
| Mapping User lama → Employee | Tidak ada | `users.employee_id` tidak dapat diisi; akun lama tidak memenuhi 1:1 |
| Full name/email Employee | Sebagian ada di User; 3 email null | Perlu sumber resmi; email wajib Employee |
| Employment status | Tidak ada | Perlu data HR |
| Struktur KCIC/Directorate/Department/Biro/Division | Hanya 2 root generik | Unit lama tidak dapat ditebak tipenya tanpa keputusan bisnis |
| Biro/Division untuk setiap Employee | Tidak ada | Satu active placement tidak dapat dibuat |

Kebijakan: **tidak mengarang NIP, unit, atau placement**. Data uji pada Testcontainers boleh sintetis dan tidak boleh dimigrasikan ke environment bersama.

## 13. E0 Exit Gate

| Gate | Hasil | Bukti |
|---|---|---|
| Repository, schema, dan migration diperiksa | PASS | V1–V13 dibaca; Flyway V13 aktif |
| Kode identity/organization/authorization diperiksa | PASS | Source dan pemanggil ditelusuri |
| Frontend administration diperiksa | PASS | Halaman users/organization/roles/access |
| Test coverage dipetakan | PASS | 40 backend + 12 frontend + 7 E2E |
| Gap register lengkap | PASS | `ARCHITECTURE_GAP_ANALYSIS.md` |
| Migration risk register lengkap | PASS | `MIGRATION_RISK_REGISTER.md` |
| Roadmap E1–E8 dan Story ID | PASS | `IMPLEMENTATION_PLAN.md` §21 |
| Tidak ada perubahan aplikasi/data | PASS | Hanya query baca dan dokumen |
| Data prerequisite teridentifikasi | PASS (blocker tercatat) | §12 |

**E0 verdict: PASS (audit selesai).** Implementasi E1 belum dimulai pada task ini.

## 14. Blocker dan Keputusan yang Masih Dibutuhkan

1. **Sumber data HR**: NIP, nama resmi, email, employment status, dan Biro/Division untuk 5 akun lama; tanpa itu `users.employee_id` tidak boleh diisi dan `NOT NULL` tidak boleh diaktifkan.
2. **Pemetaan tipe unit**: apakah `KCIC-HO`/`KCIC-OPS` menjadi Directorate/Department/Biro/Division, atau diganti hierarchy resmi. Jangan menebak.
3. **Kebijakan akun sistem/bootstrap**: apakah bootstrap admin wajib menjadi Employee sebelum go-live atau boleh menjadi system account sementara. Baseline yang diusulkan: link Employee wajib sebelum go-live, dengan gate query `count(users WHERE employee_id IS NULL)=0`; keputusan formal diperlukan untuk mengecualikan service account.
4. **Default `include_descendants` untuk grant baru**: baseline diusulkan `false` (explicit opt-in) agar tidak ada widening; UI dapat menyarankan `true` untuk Directorate/Department.
5. **Routing request ke ancestor manager**: baseline mempertahankan exact match agar kompatibel; perlu keputusan bisnis jika manager Directorate harus menangani request Department/Biro/Division.
