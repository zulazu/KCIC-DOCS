# KCIC Connect — Organization & Identity Architecture Gap Analysis

| Metadata | Nilai |
|---|---|
| Dokumen | Architecture Gap Analysis (E0) |
| Versi | 1.0 |
| Tanggal | 22 November 2026 |
| Status | Baseline target; implementasi belum dimulai |
| Sumber | `PHASE_E0_AUDIT.md`, kode dan schema aktual |

## 1. Ringkasan Eksekutif

Sistem saat ini memiliki organization unit generik dan assignment user, tetapi tidak memiliki Employee master, tipe unit, hierarchy terkendali, maupun relasi 1:1 Employee–User. ACL sudah mendukung principal unit, tetapi pencocokan unit bersifat **exact** sehingga belum mendukung descendant membership. Perubahan harus bersifat additive dan opt-in agar grant lama tidak otomatis melebar.

Target arsitektur:

```text
KCIC (COMPANY)
└── Directorate (DIRECTORATE)
    └── Department (DEPARTMENT)
        └── Biro/Division (DIVISION)
             ▲
             │ satu active placement (history disimpan)
          Employee 1 ──── 1 User Account
             │                 │
             │                 ├── role assignments (global/unit/workspace)
             │                 └── session/credentials
             └── resource ACL principal: USER (employee account) atau ORGANIZATION_UNIT
```

Directorate dan Department **tidak disimpan** pada Employee; keduanya diturunkan dari ancestry Biro/Division.

## 2. Target Domain Model

### 2.1 Organization

`organization.organization_units` ditambah `unit_type`:

| Tipe | Parent yang diizinkan | Aturan |
|---|---|---|
| `COMPANY` | null | Tepat satu root aktif; kode `KCIC` |
| `DIRECTORATE` | `COMPANY` | Parent wajib aktif dan bertipe COMPANY |
| `DEPARTMENT` | `DIRECTORATE` | Parent wajib aktif dan bertipe DIRECTORATE |
| `DIVISION` | `DEPARTMENT` | Parent wajib aktif dan bertipe DEPARTMENT; hanya Biro/Division dapat menerima Employee placement |

Aturan tambahan:

- Kode unit tetap unique case-insensitive.
- Unit bertipe `COMPANY` tidak boleh menjadi child.
- Perubahan parent tidak boleh menghasilkan cycle atau tipe yang tidak sah.
- Unit yang direferensikan Employee/workspace/ACL tidak dihapus; gunakan `INACTIVE`.
- Unit lama tanpa tipe dipertahankan sebagai `NULL` sampai dipetakan oleh business owner; tidak ada tebakan tipe.

### 2.2 Employee

Tabel baru `identity.employees`:

- `id UUID PK`
- `nip VARCHAR(30) NOT NULL UNIQUE` (case-sensitive; format final menunggu kebijakan HR)
- `full_name VARCHAR(160) NOT NULL`
- `email VARCHAR(254) NOT NULL` unique case-insensitive
- `employment_status VARCHAR(20) NOT NULL` (`ACTIVE`, `INACTIVE`, `TERMINATED`)
- `created_at`, `updated_at`, `row_version`
- Tidak menyimpan directorate/department/division (derived).
- Tidak menyimpan password, role, session, atau account status.

Tabel `organization.employee_placements`:

- `id UUID PK`
- `employee_id UUID NOT NULL FK employees`
- `division_unit_id UUID NOT NULL FK organization_units`
- `valid_from TIMESTAMPTZ NOT NULL`
- `valid_until TIMESTAMPTZ NULL`
- `is_active BOOLEAN NOT NULL DEFAULT TRUE`
- `assigned_by UUID NOT NULL FK users`
- `created_at TIMESTAMPTZ NOT NULL`
- CHECK `valid_until IS NULL OR valid_until > valid_from`
- Partial unique index `(employee_id) WHERE is_active` → satu active placement per Employee.
- Index `(division_unit_id, is_active)` untuk pencarian anggota.
- Biro/Division harus bertipe `DIVISION` dan aktif; divalidasi di application service, diperkuat dengan trigger/constraint jika memungkinkan tanpa menduplikasi hierarchy.

`user_organization_assignments` lama **tidak langsung dihapus**. Baseline transisi:

1. E2 menambah employee/placement baru.
2. E3/E6 memigrasikan placement lama yang telah diverifikasi ke `employee_placements`.
3. Setelah tidak ada pembaca, tabel lama diarsipkan/dihentikan penggunaannya. Drop tabel hanya dengan persetujuan eksplisit (bukan bagian E-series awal).

### 2.3 User Account

`identity.users` tetap menjadi identitas autentikasi:

- `username`, `password_hash`, account status, lockout, session.
- Tambah `employee_id UUID UNIQUE NULL` FK ke `identity.employees`.
- Unique constraint menjamin satu Employee maksimal satu User.
- `NOT NULL` diaktifkan setelah seluruh akun manusia memiliki Employee terverifikasi.
- `display_name` dan `email` pada User menjadi **deprecated** setelah E3: tidak ditulis lagi sebagai sumber data; nilai dibaca dari Employee. Kolom dipertahankan sementara untuk kompatibilitas dan baru boleh di-drop dengan persetujuan eksplisit.
- Akun bootstrap/service: baseline transisi mengizinkan `employee_id NULL` hanya selama migrasi; gate go-live `count(*) WHERE employee_id IS NULL = 0` kecuali pengecualian service account disetujui formal.

## 3. Target Authorization

### 3.1 Principal

| Principal | Representasi | Catatan |
|---|---|---|
| Individual Employee | `USER` principal pada `resource_permissions.principal_user_id` | Karena 1:1, user id merepresentasikan Employee. Tidak menambah principal type baru agar ACL, distribution, dan request grant tetap kompatibel. |
| Role | `ROLE` | Tidak berubah |
| Organization unit | `ORGANIZATION_UNIT` | Dapat menunjuk Directorate/Department/Biro/Division; apakah mencakup descendant ditentukan flag grant. |

### 3.2 Descendant membership (opt-in)

Tambah `include_descendants BOOLEAN NOT NULL DEFAULT FALSE` pada:

- `authorization.resource_permissions`
- `authorization.user_role_assignments` (khusus scope `ORGANIZATION_UNIT`)

Semantik:

1. `include_descendants = false` → perilaku lama, exact match. Semua row lama otomatis `false`.
2. `include_descendants = true` → grant pada unit U berlaku untuk user yang active placement-nya berada pada U atau descendant U.
3. Effective unit set user = Biro/Division placement + ancestor chain (Biro/Division → Department → Directorate → COMPANY). Pencocokan grant unit dilakukan terhadap rantai tersebut; flag hanya menentukan apakah ancestor grant menjangkau descendant.
4. `DENY` dengan flag true juga menjangkau descendant dan tetap mengalahkan `ALLOW`.
5. Revoke satu grant tidak mengubah grant lain; source/provenance tidak berubah.
6. Grant lama **tidak boleh** di-backfill menjadi `true`.
7. Grant baru default `false`; UI menawarkan opsi eksplisit "Berlaku untuk sub-unit" dan menampilkan ringkasan cakupan sebelum simpan.
8. Role scope unit dengan flag true mengikuti aturan yang sama; role assignment lama tetap `false`.
9. Routing request (UNIT_MANAGER) tetap exact pada baseline E-series; perubahan ke ancestor routing memerlukan keputusan bisnis terpisah.

### 3.3 Matriks keputusan

| Kondisi | Hasil |
|---|---|
| Tidak ada grant/role | DENY (default) |
| Grant USER ALLOW exact | ALLOW jika capability dimiliki |
| Grant unit ALLOW `false`, user placement descendant | Tidak berlaku |
| Grant unit ALLOW `true`, user placement descendant | ALLOW |
| Grant unit DENY `true` pada ancestor | DENY, mengalahkan allow mana pun |
| Grant expired/revoked | Tidak dihitung |
| Account INACTIVE/LOCKED | Ditolak sebelum evaluasi resource |
| SYSTEM_ADMIN tanpa ACL/ownership | Tidak otomatis membaca konten (DEC-025) |

### 3.4 Performance

Baseline pilot:

- Kedalaman hierarchy maksimum 4 (COMPANY → DIRECTORATE → DEPARTMENT → DIVISION).
- Resolusi ancestor dilakukan dengan recursive CTE atau pemuatan parent chain ≤4 level; hasil di-cache per request.
- Untuk listing yang memuat banyak dokumen, kumpulkan unit ancestor user sekali, lalu evaluasi grant dalam memori seperti pola ACL saat ini.
- Jika volume menuntut, tambahkan closure table pada fase optimasi terpisah; jangan menambah kompleksitas sebelum ada bukti query.

## 4. Gap Register

| ID | Gap | Severity | Bukti | Target phase | Acceptance |
|---|---|---|---|---|---|
| G-01 | Tidak ada `organization_units.unit_type` | High | `V3__organization.sql` | E1 | Tipe tersedia, tervalidasi, unit lama tetap NULL sampai dipetakan |
| G-02 | Hierarchy bebas; tidak ada aturan parent per tipe | High | `OrganizationService.validateParent` hanya anti-cycle | E1 | Create/update menolak parent tipe salah; test positif/negatif |
| G-03 | Tidak ada Employee/NIP/employment status | High | Tidak ada tabel/endpoint | E2 | Employee CRUD, NIP/email unique, status lifecycle |
| G-04 | Placement per User dan boleh banyak aktif | High | `V3` partial unique hanya PRIMARY; data employee3 SECONDARY | E2 | Satu active placement per Employee di DB; history utuh |
| G-05 | Placement tidak terbatas pada Biro/Division | High | Tidak ada tipe | E1/E2 | Placement hanya menerima unit DIVISION aktif |
| G-06 | Directorate/Department tidak diturunkan | Medium | Tidak ada derivasi | E2 | Response employee/me memuat chain; tidak ada kolom duplikat |
| G-07 | Tidak ada relasi Employee–User | High | `identity.users` tanpa `employee_id` | E3 | FK + UNIQUE; minimal satu arah 1:1; gate NOT NULL setelah backfill |
| G-08 | Employee data diduplikasi di User | High | `users.display_name`, `users.email` | E3 | User tidak lagi menjadi sumber; migrasi nilai; deprecation tercatat |
| G-09 | ACL unit exact, tanpa descendant | High | `AuthorizationService.aclDecision` `unitIds.contains` | E4 | Flag `include_descendants`; descendant ALLOW/DENY bekerja |
| G-10 | Role unit scope exact, tanpa descendant | High | `allowsResourceCapability` exact | E4 | Flag role assignment; test scope |
| G-11 | Risiko widening grant lama | Critical | Belum ada flag; default descendant jika diaktifkan akan melebar | E4/E6 | Backfill default false; regression test grant lama |
| G-12 | Tidak ada test Employee/typed hierarchy/descendant | High | Daftar test aktual | E1–E7 | Test baru per phase, termasuk negative |
| G-13 | Admin UI belum Employee-centric | High | `UsersPage`, `OrganizationPage` | E5 | Halaman Employee, typed tree, placement history, 1:1 indicator |
| G-14 | User admin API membuat User tanpa Employee | High | `UserAdminService.create` | E3 | Alur create employee+user transaksional; API kompatibel/deprecated jelas |
| G-15 | Tidak ada opsi descendant pada UI grant/role | High | `GrantDialog`, `UserDetailPage` | E5 | Checkbox + preview cakupan; default false |
| G-16 | Bootstrap admin tanpa Employee/placement | High | Data admin | E3/E6 | Gate go-live; keputusan service account |
| G-17 | Data lama tidak memiliki NIP/email/placement | Critical (blocker) | Inventory §4.3/§5.3 | E6 | Backfill dari sumber HR; tidak ada data sintetis di shared env |
| G-18 | Audit memakai nama User | Medium | `AuditDtos.actorDisplayName` | E3 | Enrichment via Employee; audit lama tetap terbaca |
| G-19 | Dashboard belum menampilkan coverage Employee/placement | Medium | `DashboardPage` | E5 | Ringkasan dan tautan administrasi |
| G-20 | Dokumentasi menyebut model lama | Medium | PRD/ARCHITECTURE/SCHEMA | E8 | Dokumen diperbarui, keputusan lama dipertahankan bila relevan |

## 5. Target API (kontrak yang direncanakan)

Semua endpoint baru memakai `/api/v1`, DTO eksplisit, dan backend capability checks.

| Method | Path | Capability | Catatan |
|---|---|---|---|
| GET | `/api/v1/admin/employees` | `employee.manage` | Search/pagination/filter status, NIP, unit ancestor |
| POST | `/api/v1/admin/employees` | `employee.manage` | Create Employee + placement + optional account dalam satu transaksi |
| GET | `/api/v1/admin/employees/{id}` | `employee.manage` | Detail + history placement + account link |
| PUT | `/api/v1/admin/employees/{id}` | `employee.manage` | NIP/full name/email/employment status |
| POST | `/api/v1/admin/employees/{id}/placements` | `employee.manage` | Tutup active placement lalu buat baru secara transaksional |
| GET | `/api/v1/admin/employees/{id}/placements` | `employee.manage` | History |
| POST | `/api/v1/admin/employees/{id}/account` | `user.manage` + `employee.manage` | Provision account 1:1 |
| POST | `/api/v1/admin/users/{id}/employee-link` | `user.manage` + `employee.manage` | Migrasi/link akun lama |
| GET | `/api/v1/auth/me` | authenticated | Tambah employee, employment status, division, department, directorate (derived) |
| POST | `/api/v1/admin/organization-units` | `organization.manage` | Tambah `unitType`; validasi parent |
| PUT | `/api/v1/admin/organization-units/{id}` | `organization.manage` | Perubahan tipe/parent tervalidasi; typology tidak boleh merusak data |
| POST | `/api/v1/.../access-grants` | existing manage capability | Tambah `includeDescendants` (default false) |
| POST | `/api/v1/admin/users/{id}/role-assignments` | `role.manage` | Tambah `includeDescendants` untuk scope unit |

Kompatibilitas:

- Endpoint `/admin/users` dan `/admin/users/{id}/units` tetap ada selama transisi; UI baru memakai Employee.
- Response lama menambah field opsional, tidak menghapus field.
- Permission baru minimal: `employee.manage` (atau gabungan `employee.view`/`employee.manage`). Baseline: satu `employee.manage` untuk administrasi; read detail melalui capability yang sama sampai ada kebutuhan pemisahan.
- `organization.manage` tetap mengatur unit; `role.manage` tetap mengatur role assignment.

## 6. Urutan Implementasi dan Dependensi

```text
E0 Audit (selesai)
  └─> E1 Organization Master (typed units; legacy untyped)
        └─> E2 Employee Master (employees + placements)
              └─> E3 User–Employee Integration (1:1, transactional provisioning)
                    ├─> E4 Authorization Enhancement (descendant opt-in)
                    └─> E5 Admin Dashboard/UI (employee-first admin)
                          └─> E6 Migration & Compatibility (verified backfill, cutover)
                                └─> E7 Security & UAT (cross-unit, non-widening, lifecycle)
                                      └─> E8 Documentation & Handover
```

E4 dapat berjalan paralel dengan E3 setelah schema E1/E2 stabil, tetapi tidak boleh mengaktifkan descendant secara default sebelum regression test non-widening lulus.

## 7. Test Matrix Target

| Area | Positive | Negative |
|---|---|---|
| Typed hierarchy | Company→Directorate→Department→Biro/Division valid | Directorate child of Directorate, Biro/Division child of Directorate, second COMPANY root |
| Employee | Create/link with unique NIP/email | Duplicate NIP/email, missing required fields, invalid employment status |
| Placement | One active; history preserved; move closes old | Second active placement, placement on non-DIVISION/inactive unit |
| 1:1 | One Employee one User | Second User for same Employee; account without Employee at go-live gate |
| Descendant ALLOW | Directorate grant true reaches Biro/Division employee | Same grant false does not reach descendant |
| Descendant DENY | Directorate DENY true blocks Biro/Division employee even with lower ALLOW | DENY false does not affect other branches |
| Legacy non-widening | Existing unit grant/role assignment still exact | No automatic descendant access after deploy |
| Cross-unit | Correct branch access | Sibling directorate/department denied |
| Lifecycle | Terminated employee and inactive account behavior explicit | Inactive employee/account cannot authenticate or access |
| Distribution | Snapshot recipient still works after employee link | Moving employee does not alter historical recipient set |
| Audit | Employee/placement actions audited | No password/hash/token in metadata |

## 8. Non-Goals E-Series

- HRIS/AD/SSO integration atau sinkronisasi otomatis.
- Multi-placement, acting position, atau matrix reporting.
- Menyimpan Directorate/Department sebagai kolom Employee.
- Mengubah MinIO/storage, distribution snapshot semantics, atau workflow state machine.
- Mengganti seluruh relasi domain dari `users.id` ke `employees.id`.
- Menghapus data/migration lama.

## 9. Ringkasan Perubahan Arsitektur

1. Organization menjadi **typed hierarchy**, bukan tree bebas.
2. Employee menjadi **single source of person/organization data**; User hanya account.
3. Placement dipindahkan ke Employee dengan invariant satu active dan history.
4. ACL unit mendapatkan **descendant membership opt-in** tanpa memperluas grant lama.
5. UI administrasi bergeser dari user-centric ke **employee-first**, dengan kompatibilitas transisi.
6. Semua perubahan schema additive (V14+), dengan cutover data yang bergantung pada sumber HR resmi.
