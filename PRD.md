# KCIC Docs — Product Requirements Document (PRD)

| Metadata | Value |
|---|---|
| Product | KCIC Docs |
| Document | Product Requirements Document |
| Version | 1.1 |
| Status | Strengthened Baseline — siap untuk technical breakdown dan implementation planning |
| Tanggal | 22 November 2026 |
| Bahasa | Indonesia |
| Referensi | Source input: `BRD.md` dan `SYSTEM_ANALYSIS.md` dari arsip awal (tidak disalin ke paket ini); `AGENTS.md`, `ARCHITECTURE.md`, `SCHEMA.md`, `DESIGN_SYSTEM.md`, `IMPLEMENTATION_PLAN.md` |

---

## 1. Ringkasan Produk

KCIC Docs adalah aplikasi web internal untuk mengelola dokumen, tautan Office/eksternal, permintaan dokumen, distribusi, dan template resmi perusahaan. Produk ditujukan untuk mengurangi ketergantungan pada WhatsApp sebagai sarana utama berbagi file dan meminta dokumen, sekaligus menyediakan kendali akses dan histori yang lebih terstruktur.

KCIC Docs merupakan **pilot project dengan standar implementasi produk yang serius**. Pilot membatasi integrasi dan fitur lanjutan, bukan kualitas. Aplikasi dibangun dengan modul yang jelas, keamanan dasar, data integrity, automated testing, dan clean code agar dapat diperluas tanpa perubahan besar pada fondasi.

### 1.1 Product vision

**Satu platform internal untuk menemukan, meminta, mengelola, dan membagikan dokumen perusahaan dengan akses yang jelas dan histori yang dapat ditelusuri.**

### 1.2 Product outcomes

- Pegawai menemukan dokumen dan template melalui satu katalog.
- Setiap dokumen mempunyai owner, metadata, status, dan versi yang jelas.
- Pengguna hanya dapat melihat atau mengubah resource sesuai kewenangannya.
- Permintaan akses dan permintaan dokumen memiliki status dan hasil pemenuhan.
- Distribusi dokumen memiliki daftar penerima, permission, dan histori.
- Administrator mengelola akun, role, permission, dan master data dasar.
- Aktivitas penting dapat ditelusuri melalui audit trail.

### 1.3 Indikator keberhasilan produk

KPI berikut menjadi kerangka evaluasi pilot. Target numerik ditetapkan setelah baseline proses saat ini dan skala pengguna diketahui; agent tidak boleh mengarang target.

| KPI | Definisi pengukuran | Tujuan |
|---|---|---|
| Active User Rate | Pengguna aktif pada periode dibanding akun aktif yang ditargetkan | Mengukur adopsi |
| Managed Document Count | Jumlah dokumen aktif terdaftar | Mengukur sentralisasi katalog |
| Metadata Completeness | Dokumen dengan metadata wajib lengkap dibanding dokumen aktif | Mengukur kualitas repository |
| Document Search Success | Pencarian yang menghasilkan dokumen relevan/akses ke detail | Mengukur kemampuan discovery |
| Request Fulfillment Time | Waktu dari SUBMITTED sampai FULFILLED | Mengukur kecepatan pemenuhan |
| Request Fulfillment Rate | Request fulfilled dibanding request yang diproses | Mengukur penyelesaian request |
| Distribution Read Rate | Recipient berstatus READ dibanding recipient terkirim | Mengukur keterjangkauan distribusi; READ hanya berarti detail distribution dibuka |
| Current Template Usage | Penggunaan template versi current dibanding versi lama | Mengukur konsistensi template |
| Access Review Findings | Temuan akses terlalu luas/tidak sesuai | Mengukur kualitas governance |
| WhatsApp Dependency Reduction | Perubahan proporsi permintaan/distribusi yang masih dilakukan di luar KCIC Docs | Mengukur dampak bisnis |

Jumlah dokumen yang tinggi tidak otomatis membuktikan kualitas repository; KPI adopsi harus dibaca bersama kelengkapan metadata dan penggunaan versi current.

## 2. Tujuan dan Non-Tujuan Produk

### 2.1 Tujuan

1. Menyediakan repository dokumen berbasis metadata.
2. Mendukung file upload dan pencatatan external link.
3. Menyediakan pengelolaan folder, workspace, kategori, klasifikasi, tag, owner, dan versi.
4. Menerapkan local authentication serta RBAC + scope + resource permission.
5. Mengelola request akses dan pemenuhan dokumen.
6. Mengelola distribusi dokumen internal.
7. Menyediakan katalog template resmi dan lifecycle dasar.
8. Menyediakan in-app notification dan audit trail.
9. Menerapkan modular codebase dan quality gates sejak awal.

### 2.2 Non-goals pilot

- Integrasi SSO/OIDC/LDAP/Active Directory/HRIS.
- Microsoft Graph, SharePoint API, OneDrive API, atau permission synchronization dengan Office provider.
- Akun eksternal/guest dan public sharing link.
- Antivirus scanning atau malware sandbox.
- Centralized logging, APM, monitoring platform, Redis, RabbitMQ, atau Kubernetes.
- Microservices dan distributed workflow engine.
- Email, WhatsApp, SMS, dan push notification.
- Real-time document editing/collaboration.
- OCR, full-text content indexing, dan advanced semantic search.
- Automatic retention/destruction dan workflow approval multi-level yang kompleks.

## 3. Target Pengguna dan Persona

### 3.1 Persona

| Persona | Tujuan utama | Kebutuhan utama |
|---|---|---|
| Employee | Menemukan dan menggunakan dokumen | Search, filter, preview/download, request, received documents |
| Contributor | Menyimpan dan membagikan dokumen | Upload, metadata, versioning, permission, distribution |
| Document Owner | Menjaga dokumen yang menjadi tanggung jawabnya | Kelola metadata/versi, review request, grant/revoke access |
| Unit Manager | Mengelola repository dan proses unit | Workspace, assigned requests, monitoring aktivitas unit |
| Document Administrator | Menjaga tata kelola dokumen lintas unit | Metadata master, repository governance, template, archive |
| System Administrator | Mengelola akun dan akses aplikasi | User, role, permission, organization master, account status |
| Auditor | Menelusuri aktivitas | Audit filters, detail event, export bila diizinkan |

### 3.2 Role baseline

| Role | Scope default | Capability utama |
|---|---|---|
| SYSTEM_ADMIN | Global | User, role, permission, organization/master configuration dasar |
| DOCUMENT_ADMIN | Global sesuai policy | Governance repository, kategori, klasifikasi, template dan pengelolaan dokumen |
| UNIT_MANAGER | Organization unit | Workspace unit, request unit, dokumen sesuai scope |
| CONTRIBUTOR | Unit/workspace yang ditetapkan | Upload, update versi, metadata, distribusi sesuai grant |
| EMPLOYEE | Resource yang diizinkan | Search, view/download, request, receive distribution |
| AUDITOR | Audit scope | View audit events/report; tidak otomatis dapat membaca isi dokumen |

Permission `request.approve` diberikan secara terpisah kepada pengguna yang bertugas sebagai approver. Satu pengguna dapat memiliki lebih dari satu role dan assignment scope.

## 4. Prinsip UX dan Navigasi

### 4.1 Prinsip UX

- Berorientasi pada tugas pegawai: menemukan dokumen, meminta dokumen, dan membagikan dokumen harus mudah ditemukan.
- Gunakan istilah bisnis yang konsisten: Document, Request, Distribution, Template, Workspace.
- Tampilkan owner, kategori, klasifikasi, versi, status, dan permission dengan jelas.
- Jangan menampilkan aksi yang tidak tersedia bagi pengguna; backend tetap menjadi otoritas izin.
- Setiap halaman mempunyai loading, empty, error, success, dan disabled state.
- Form metadata dibuat ringkas dengan field wajib yang jelas.
- Konfirmasi diperlukan untuk aksi berisiko seperti archive, revoke access, deactivate account, dan publish template.
- Status harus memakai label dan deskripsi yang mudah dipahami, bukan hanya kode teknis.
- Pola navigasi utama menggunakan **left sidebar** pada desktop, bukan top navigation sebagai navigasi modul utama.
- Pada tablet/mobile, left sidebar berubah menjadi drawer agar ruang konten tetap cukup.
- Sidebar menampilkan kelompok menu berdasarkan sitemap dan permission pengguna; backend tetap memvalidasi setiap akses.
- Area konten memakai breadcrumb, page title, deskripsi singkat, dan primary action yang konsisten.
- Design token, warna, ukuran layout, dan aturan komponen terperinci ditetapkan di `DESIGN_SYSTEM.md`.

### 4.2 Sitemap pengguna

```text
KCIC DOCS
├── Dashboard
├── Document Repository
│   ├── All Documents
│   ├── My Documents
│   ├── Department / Workspace
│   ├── Shared With Me
│   ├── Recent Documents
│   ├── Favorites
│   └── Document Detail
├── Requests
│   ├── Create Request
│   ├── My Requests
│   ├── Assigned Requests (sesuai permission)
│   ├── Approval Inbox (sesuai permission)
│   └── Request Detail
├── Distribution
│   ├── Send Document
│   ├── Sent
│   ├── Received
│   └── Distribution Detail
├── Templates
│   ├── Template Catalog
│   └── Template Detail / Version History
├── Notifications
└── Profile
    ├── My Profile
    └── Change Password
```

### 4.2.1 Left sidebar navigation behavior

| Area | Ketentuan |
|---|---|
| Desktop | Persistent left sidebar; default expanded; dapat di-collapse ke mode compact |
| Tablet/mobile | Off-canvas drawer dengan overlay; close setelah navigasi dan mendukung keyboard/focus |
| Active state | Active background tint, accent bar/border berwarna primary, icon dan label aktif, serta `aria-current` |
| Grouping | Menu utama: Dashboard, Repository, Requests, Distribution, Templates; area administrasi ditampilkan sesuai permission |
| Header | Breadcrumb/page context, notification entry, dan user menu; bukan pengganti sidebar |
| Authorization | Visibilitas menu mengikuti capability snapshot; backend tetap memeriksa seluruh API dan resource |
| Empty/denied | Deep link tanpa akses menghasilkan forbidden/not-found state yang aman |

### 4.3 Sitemap administrasi

```text
ADMINISTRATION
├── Overview
├── User Management
│   ├── Users
│   ├── Create/Edit User
│   └── Account Status
├── Authorization
│   ├── Roles
│   ├── Permissions
│   ├── Role-Permission Mapping
│   └── User Role Assignments
├── Organization
│   ├── Organization Units
│   └── User Assignment
├── Master Data
│   ├── Categories
│   ├── Document Types
│   ├── Classifications
│   ├── Tags
│   └── File Policy
├── Repository Governance
│   ├── Workspace Management
│   ├── Document Inventory
│   ├── Archived Documents
│   └── Access Review (dasar)
├── Template Administration
├── Request Administration (sesuai permission)
├── Audit Trail
└── System Configuration (terbatas)
```

## 5. Functional Requirements

Prioritas:
- **P0 / Must:** wajib untuk pilot dan release pertama.
- **P1 / Should:** nilai tinggi, dapat masuk release berikutnya dalam pilot jika kapasitas memungkinkan.
- **P2 / Could:** opsional setelah alur utama stabil.

Setiap requirement memiliki acceptance criteria yang dapat diuji. Detail visual, ukuran komponen, dan design token mengikuti `DESIGN_SYSTEM.md`; PRD ini menetapkan perilaku dan kebutuhan UX yang harus dipenuhi.

---

## 6. Epic AUTH — Local Authentication

### AUTH-01 Login

**Deskripsi:** pengguna internal login menggunakan akun lokal yang telah dibuat administrator.

**Input:** username/email dan password.

**Acceptance criteria:**
1. Pengguna dengan kredensial valid dan status ACTIVE dapat login.
2. Kredensial tidak valid menghasilkan pesan umum yang tidak mengungkap apakah username terdaftar.
3. Akun INACTIVE atau LOCKED tidak dapat login.
4. Percobaan login dibatasi oleh policy aplikasi untuk mengurangi brute force.
5. Session cookie menggunakan HttpOnly dan Secure pada HTTPS serta konfigurasi SameSite yang sesuai.
6. Operasi yang mengubah data dilindungi CSRF jika menggunakan session cookie.
7. Setelah login berhasil, pengguna diarahkan ke halaman yang sesuai permission.
8. Password tidak pernah dikirim kembali dalam response atau dicatat pada log/audit.

### AUTH-02 Logout dan session

**Acceptance criteria:**
1. Logout mengakhiri session aktif pada server.
2. Cookie session dihapus/invalid.
3. Session yang sudah berakhir tidak dapat digunakan untuk mengakses API.
4. Deaktivasi akun mencabut atau menginvalidasi session terkait.

### AUTH-03 Change password

**Acceptance criteria:**
1. Pengguna harus mengisi password saat ini dan password baru.
2. Password baru mengikuti password policy.
3. Password lama yang salah ditolak.
4. Password disimpan menggunakan adaptive password hashing, bukan plaintext atau reversible encryption.
5. Sistem memberikan konfirmasi berhasil tanpa menampilkan credential.

### AUTH-04 Administrator reset password

**Acceptance criteria:**
1. Admin berwenang dapat memicu reset password.
2. Reset tidak menampilkan password lama.
3. Mekanisme temporary password/forced password change digunakan sesuai keputusan implementasi.
4. Reset password dicatat dalam audit trail tanpa menyimpan password/token.

### AUTH-05 Account status

Status akun minimum: `ACTIVE`, `INACTIVE`, `LOCKED`.

**Acceptance criteria:**
1. Admin dapat mengaktifkan/menonaktifkan akun sesuai permission.
2. Akun nonaktif tidak dapat login dan tidak dapat menerima akses resource.
3. Perubahan status tercatat dengan actor dan timestamp.
4. Akun yang menjadi owner dokumen tidak boleh dihapus permanen tanpa proses transfer ownership.

---

## 7. Epic USER — User, Role, dan Organization Management

### USER-01 User directory

**Acceptance criteria:**
1. Administrator dapat melihat daftar akun sesuai scope.
2. Daftar mendukung pagination, search, dan filter status/unit/role.
3. Detail user menampilkan identitas dasar, unit, role, status, dan waktu perubahan.
4. Password, hash, session ID, dan credential tidak pernah ditampilkan.

### USER-02 Create/edit user

Field minimum: username, nama tampilan, email (bila diwajibkan), status, primary organization unit, dan role assignment.

**Acceptance criteria:**
1. Username unik.
2. Field wajib divalidasi pada frontend dan backend.
3. Admin dapat membuat akun tanpa public registration.
4. Admin dapat mengubah data yang diizinkan.
5. Perubahan unit/role tidak boleh menghapus histori assignment sebelumnya.

### USER-03 Role and permission management

**Acceptance criteria:**
1. Sistem menyediakan role baseline dan permission catalog.
2. Role mempunyai kode unik, nama, deskripsi, dan status.
3. Admin dapat mengatur mapping role-permission sesuai kewenangannya.
4. Role yang sedang digunakan tidak dapat dihapus sembarangan; nonaktifkan atau migrasikan assignment terlebih dahulu.
5. Backend mengevaluasi permission dan scope untuk setiap operasi terlindungi.

### USER-04 Organization master

**Acceptance criteria:**
1. Unit organisasi mendukung parent-child hierarchy.
2. Setiap unit mempunyai kode/nama unik sesuai aturan.
3. Unit dapat diaktifkan/nonaktifkan.
4. Unit yang masih menjadi referensi workspace/user tidak dapat hard-delete.
5. Pengguna dapat ditugaskan ke primary unit.

### USER-05 Role assignment scope

Scope minimum: `GLOBAL`, `ORGANIZATION_UNIT`, `WORKSPACE`.

**Acceptance criteria:**
1. Role assignment menyimpan user, role, scope type, scope ID, effective status, dan optional validity.
2. Backend tidak menganggap role tanpa scope sebagai akses global, kecuali role memang didefinisikan global.
3. Perubahan assignment memicu audit event.

### USER-06 Typed organization master (E-series)

Struktur organisasi KCIC adalah `KCIC → Directorate → Department → Biro/Division`. Unit memiliki `unit_type` dan hanya boleh memiliki parent sesuai tipe.

**Acceptance criteria:**
1. Tipe `COMPANY`, `DIRECTORATE`, `DEPARTMENT`, `DIVISION` tersedia dan tervalidasi backend.
2. `DIRECTORATE` hanya berada di bawah `COMPANY`; `DEPARTMENT` di bawah `DIRECTORATE`; `DIVISION` di bawah `DEPARTMENT`.
3. Tidak ada cycle, tidak ada tipe parent yang tidak sah, dan tidak ada unit kedua bertipe `COMPANY`.
4. Unit lama tanpa tipe dipertahankan sampai dipetakan oleh business owner; sistem tidak menebak tipe.
5. Unit yang masih direferensikan Employee/workspace/ACL tidak dihapus permanen.

### USER-07 Employee master (E-series)

Setiap Employee wajib memiliki NIP unik, full name, email, employment status, dan tepat satu active organizational placement.

**Acceptance criteria:**
1. NIP dan email unik; validasi dilakukan backend dan database.
2. Employee ditempatkan pada `DIVISION`; Directorate dan Department diturunkan dari ancestry Biro/Division, bukan disimpan sebagai kolom terpisah.
3. Satu active placement per Employee dijaga database; perpindahan menutup placement lama dan menyimpan history.
4. Employee dan User Account memiliki lifecycle terpisah; terminasi Employee tidak mengubah account secara diam-diam.
5. Setiap Employee tepat satu User Account; relasi 1:1 dijaga database.
6. Data Employee tidak diduplikasi sebagai sumber independen pada User.
7. Pembuatan Employee + initial placement berjalan dalam satu transaksi; pembuatan User Account dan assignment role berjalan dalam transaksi terpisah. Alur administrasi gabungan wajib mengompensasi kegagalan agar tidak meninggalkan Employee tanpa akun atau akun tanpa Employee; Employee baru tidak dianggap selesai onboarding sebelum link akun berhasil.
8. Tidak ada NIP, unit, atau placement sintetis pada environment bersama; data berasal dari sumber HR resmi.

### USER-08 Employee administration (E-series)

**Acceptance criteria:**
1. Administrator dengan `employee.manage` dapat mencari, membuat, dan memperbarui Employee.
2. Detail Employee menampilkan NIP, status, Biro/Division, Directorate/Department turunan, account link, dan history placement.
3. Perpindahan Biro/Division dilakukan melalui satu aksi yang menutup placement lama dan membuat placement baru.
4. Akun tanpa Employee link ditandai pada dashboard administrasi dan menjadi blocker go-live.
5. Aksi Employee dan placement dicatat pada audit trail dengan metadata aman.

---

## 8. Epic MASTER — Document Master Data

### MASTER-01 Category

**Acceptance criteria:**
1. Admin dapat membuat, mengubah, mengaktifkan, dan menonaktifkan kategori.
2. Kategori mempunyai kode unik, nama, deskripsi, dan optional parent category.
3. Kategori yang telah digunakan tidak dapat dihapus permanen.

### MASTER-02 Document type

Contoh nilai awal: SOP, Work Instruction, Report, Memo, Form, Contract/Agreement, Minutes of Meeting, Technical Document. Daftar final ditetapkan saat discovery.

**Acceptance criteria:**
1. Document type dapat dikelola administrator.
2. Type yang digunakan dokumen lama tetap dapat ditampilkan meskipun dinonaktifkan untuk penggunaan baru.

### MASTER-03 Classification

Nilai baseline: `INTERNAL`, `CONFIDENTIAL`, `RESTRICTED`. Nilai final harus disetujui pemilik kebijakan perusahaan.

**Acceptance criteria:**
1. Setiap dokumen aktif memiliki classification.
2. Classification ditampilkan pada document detail dan list yang relevan.
3. Policy permission dapat mempertimbangkan classification.
4. Perubahan classification dicatat pada audit trail.

### MASTER-04 Tags dan file policy

**Acceptance criteria:**
1. Admin dapat mengelola tag.
2. Dokumen dapat mempunyai beberapa tag.
3. File policy menyimpan allowlist format dan batas ukuran yang dapat dikonfigurasi.
4. Backend tetap memvalidasi format/ukuran; validasi frontend tidak dianggap sebagai kontrol keamanan.

---

## 9. Epic REPO — Document Repository

### REPO-01 Workspace

**Deskripsi:** area repository yang mempunyai owner dan scope organisasi.

**Field minimum:** workspace code, name, description, owner unit, owner user (bila relevan), status.

**Acceptance criteria:**
1. Pengguna hanya dapat melihat workspace yang public-to-internal sesuai policy atau yang diberikan akses.
2. Pembuatan dan perubahan workspace memerlukan permission.
3. Workspace dapat dinonaktifkan/diarsipkan tanpa menghilangkan histori dokumen.
4. Workspace menjadi salah satu batas scope authorization.

### REPO-02 Folder management

**Acceptance criteria:**
1. Pengguna berwenang dapat membuat, rename, move, dan archive folder.
2. Folder mendukung parent-child hierarchy.
3. Sistem mencegah folder menjadi parent dirinya sendiri atau membuat circular hierarchy.
4. Pemindahan folder/dokumen memeriksa izin pada sumber dan target.
5. Permission inheritance dijelaskan dan ditampilkan secara transparan kepada pengguna berwenang.

### REPO-03 Create document record

**Metadata minimum:**

| Field | Wajib | Keterangan |
|---|---|---|
| Document title | Ya | Nama yang mudah ditemukan |
| Document code | Conditional | Wajib jika kategori/kebijakan membutuhkan kode unik |
| Description | Disarankan | Ringkasan isi/kegunaan |
| Workspace | Ya | Area pemilik dokumen |
| Folder | Tidak | Lokasi pengorganisasian |
| Category | Ya | Kategori dokumen |
| Document type | Ya | Jenis dokumen |
| Classification | Ya | Tingkat sensitivitas |
| Owner | Ya | Pengguna/unit penanggung jawab |
| Tags | Tidak | Label tambahan |
| Effective date | Tidak | Tanggal berlaku jika relevan |
| Expiry/review date | Tidak | Pengingat review, bukan automatic deletion |

**Acceptance criteria:**
1. Backend memvalidasi seluruh field wajib.
2. Owner harus berupa user/unit yang valid dan aktif sesuai kebijakan.
3. Dokumen tidak otomatis dapat diakses semua pegawai hanya karena berhasil dibuat.
4. Sistem menghasilkan ID dokumen yang stabil.
5. Create event tercatat pada audit trail.
6. Perubahan document owner harus mencatat owner sebelumnya, owner baru, actor, waktu, dan alasan perubahan; histori owner tidak boleh dihapus.

### REPO-04 Upload file

**Acceptance criteria:**
1. Pengguna harus memiliki `document.upload` pada scope yang benar.
2. Format dan ukuran file diperiksa backend.
3. Nama object dibuat oleh aplikasi; original filename disimpan sebagai metadata.
4. File disimpan di private object storage.
5. Sistem menyimpan size, content type, checksum, storage key, uploader, dan upload time.
6. Kegagalan object upload atau database transaction tidak meninggalkan record dokumen yang tampak sukses tetapi file tidak tersedia.
7. File berformat tidak diizinkan ditolak dengan pesan yang jelas.
8. Progress upload dan hasil upload ditampilkan.
9. File tidak dianggap tervalidasi dari sisi malware karena antivirus berada di luar scope pilot; batasan ini didokumentasikan.

### REPO-05 Register external link

**Acceptance criteria:**
1. Pengguna berwenang dapat mendaftarkan URL Office atau external document URL.
2. Sistem menyimpan URL, provider/domain, title, description, owner, dan classification.
3. Sistem memvalidasi skema URL yang diperbolehkan (misalnya HTTPS) dan menolak skema berbahaya.
4. Sistem tidak mengeklaim bahwa registrasi link memberikan permission pada provider asal.
5. UI memberikan peringatan bahwa pengguna mungkin memerlukan akses terpisah di Office/provider.
6. Perubahan URL dapat disimpan sebagai versi/link revision baru sehingga histori lama tetap dapat ditelusuri.

### REPO-06 Document detail

**Acceptance criteria:**
1. Detail menampilkan metadata, owner, workspace/folder, classification, status, current version, dan permission sesuai hak pengguna.
2. Aksi yang tersedia menyesuaikan permission pengguna.
3. Riwayat versi dan audit/activity yang boleh dilihat tersedia.
4. Akses detail tetap diperiksa backend; mengetahui ID tidak cukup untuk mendapatkan akses.

### REPO-07 Version history

**Acceptance criteria:**
1. Revisi binary membuat document version baru.
2. Versi lama tidak ditimpa dan tetap tercatat.
3. Versi mempunyai version number/label, file/link reference, uploader, timestamp, change note, checksum bila file.
4. Hanya satu versi berstatus CURRENT.
5. Promote/revert ke versi sebelumnya harus berupa aksi eksplisit yang menghasilkan audit event dan aturan versioning yang konsisten; tidak menghapus versi yang lebih baru.
6. Download versi lama hanya diberikan kepada pengguna yang berwenang.

### REPO-08 Search dan filter

Filter minimum: title, document code, category, type, classification, owner, workspace, tag, created/updated date, status.

**Acceptance criteria:**
1. Hasil pencarian hanya menampilkan dokumen yang boleh diketahui/dilihat pengguna.
2. Search dan filter mendukung pagination dan sorting yang dibatasi.
3. Empty state memberi panduan untuk mengubah keyword/filter.
4. Pencarian awal menggunakan metadata, bukan isi file.
5. Query tidak boleh membocorkan keberadaan dokumen Restricted kepada pengguna yang tidak berwenang bila policy melarang discovery.

### REPO-09 Preview dan download

**Acceptance criteria:**
1. View/preview dan download merupakan permission terpisah bila kebijakan mengharuskannya.
2. Backend memeriksa permission sebelum streaming atau membuat short-lived signed URL.
3. Signed URL tidak permanen, tidak disimpan sebagai link dokumen, dan tidak dicatat ke audit/log.
4. Format yang tidak didukung preview-nya tetap dapat diunduh jika user mempunyai permission.
5. Download mencatat event, actor, document/version, dan waktu.
6. Browser tidak menerima storage credentials.

### REPO-10 Archive dan restore

**Acceptance criteria:**
1. Pengguna berwenang dapat mengarsipkan dokumen.
2. Dokumen archived tidak muncul pada hasil aktif secara default, tetapi dapat ditemukan melalui filter/archive view oleh pihak berwenang.
3. Archive tidak otomatis menghapus binary atau histori.
4. Restore memerlukan permission dan menghasilkan audit event.
5. Hard delete tidak menjadi aksi umum pada UI pilot.

### REPO-11 Favorite documents (P1)

**Deskripsi:** pengguna dapat menyimpan shortcut dokumen yang sering digunakan pada daftar Favorites miliknya.

**Acceptance criteria:**
1. Pengguna hanya dapat menambahkan favorite untuk dokumen yang boleh diketahui/dilihatnya.
2. Favorite bersifat personal dan tidak memberikan permission baru terhadap dokumen.
3. Pengguna dapat menambah dan menghapus favorite.
4. Daftar Favorites hanya menampilkan dokumen yang masih dapat diakses; akses yang dicabut tidak boleh dibypass oleh favorite record.
5. Relasi user-document bersifat unik sehingga satu pengguna tidak mempunyai favorite duplikat untuk dokumen yang sama.

---

## 10. Epic ACL — Access Control

### ACL-01 Permission model

Capability contoh:

```text
document.view
document.preview
document.download
document.create
document.upload
document.update_metadata
document.create_version
document.archive
document.share
document.manage_access
folder.view
folder.create
folder.update
folder.archive
workspace.manage
request.create
request.view_own
request.view_assigned
request.approve
request.reject
request.fulfill
distribution.create
distribution.view_own
distribution.view_received
distribution.revoke
template.view
template.create
template.publish
template.archive
user.manage
role.manage
master_data.manage
audit.view
```

**Acceptance criteria:**
1. Permission dievaluasi di backend untuk setiap resource action.
2. UI menggunakan permission response untuk mengatur visibilitas aksi, tetapi bukan sebagai security enforcement.
3. Default deny diterapkan.
4. Explicit deny mengalahkan inherited allow.
5. Role capability, scope, resource ACL, account status, document status, dan expiry dipertimbangkan.
6. Automated tests membuktikan akses allow dan deny, termasuk cross-unit access dan IDOR attempts.

### ACL-02 Grant/revoke access

**Acceptance criteria:**
1. Pengguna yang mempunyai `document.manage_access` dapat memberi akses kepada user internal sesuai policy.
2. Grant menyimpan principal, resource, permission, grant source, granted by, granted at, dan optional expiry.
3. Pengguna dapat melihat daftar grant yang relevan.
4. Revoke mencatat actor dan timestamp.
5. Revocation pada grant distribution tidak menghapus grant lain yang dibuat dari sumber berbeda.
6. Akses yang sudah expired ditolak walaupun record grant masih tersimpan untuk histori.

### ACL-03 Permission inheritance

**Acceptance criteria:**
1. Workspace/folder dapat menjadi sumber inherited permission.
2. UI menunjukkan sumber akses apabila pengguna berwenang melihatnya.
3. Explicit deny mengalahkan inherited allow.
4. Pemindahan dokumen/folder mengevaluasi ulang inherited access dan mencatat perubahan penting.
5. Aturan inheritance didokumentasikan dan memiliki test matrix.

### ACL-04 Organization descendant membership (E-series)

Grant dapat menunjuk individual Employee (melalui akun 1:1), Directorate, Department, atau Biro/Division.

**Acceptance criteria:**
1. Grant unit memiliki opsi eksplisit `include descendants`; default `false`.
2. Grant pada Directorate/Department dengan opsi aktif berlaku untuk anggota Biro/Division turunannya.
3. Grant lama tetap exact-match dan tidak otomatis memperoleh cakupan lebih luas.
4. Explicit DENY dengan cakupan descendant tetap mengalahkan ALLOW mana pun.
5. Revoke satu grant tidak menghapus grant independen dari sumber lain.
6. Expiry dan account status tetap diperiksa sebelum evaluasi resource.
7. Automated tests mencakup allow/deny lintas Directorate/Department/Biro/Division, sibling branch, legacy non-widening, dan inactive employee/account.

---

## 11. Epic REQ — Document Request dan Approval

### REQ-01 Create request

Jenis request:
- `ACCESS`: meminta akses pada dokumen existing.
- `DOCUMENT_DELIVERY`: meminta dokumen disiapkan/dikirim owner.
- `TEMPLATE`: meminta template atau bantuan menemukan template.

**Field minimum:** request type, title/description, target document (bila ada), target owner/unit, reason/business purpose, needed-by date (opsional), attachment pendukung (opsional jika diizinkan).

**Acceptance criteria:**
1. Employee dapat membuat request untuk dirinya sendiri.
2. Target document wajib untuk ACCESS; untuk DOCUMENT_DELIVERY dapat berupa dokumen yang sudah teridentifikasi atau deskripsi kebutuhan.
3. Reason wajib diisi.
4. Sistem menentukan owner/approver berdasarkan document owner atau unit tujuan; fallback ditetapkan kepada Document Administrator/administrator yang ditunjuk jika owner tidak tersedia.
5. Request mendapat ID dan status SUBMITTED setelah dikirim.
6. Pemohon menerima notifikasi bahwa request telah dikirim.

### REQ-02 Request list dan detail

**Acceptance criteria:**
1. Pemohon dapat melihat request miliknya.
2. Approver dapat melihat request yang ditugaskan kepadanya.
3. Unit Manager melihat request unit sesuai scope.
4. List mendukung filter status, type, tanggal, dan requester.
5. Detail menampilkan timeline, komentar, approval, dan fulfillment sesuai permission.

### REQ-03 Review, approve, reject

**Acceptance criteria:**
1. Approver hanya dapat memproses request yang ditugaskan/masuk scope-nya.
2. Approve/reject membutuhkan konfirmasi.
3. Reject wajib menyertakan alasan.
4. Approve untuk ACCESS tidak menganggap request selesai sebelum access grant berhasil dibuat.
5. Semua transisi memeriksa status sebelumnya untuk mencegah race condition/double approval.
6. Semua keputusan dicatat pada request history dan audit trail.

### REQ-04 Fulfillment

Cara pemenuhan:
- grant access ke dokumen existing;
- hubungkan satu atau lebih dokumen existing;
- upload dokumen baru;
- daftarkan external link.

**Acceptance criteria:**
1. Hanya owner/fulfiller berwenang yang dapat memenuhi request.
2. Fulfillment record menyimpan jenis pemenuhan, resource result, pelaksana, dan waktu.
3. Request hanya menjadi FULFILLED setelah hasil pemenuhan tersedia dan dapat ditelusuri.
4. Pemohon menerima notifikasi hasil pemenuhan.
5. Request yang sudah FULFILLED tidak dapat diubah kembali menjadi APPROVED/IN_PROGRESS.

### REQ-05 Cancel request

**Acceptance criteria:**
1. Pemohon dapat membatalkan request sebelum keputusan final sesuai policy.
2. Approver dapat menutup request yang duplikat/tidak relevan dengan alasan.
3. Cancel/close dicatat pada timeline.

### REQ-06 Request status model

Status yang digunakan:

| Status | Makna |
|---|---|
| DRAFT | Belum dikirim (opsional bila autosave/draft diimplementasikan) |
| SUBMITTED | Sudah dikirim dan menunggu review |
| UNDER_REVIEW | Sedang ditinjau approver |
| APPROVED | Disetujui, pemenuhan belum selesai |
| REJECTED | Ditolak dengan alasan |
| IN_PROGRESS | Owner sedang memenuhi request |
| FULFILLED | Hasil pemenuhan sudah tersedia |
| CLOSED | Ditutup setelah selesai |
| CANCELLED | Dibatalkan sesuai aturan |

Transisi utama:

```text
DRAFT -> SUBMITTED -> UNDER_REVIEW
UNDER_REVIEW -> REJECTED
UNDER_REVIEW -> APPROVED -> IN_PROGRESS -> FULFILLED -> CLOSED
APPROVED -> FULFILLED (khusus ACCESS jika grant langsung berhasil)
SUBMITTED/UNDER_REVIEW -> CANCELLED (sesuai policy)
FULFILLED -> CLOSED
```

Backend harus menolak transisi yang tidak tercantum. `CLOSED` adalah penutupan administratif setelah fulfillment; untuk pilot dapat dilakukan otomatis setelah pemohon/owner mengonfirmasi atau melalui aturan sederhana yang ditetapkan pada implementasi.

---

## 12. Epic DIST — Document Distribution

### DIST-01 Create distribution

**Input:** satu atau lebih dokumen, daftar penerima individu/unit, pesan, permission yang diberikan, dan optional expiry.

**Acceptance criteria:**
1. Pengirim mempunyai `distribution.create` dan `document.share` pada seluruh dokumen yang dipilih.
2. Penerima harus akun internal aktif.
3. Distribution ke unit menghasilkan snapshot pengguna aktif pada waktu pengiriman.
4. Sistem tidak mengirim ke akun eksternal atau public URL pada pilot.
5. Permission distribution tidak boleh lebih luas daripada permission yang boleh didelegasikan pengirim.
6. Pengiriman membuat distribution record, recipient records, permission grants, dan notification records secara konsisten.
7. Bila proses gagal sebagian, sistem tidak boleh melaporkan distribution sukses dengan recipient/access yang tidak lengkap; gunakan transaksi/kompensasi yang jelas.

### DIST-02 Sent dan received distribution

**Acceptance criteria:**
1. Pengirim dapat melihat distribution yang dibuatnya sesuai scope.
2. Penerima dapat melihat distribution yang ditujukan kepadanya.
3. Detail menampilkan dokumen, pengirim, waktu, pesan, jenis akses, dan expiry.
4. Penerima hanya dapat membuka file jika grant masih valid dan permission lain mengizinkan.
5. Status READ tercatat ketika penerima membuka detail distribution.

### DIST-03 Revoke distribution

**Acceptance criteria:**
1. Pengirim atau pihak berwenang dapat mencabut distribution.
2. Revoke mengakhiri grant yang bersumber dari distribution tersebut.
3. Grant lain yang independen tetap berlaku.
4. Perubahan status recipient dan distribution dicatat.
5. Setelah revoke, akses berikutnya ditolak oleh backend.

### DIST-04 Expiry

**Acceptance criteria:**
1. Expiry bersifat optional sesuai policy.
2. Permission yang melewati expiry otomatis tidak valid saat dievaluasi oleh backend, walaupun proses cleanup belum dijalankan.
3. UI menampilkan waktu expiry.
4. Expired event dapat tercermin pada status dan activity history.

### DIST-05 Distribution status

Status distribution: `DRAFT`, `SENT`, `PARTIALLY_REVOKED`, `REVOKED`, `EXPIRED`.

Status recipient: `SENT`, `READ`, `REVOKED`, `EXPIRED`.

`READ` hanya menunjukkan halaman distribution dibuka. Status tidak boleh diberi label "file dibaca" kecuali sistem benar-benar dapat membuktikan file content dibuka.

---

## 13. Epic TEMPLATE — Template Management

### TEMPLATE-01 Template catalog

**Acceptance criteria:**
1. Employee dapat menemukan template PUBLISHED yang boleh diakses.
2. Katalog dapat difilter berdasarkan nama, kode, kategori, owner, dan unit.
3. Detail menampilkan deskripsi, owner, versi current, effective date (jika ada), dan status.
4. Draft dan archived tidak tampil sebagai template resmi pada katalog umum.

### TEMPLATE-02 Create/manage template

**Acceptance criteria:**
1. Contributor/Document Admin berwenang dapat menetapkan dokumen menjadi template.
2. Template memiliki code, name, description, owner, category, status, dan document reference.
3. Template menggunakan document versioning yang sama dengan repository; binary tidak diduplikasi ke tabel template.
4. Kode template unik dalam aturan yang ditetapkan.

### TEMPLATE-03 Publish/archive

**Acceptance criteria:**
1. Hanya pengguna dengan `template.publish` dapat menerbitkan template.
2. Publish mencatat publisher, timestamp, dan versi yang diterbitkan.
3. Template yang tidak lagi berlaku dapat diarsipkan.
4. Template archived tidak hilang dari histori.
5. Publikasi versi baru menandai versi sebelumnya sebagai superseded bila sesuai aturan; versi lama tetap dapat ditelusuri oleh pengguna yang berwenang.

### TEMPLATE-04 Template usage

**Acceptance criteria:**
1. Pengguna dapat membuka atau mengunduh versi current sesuai permission.
2. Sistem dapat mencatat event penggunaan/download untuk analisis adopsi.
3. Sistem menampilkan peringatan bila pengguna membuka template yang bukan current dari histori.

---

## 14. Epic NOTIF — In-App Notification

### NOTIF-01 Notification center

**Acceptance criteria:**
1. Pengguna melihat notifikasi yang ditujukan kepadanya.
2. Notifikasi mempunyai type, title, message, target resource, created time, dan read status.
3. Pengguna dapat menandai satu atau beberapa notifikasi sebagai read.
4. Membuka notification dapat mengarahkan pengguna ke resource jika masih berhak mengaksesnya.
5. Notifikasi tidak boleh menjadi jalur bypass authorization.
6. Notification list mendukung pagination.

### NOTIF-02 Notification triggers

Event minimum: request submitted/assigned/approved/rejected/fulfilled, document distributed, access granted/revoked/expired, template published/updated, dan assignment penting.

Pengiriman email/WhatsApp/SMS/push berada di luar pilot.

---

## 15. Epic AUDIT — Audit Trail

### AUDIT-01 Audit event capture

Event minimum:

- login success/failure (tanpa credential);
- account create/update/deactivate/reactivate/password reset;
- role/permission assignment changes;
- workspace/folder create/update/archive;
- document create/upload/metadata update/version create/archive/restore;
- preview/download;
- access grant/revoke;
- request submit/approve/reject/fulfill/cancel/close;
- distribution send/read/revoke/expire;
- template create/publish/archive.

**Acceptance criteria:**
1. Audit event menyimpan actor, action, resource type/ID, timestamp, outcome, dan safe metadata.
2. Event tidak menyimpan password, hash, session cookie, signed URL, storage credential, atau isi binary.
3. Pengguna umum tidak dapat mengubah/menghapus event.
4. Hanya Auditor/Admin yang mempunyai `audit.view` dapat mencari event dalam scope.
5. Audit list mendukung filter actor, action, resource, outcome, dan date range.
6. Perubahan penting dapat ditelusuri dari detail resource.

### AUDIT-02 Audit reporting

Prioritas P1. Laporan dasar dapat menampilkan jumlah dokumen, request berdasarkan status, distribution, dan template aktif. Export audit perlu pembatasan permission dan pencatatan aktivitas export.

**Catatan:** Audit trail adalah rekaman bisnis yang diperlukan produk. Ini tetap berjalan pada pilot meskipun centralized technical logging dan monitoring platform ditunda.

---

## 16. Epic DASH — Dashboard dan Reporting

### DASH-01 Employee dashboard

Menampilkan:
- search utama;
- pintasan Repository, My Requests, Distribution, Templates;
- recent documents berdasarkan event akses yang boleh dilihat pengguna;
- request yang menunggu update;
- distribution diterima;
- notifikasi terbaru.

**Acceptance criteria:**
1. Widget hanya menampilkan resource yang boleh dilihat pengguna.
2. Empty state ditampilkan jika belum ada aktivitas.
3. Dashboard tidak melakukan query tak terbatas; gunakan batas jumlah item dan pagination pada halaman detail.

### DASH-02 Admin dashboard (P1)

Menampilkan ringkasan akun aktif, dokumen aktif, request pending, distribution, template published, dan kapasitas storage bila metrik tersedia. Tidak perlu membangun analytics platform pada pilot.

---

## 17. Data Requirements

### 17.1 Entitas inti

| Domain | Entitas |
|---|---|
| Identity | users, employees |
| Organization | organization_units (typed), employee_placements |
| Authorization | roles, permissions, role_permissions, user_role_assignments, resource_permissions |
| Master | document_categories, document_types, document_classifications, tags, file_policies |
| Repository | workspaces, folders, documents, document_owner_history, document_versions, storage_objects, external_resources, document_tag_assignments, document_favorites |
| Request | document_requests, request_attachments, request_approvals, request_fulfillments, request_fulfillment_items, request_histories |
| Distribution | distributions, distribution_items, distribution_recipients, distribution_access_grants, distribution_events |
| Template | document_templates, template_usage_events |
| Notification | notifications (satu row per penerima; `read_at` disimpan pada row tersebut) |
| Audit | audit_events |

### 17.2 Data integrity rules

1. Primary key menggunakan UUID atau identifier stabil.
2. Foreign key digunakan untuk relasi internal yang relevan.
3. Code/username yang ditentukan unik dijaga dengan unique constraint.
4. Timestamp disimpan secara konsisten dalam UTC.
5. Document version tidak mengubah binary versi sebelumnya.
6. Checksum file disimpan untuk verifikasi integritas.
7. Favorite bersifat personal dan tidak memberikan permission; daftar recent dihitung dari event akses yang sesuai.
8. Satu current version per document dijaga melalui transaction/constraint yang sesuai.
9. Status workflow dan distribution mempunyai transition validation.
10. Record yang memiliki histori tidak dihapus permanen melalui operasi UI normal.
11. Semua perubahan schema menggunakan database migration yang versioned.

### 17.3 Required metadata minimum dokumen

`title`, `workspace_id`, `category_id`, `document_type_id`, `classification_id`, `owner`, `status`, `created_by`, `created_at`.

`document_code`, `description`, `folder_id`, `tags`, `effective_date`, `review_date`, dan `expiry_date` bergantung pada kebijakan dan jenis dokumen.

---

## 18. API dan Integrasi — Batas Domain

API menggunakan REST dengan prefix versi, misalnya `/api/v1`. Kontrak final, pagination convention, error schema, dan naming disepakati pada technical design.

Domain endpoint yang diperkirakan:

| Domain | Contoh resource |
|---|---|
| Auth | `/auth/login`, `/auth/logout`, `/auth/me`, `/auth/change-password` |
| Users | `/users`, `/users/{id}`, `/users/{id}/roles`, `/users/{id}/status` |
| Roles | `/roles`, `/permissions`, `/roles/{id}/permissions` |
| Organization | `/organization-units`, `/users/{id}/organization` |
| Master | `/document-categories`, `/document-types`, `/classifications`, `/tags` |
| Workspaces | `/workspaces`, `/workspaces/{id}/folders` |
| Documents | `/documents`, `/documents/{id}`, `/documents/{id}/versions` |
| Files | `/documents/{id}/content`, `/documents/{id}/versions/{versionId}/content` |
| Permissions | `/documents/{id}/permissions`, `/documents/{id}/permissions/{permissionId}` |
| Requests | `/requests`, `/requests/{id}`, `/requests/{id}/approve`, `/requests/{id}/fulfill` |
| Distribution | `/distributions`, `/distributions/{id}`, `/distributions/{id}/revoke` |
| Templates | `/templates`, `/templates/{id}/publish`, `/templates/{id}/archive` |
| Notifications | `/notifications`, `/notifications/{id}/read` |
| Audit | `/audit-events` |

Daftar di atas merupakan domain outline, bukan final API contract. Endpoint final harus konsisten dengan use case, authorization policy, dan DTO tiap modul.

### 18.1 External Office URL

Pilot hanya mencatat URL dan metadata. Backend tidak melakukan Microsoft Graph/OneDrive/SharePoint permission sync. UI harus membedakan aksi `Open external link` dari `Download from KCIC Docs`.

---

## 19. Security and Privacy Requirements

1. Local password disimpan menggunakan adaptive hashing yang dikelola framework keamanan.
2. Tidak ada public self-registration.
3. Session cookie aman dan CSRF dilindungi.
4. Backend melakukan authorization pada setiap endpoint/resource.
5. Object storage private dan credential tidak pernah dikirim ke frontend.
6. Upload dibatasi format/ukuran dan divalidasi pada backend.
7. File tidak dieksekusi dari web application server.
8. Download melalui backend authorization dan signed URL singkat bila digunakan.
9. External link harus dibatasi ke skema URL yang aman, umumnya HTTPS.
10. Error response tidak membocorkan stack trace, path, SQL, bucket name sensitif, atau credential.
11. Audit metadata tidak memuat secret atau konten dokumen.
12. Akun nonaktif kehilangan kemampuan login dan akses.
13. Pengujian IDOR, privilege escalation, cross-unit access, dan permission expiry wajib dilakukan.
14. Antivirus belum tersedia pada pilot; pembatasan file dan residual risk wajib terdokumentasi.

## 20. Non-Functional Requirements

| ID | Kategori | Requirement |
|---|---|---|
| NFR-01 | Performance | List dan search menggunakan pagination serta batas hasil |
| NFR-02 | Performance | Upload menampilkan progress dan memiliki batas ukuran yang dapat diatur |
| NFR-03 | Security | Authorization backend untuk semua resource action |
| NFR-04 | Security | Password hashing, secure session, CSRF, private storage |
| NFR-05 | Reliability | Kegagalan upload/metadata tidak menciptakan state sukses parsial |
| NFR-06 | Integrity | Checksum dan immutable version untuk binary |
| NFR-07 | Maintainability | Modul terpisah berdasarkan domain dan tanggung jawab |
| NFR-08 | Testability | Unit/integration/E2E test untuk alur inti dan authorization |
| NFR-09 | Usability | Loading, error, empty, success, disabled, dan confirmation state tersedia |
| NFR-10 | Compatibility | Mendukung browser modern dan tampilan responsif |
| NFR-11 | Recoverability | Database dan object storage memiliki backup/restore yang diuji |
| NFR-12 | Auditability | Perubahan akses dan lifecycle tercatat |

Target angka p95, availability, pengguna konkuren, ukuran maksimum upload, RPO, dan RTO ditetapkan setelah mengetahui kapasitas infrastruktur serta skenario penggunaan.

## 21. Clean Code dan Modular Architecture — Product Engineering Requirements

Bagian ini bersifat normatif dan wajib dipatuhi dalam setiap fase implementasi.

### 21.1 Backend modularity

Gunakan modular monolith dengan pembagian berdasarkan domain/feature. Setiap modul memiliki batas yang jelas dan dapat dipahami secara mandiri.

Contoh:

```text
backend/src/main/java/com/kcic/connect/
├── identity/
├── authorization/
├── organization/
├── masterdata/
├── repository/
│   ├── document/
│   ├── folder/
│   ├── version/
│   └── storage/
├── workflow/
│   ├── request/
│   └── approval/
├── distribution/
├── template/
├── notification/
├── audit/
└── shared/
```

Setiap modul mengikuti pemisahan tanggung jawab yang sesuai, misalnya `api`, `application`, `domain`, dan `infrastructure`.

**Aturan wajib:**
- Controller tidak menampung business logic utama.
- Use case/application service menjadi entry point business operation.
- Domain tidak bergantung pada Spring MVC, HTTP, atau vendor storage.
- Repository module tidak diakses langsung dari modul lain.
- Antar-modul berinteraksi melalui public service/interface atau internal event yang eksplisit.
- Hindari circular dependency dan shared mutable state.
- Entity persistence tidak diekspos langsung sebagai API response.
- Transaksi memiliki batas use case yang jelas.
- Authorization policy reusable dan tidak di-hardcode berulang kali.
- Error mapping dan response API konsisten.

### 21.2 Frontend modularity

Contoh:

```text
frontend/src/
├── app/
│   ├── routes/
│   ├── providers/
│   └── layouts/
├── features/
│   ├── auth/
│   ├── dashboard/
│   ├── documents/
│   ├── requests/
│   ├── distributions/
│   ├── templates/
│   ├── notifications/
│   └── administration/
├── shared/
│   ├── components/
│   ├── hooks/
│   ├── api/
│   ├── types/
│   ├── utils/
│   └── styles/
└── assets/
```

**Aturan wajib:**
- Komponen halaman tidak boleh menjadi tempat seluruh data fetching, validasi bisnis, state, dan layout sekaligus.
- API client dan tipe response dikelola secara konsisten.
- Komponen reusable ditempatkan di shared hanya jika benar-benar generik.
- Business component tetap berada di feature pemiliknya.
- Hindari `any`; gunakan type/interface eksplisit.
- Tidak ada authorization enforcement hanya di frontend.
- Setiap form dan aksi memiliki state loading/error/success.
- Hindari duplikasi logic dan efek samping yang tersebar.

### 21.3 Cross-cutting clean-code rules

1. Nama file, class, function, variable, endpoint, dan database field harus konsisten dan menjelaskan maksudnya.
2. Satu unit kode memiliki satu tanggung jawab utama.
3. Hindari hardcoded permission, status, URL, dan business threshold; simpan pada enum/config/master sesuai sifat datanya.
4. Jangan membuat abstraction generik yang belum dibutuhkan; abstraksi harus menyelesaikan coupling atau variasi nyata.
5. Jangan membuat satu file `utils`/`helpers` sebagai tempat seluruh fungsi.
6. Hindari silent exception, empty catch, dan error yang ditelan.
7. Jangan commit credential, token, URL signed, atau file data sensitif.
8. Komentar menjelaskan alasan/constraint, bukan mengulang kode.
9. Perubahan database selalu melalui migration.
10. Perubahan modul harus menyertakan automated test yang relevan.

### 21.4 Quality gates

Sebelum merge:

- frontend build berhasil;
- backend build berhasil;
- lint dan TypeScript type-check berhasil;
- unit test lulus;
- integration test database dan storage lulus untuk alur kritis;
- authorization test lulus untuk allow/deny, cross-unit, expiry, dan revoke;
- E2E test alur login, repository, request, distribution, dan template lulus;
- migration dapat dijalankan dari database kosong dan upgrade database sebelumnya;
- code review memeriksa modularity, security, error handling, dan maintainability.

Jangan menetapkan persentase coverage sebagai satu-satunya ukuran kualitas. Coverage membantu, tetapi bukti utama adalah pengujian business rules dan skenario negatif.

---

## 22. Acceptance Criteria End-to-End

### E2E-01 Pengguna mengakses repository

**Given** pengguna aktif telah login dan memiliki permission pada workspace, **when** pengguna membuka repository dan mencari dokumen, **then** sistem hanya menampilkan resource yang boleh diketahui/diaksesnya dan menampilkan metadata yang relevan.

### E2E-02 Upload dan versi dokumen

**Given** Contributor memiliki permission upload, **when** ia membuat dokumen lalu mengunggah file, **then** record dokumen dan storage object tercatat konsisten. Ketika ia mengunggah revisi, sistem membuat versi baru dan mempertahankan versi sebelumnya.

### E2E-03 Penolakan akses

**Given** Employee tidak memiliki permission terhadap dokumen Restricted, **when** ia mencoba membuka detail, preview, download, atau memanipulasi ID dokumen, **then** backend menolak akses dan tidak membocorkan konten maupun metadata yang dilindungi.

### E2E-04 Request access

**Given** Employee menemukan dokumen tetapi belum memiliki akses, **when** ia mengajukan ACCESS request, **then** request diteruskan ke owner/approver. Setelah disetujui, permission grant dibuat dan pemohon mendapat notifikasi. Jika grant gagal, request tidak boleh dinyatakan fulfilled.

### E2E-05 Request delivery

**Given** Employee membutuhkan dokumen yang belum tersedia, **when** ia mengajukan DOCUMENT_DELIVERY request, **then** owner dapat memenuhi melalui dokumen existing, upload, atau external link dan hasilnya terhubung pada request.

### E2E-06 Distribution internal

**Given** Contributor berhak membagikan dokumen kepada sejumlah pegawai, **when** distribution dikirim, **then** sistem menyimpan snapshot penerima, grant, dan notifikasi. Penerima dapat membuka dokumen sesuai permission. Revoke distribution mencabut grant yang berasal dari distribution tersebut.

### E2E-07 Template lifecycle

**Given** Contributor menyiapkan dokumen template, **when** pengguna berwenang memublikasikannya, **then** template muncul di katalog sebagai PUBLISHED dengan versi current. Revisi berikutnya menghasilkan versi baru dan histori lama tetap dapat ditelusuri.

### E2E-08 Auditability

**Given** pengguna melakukan aksi penting, **when** aksi berhasil atau ditolak pada kategori yang perlu dicatat, **then** audit event tersedia dengan actor, resource, action, timestamp, dan outcome tanpa menyimpan credential atau konten file.

---

## 23. Release Plan dan Prioritas

| Release | Scope | Exit criteria |
|---|---|---|
| R0 — Foundation | Project setup, modular structure, migrations, local auth, users, organization, RBAC baseline | User dapat login; authorization test lulus; struktur modul dan CI quality gates aktif |
| R1 — Repository | Workspace, folder, document metadata, upload/link, versioning, search, preview/download, ACL, audit | Dokumen dapat dikelola dan aksesnya teruji; backup storage/database disiapkan |
| R2 — Request & Distribution | Request types, approval, fulfillment, distribution, recipients, revoke, in-app notifications | Request dan distribution berjalan end-to-end dan memiliki audit history |
| R3 — Template & Governance | Template catalog, publish/archive, version history, reporting dasar | Template official dapat ditemukan dan versi current teridentifikasi |
| R4 — Pilot hardening | Security review, regression, restore test, user acceptance, operational handover | UAT disetujui, residual risks terdokumentasi, runbook dasar tersedia |

Katalog template dasar dapat dimasukkan pada R1/R2 bila diperlukan untuk uji proses, sementara lifecycle governance lengkap menjadi R3.

## 24. Testing Strategy

### 24.1 Unit test

- request status transition;
- document version current/superseded;
- permission evaluation;
- inheritance dan explicit deny;
- distribution recipient snapshot;
- expiry/revoke behavior;
- template publication rules;
- file metadata validation.

### 24.2 Integration test

- repository PostgreSQL;
- transaction behavior;
- object storage adapter;
- upload failure compensation;
- signed URL/download authorization;
- session authentication;
- notification persistence;
- audit event creation.

### 24.3 Security test

- IDOR dan resource enumeration;
- privilege escalation;
- cross-unit access;
- access setelah revoke/expiry;
- inactive user access;
- file extension/MIME mismatch;
- path traversal dan unsafe filename;
- CSRF/session handling;
- external URL scheme validation.

### 24.4 E2E test

- login/logout;
- create user dan role assignment;
- upload document dan version creation;
- register external link;
- search dan access control;
- request access dan request delivery;
- approval/rejection/fulfillment;
- distribution, read state, revoke;
- template publish/archive;
- audit trail search.

## 25. UX State dan Error Handling

Setiap halaman/list/form setidaknya menyediakan:

- **Loading:** indikator proses dan pencegahan double submit.
- **Empty:** penjelasan mengapa belum ada data serta tindakan berikutnya jika tersedia.
- **Error:** pesan yang dapat dipahami, correlation/reference ID bila relevan, dan opsi retry yang aman.
- **Success:** hasil tindakan dan navigasi berikutnya.
- **Forbidden:** akses tidak diizinkan tanpa membocorkan resource yang dilindungi.
- **Not found:** resource tidak tersedia atau tidak dapat diungkapkan keberadaannya sesuai policy.
- **Conflict:** perubahan bersamaan/status tidak lagi valid; tampilkan cara memuat ulang.

Backend menggunakan response error konsisten dengan code, message aman, optional field errors, dan request/correlation ID. Stack trace hanya untuk developer-side diagnostics dan tidak dikirim ke browser.

## 26. Deployment dan Operasional Pilot

Detail infrastruktur final ditentukan bersama tim IT. Persyaratan minimum:

1. Environment configuration terpisah untuk development, test, staging, dan production/pilot.
2. PostgreSQL dan object storage tersedia serta credential dikelola sebagai secret.
3. Bucket object storage private.
4. TLS/HTTPS digunakan pada environment yang diakses pengguna.
5. Database migration dijalankan secara terkontrol.
6. Backup PostgreSQL dan object storage dilakukan dengan prosedur yang konsisten.
7. Restore drill dilakukan sebelum go-live.
8. Ada prosedur pembuatan admin awal yang aman; tidak ada default credential permanen di source code.
9. Aplikasi menampilkan versi/build identifier untuk memudahkan support.
10. Centralized logging, antivirus, dan monitoring platform tetap ditunda; tetapi error handling, backup, audit trail bisnis, serta prosedur support dasar wajib tersedia.

## 27. Keputusan yang Sudah Dikunci dan Open Questions

### 27.1 Locked decisions

Keputusan berikut menjadi baseline dan tidak perlu dibuka ulang pada setiap task implementasi:

| Area | Keputusan baseline |
|---|---|
| Authentication | Local account yang dibuat/dikelola administrator; tidak ada public registration |
| Identity integration | SSO/OIDC/LDAP/AD/HRIS tidak diimplementasikan pada pilot |
| Session | Server-side session, secure cookie, dan CSRF protection |
| Frontend | React + TypeScript |
| Styling/UI | Tailwind CSS + reusable internal component system |
| Backend | Java + Spring Boot modular monolith |
| Database | PostgreSQL |
| Object storage | MinIO atau S3-compatible storage dengan bucket private |
| Authorization | RBAC + scope + resource ACL; backend menjadi otoritas akses |
| Organization master | Typed hierarchy `COMPANY → DIRECTORATE → DEPARTMENT → DIVISION`; unit lama tidak ditebak tipenya |
| Employee master | NIP unik, full name, email, employment status, satu active placement pada Biro/Division, history placement |
| Employee–User | Tepat satu User Account per Employee; data Employee tidak diduplikasi di User |
| Organization access | Unit grant mendukung descendant membership secara opt-in; grant lama tidak melebar |
| Navigation | Left sidebar pada desktop; responsive drawer pada tablet/mobile |
| Primary palette | Maroon `#8B1A1A`, putih, silver |
| Secondary palette | Hitam dan soft gold/kuning lembut |
| Deferred infrastructure | Antivirus, centralized logging, monitoring/APM, Redis, message broker, Kubernetes, dan microservices ditunda |
| Office links | URL hanya diregistrasikan; permission provider Office tidak disinkronkan |

Keputusan warna lengkap beserta semantic token dan contrast guidance berada pada `DESIGN_SYSTEM.md`. Batas module, storage flow, dan API berada pada `ARCHITECTURE.md`; model relational berada pada `SCHEMA.md`.

### 27.2 Open questions sebelum konfigurasi operasional final

| Area | Pertanyaan/keputusan |
|---|---|
| User scale | Berapa jumlah akun dan pengguna aktif pada pilot? |
| Organization | Model typed hierarchy sudah dikunci; yang masih terbuka adalah pemetaan unit dan data Employee aktual |
| Document governance | Siapa document owner dan Document Administrator tiap unit? |
| Classification | Apakah INTERNAL/CONFIDENTIAL/RESTRICTED sesuai kebijakan perusahaan? |
| File policy | Format apa yang diizinkan dan berapa batas maksimum file? |
| Office platform | Apakah Office URL terutama dari SharePoint/OneDrive atau link lainnya? |
| Request SLA | Apakah request mempunyai target penyelesaian? |
| Approval | Siapa approver default jika owner tidak tersedia? |
| Preview | Format apa yang harus dapat dipreview di browser? |
| Retention | Berapa lama histori dokumen dan audit disimpan? |
| Deployment | On-premise/private cloud dan backup target seperti apa yang tersedia? |
| Distribution | Apakah pengiriman ke satu unit harus selalu mencakup semua anggota aktif pada saat pengiriman? (baseline: ya, snapshot) |

Open questions ini tidak menghambat pembangunan baseline teknis. Namun, jawaban harus disepakati sebelum konfigurasi operasional, security policy final, dan UAT/go-live. Jangan mengisi nilai numerik atau kebijakan yang belum disetujui dengan asumsi buatan agent.

## 28. Definition of Ready dan Definition of Done

### Definition of Ready

Sebuah user story siap dikembangkan jika:
- tujuan dan aktor jelas;
- acceptance criteria dapat diuji;
- permission yang diperlukan terdefinisi;
- owner modul dan data jelas;
- state/error/empty case dipahami;
- dependensi dan migration diketahui;
- desain UI/API/data cukup untuk implementasi.

### Definition of Done

Sebuah user story selesai jika:
- implementasi berada pada modul yang benar;
- tidak melanggar clean-code/modular rules;
- frontend/backend validation tersedia sesuai kebutuhan;
- authorization diuji pada backend;
- automated tests lulus;
- migration dan API contract terdokumentasi bila berubah;
- audit event tersedia untuk aksi penting;
- error/loading/empty/success states tersedia;
- code review selesai;
- tidak ada known critical/high defect yang belum ditangani atau diterima secara eksplisit.

## 29. Kesimpulan PRD

PRD ini menetapkan kebutuhan produk KCIC Docs untuk pilot internal: local authentication, user and organization administration, RBAC dan resource permission, repository file/link, metadata dan versioning, request workflow, distribution, template management, in-app notification, dan audit trail.

Teknologi baseline: React + TypeScript, Tailwind CSS dengan reusable UI component system, Java Spring Boot modular monolith, PostgreSQL, serta MinIO atau object storage kompatibel S3. Navigasi utama memakai left sidebar yang berubah menjadi drawer pada viewport sempit. Palet visual memakai maroon `#8B1A1A`, putih, silver, hitam, dan soft gold sesuai `DESIGN_SYSTEM.md`. SSO/LDAP, integrasi Microsoft Graph, antivirus, centralized logging, monitoring platform, Redis, RabbitMQ, dan microservices ditunda.

**Clean code dan modularisasi adalah persyaratan wajib:** pembagian kode per domain/feature, pemisahan API/application/domain/infrastructure, batas antar-modul yang jelas, tidak ada business logic besar dalam controller atau UI component, automated tests, database migrations, serta quality gates sebelum merge/release.

Dokumen ini menjadi acuan kebutuhan produk untuk `ARCHITECTURE.md`, `SCHEMA.md`, `DESIGN_SYSTEM.md`, dan `IMPLEMENTATION_PLAN.md`. BRD dan System Analysis tetap menjadi sumber konteks bisnis dan analisis awal. Sebelum konfigurasi operasional final, open questions pada Bagian 27 harus diselesaikan bersama business owner, document owner, dan tim IT.
