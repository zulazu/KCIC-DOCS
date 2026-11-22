# KCIC Connect — User Acceptance Test (UAT) Pilot

Tanggal eksekusi: 2026-11-22
Lingkungan: lokal (backend Spring Boot :8080, Vite :5173, PostgreSQL 16 di container `kcic-postgres`, storage filesystem dev)
Metode: verifikasi browser (Playwright) per role, API langsung untuk kasus negatif, dan inspeksi database/audit.

Dokumen ini mencatat skenario yang **benar-benar dijalankan** beserta hasilnya. Bukan daftar rencana.

## 1. Akun UAT

| Akun | Role | Kegunaan |
|---|---|---|
| `admin` | SYSTEM_ADMIN | Administrasi penuh, verifikasi endpoint admin |
| `employee1` | EMPLOYEE (Head Office) | Katalog, request, notifikasi, ACL hasil request/distribusi |
| `employee3` | EMPLOYEE (Head Office) | Distribusi, pencabutan akses, negatif test |
| `auditor1` | AUDITOR (fixture UAT, dibuat melalui UI admin) | Audit trail read-only, verifikasi pemisahan role |

Password hanya dikelola di lingkungan UAT lokal; tidak dicatat di repository. Akun fixture `auditor1` dibuat melalui layar **User Management** dan wajib ganti password saat login pertama (diverifikasi).

## 2. Matriks skenario

### AUTH — Autentikasi dan sesi

| ID | Skenario | Hasil |
|---|---|---|
| AUTH-01 | Login kredensial valid membuat sesi server-side dan mengarahkan ke dashboard | PASS |
| AUTH-02 | Login kredensial salah menampilkan pesan generik dan tercatat `LOGIN_FAILURE` | PASS |
| AUTH-03 | Akun baru dengan password sementara dipaksa ke `/change-password` sebelum mengakses halaman lain | PASS (`auditor1`) |
| AUTH-04 | Password baru tervalidasi (≥12 karakter) dan session lain diakhiri | PASS |
| AUTH-05 | Logout mengakhiri sesi dan kembali ke halaman login | PASS |
| AUTH-06 | Akses halaman terproteksi tanpa sesi diarahkan ke login | PASS |

### USER / ORG / ROLE — Administrasi identitas

| ID | Skenario | Hasil |
|---|---|---|
| USER-01 | Admin membuat akun baru dengan password sementara | PASS (`auditor1`) |
| USER-02 | Admin menugaskan role global (AUDITOR) ke akun | PASS |
| USER-03 | Role/capability menentukan menu sidebar dan akses API | PASS (`auditor1` hanya melihat Dashboard + Audit Trail) |
| USER-04 | Non-admin membuka `/admin/users` mendapat state "Akses tidak diizinkan"; API menolak 403 | PASS (employee3, auditor1) |
| USER-05 | Perubahan status akun (nonaktif/kunci) mengakhiri akses | PASS (Phase 2, integration test) |

### MASTER / REPO — Master data dan repository

| ID | Skenario | Hasil |
|---|---|---|
| MASTER-01 | CRUD kategori/jenis/klasifikasi/tag dan deaktivasi | PASS (Phase 3) |
| MASTER-02 | File policy divalidasi server; input token tidak valid ditolak, bukan diubah diam-diam | PASS (defect diperbaiki) |
| REPO-01 | Buat workspace dan folder, validasi nama duplikat dan siklus parent | PASS |
| REPO-02 | Buat dokumen, unggah versi PDF, preview inline, unduh dengan nama asli | PASS |
| REPO-03 | Unggah versi baru: versi lama `SUPERSEDED`, versi baru `CURRENT` | PASS |
| REPO-04 | Promosikan versi lama menjadi `CURRENT` | PASS |
| REPO-05 | Arsipkan/restore dokumen; dokumen arsip menolak unggah versi baru | PASS |
| REPO-06 | Tautan eksternal `http://` ditolak, `https://` dicatat sebagai versi EXTERNAL_LINK | PASS |
| REPO-07 | Ubah owner user→unit wajib alasan dan mencatat riwayat kepemilikan | PASS (defect 500 diperbaiki) |
| REPO-08 | Pencarian katalog (keyword, workspace, kategori, jenis, klasifikasi, status, sort) | PASS |

### ACL — Hak akses resource

| ID | Skenario | Hasil |
|---|---|---|
| ACL-01 | Grant ALLOW dokumen kepada employee di luar unit membuat dokumen terlihat dan dapat dibuka | PASS |
| ACL-02 | DENY eksplisit mengalahkan ALLOW warisan workspace/folder | PASS |
| ACL-03 | Revoke grant membuat akses berikutnya ditolak (UI + API) | PASS |
| ACL-04 | Grant dari sumber lain tetap berlaku setelah satu sumber dicabut | PASS (DISTRIBUTION vs MANUAL/REQUEST) |
| ACL-05 | Duplikasi grant aktif → 409; permission tidak berlaku untuk tipe resource → 400 | PASS (integration test) |

### REQ — Request dan approval

| ID | Skenario | Hasil |
|---|---|---|
| REQ-01 | Employee membuat ACCESS request; approver dirutekan otomatis dan menerima notifikasi | PASS (`REQ-2026-00001`) |
| REQ-02 | Approver memulai review dan menyetujui dengan catatan | PASS |
| REQ-03 | Fulfillment akses membuat grant `REQUEST` dan requester dapat membuka dokumen | PASS |
| REQ-04 | Requester menutup request `FULFILLED → CLOSED` | PASS |
| REQ-05 | Transisi tidak valid/diulang ditolak 409 | PASS (integration test) |
| REQ-06 | Notifikasi: bell, mark-read, mark-all-read, deep link ke target | PASS |

### DIST — Distribusi

| ID | Skenario | Hasil |
|---|---|---|
| DIST-01 | Admin mengirim distribusi ke employee3 (level DOWNLOAD) | PASS (`DIST-2026-00002`) |
| DIST-02 | Penerima melihat distribusi di tab "Diterima" dan membuka dokumen | PASS |
| DIST-03 | Sender mencabut penerima; status menjadi `REVOKED` | PASS |
| DIST-04 | Akses dokumen penerima ditolak setelah revoke | PASS |
| DIST-05 | Grant independen milik penerima lain tidak ikut tercabut | PASS |
| DIST-06 | Validasi penerima/dokumen/expiry di backend | PASS (integration test) |

### TEMPLATE — Katalog template

| ID | Skenario | Hasil |
|---|---|---|
| TEMP-01 | Create template dari dokumen, publish versi CURRENT | PASS (`TPL_SOP_OPS`) |
| TEMP-02 | Employee tanpa akses dokumen tidak melihat metadata template | PASS |
| TEMP-03 | Unduh versi lama menampilkan dialog peringatan sebelum unduh | PASS (TEMPLATE-04.3) |
| TEMP-04 | Usage VIEW/DOWNLOAD tercatat pada versi published saja | PASS |
| TEMP-05 | Arsip template menyembunyikan dari katalog umum, riwayat tetap ada | PASS |

### AUDIT — Audit trail (Phase 9)

| ID | Skenario | Hasil |
|---|---|---|
| AUD-01 | Admin membuka `/admin/audit`, melihat event terbaru dengan paginasi server | PASS (157 event, 1/8 halaman) |
| AUD-02 | Filter aktor + aksi + outcome mengembalikan hasil yang tepat | PASS (`employee1` + `LOGIN_SUCCESS` + `SUCCESS` → 10/10) |
| AUD-03 | Filter resolusi hari (`from`/`to`) dan validasi `from > to` → 400 | PASS (integration test) |
| AUD-04 | Auditor (role AUDITOR) dapat membuka audit trail dan hanya melihat menu tersebut | PASS (`auditor1`, 162 event) |
| AUD-05 | Non-auditor mendapat 403 dari API dan state forbidden di UI | PASS (employee3, auditor1 untuk `/admin/users` dan `/documents`) |
| AUD-06 | Metadata event tidak memuat secret/URL storage | PASS (inspeksi DB; hanya id/status/nomor versi) |

### UX / Responsif / Rilis

| ID | Skenario | Hasil |
|---|---|---|
| UX-01 | Sidebar maroon `#8B1A1A` sebagai navigasi utama desktop, drawer <768 px | PASS (1440×900 dan 390×844) |
| UX-02 | Active route ditandai warna + edge emas + `aria-current` | PASS |
| UX-03 | Loading, empty, error, forbidden, not-found, conflict state tersedia per konteks | PASS |
| UX-04 | Deep link langsung (`/documents/{id}`, `/admin/audit`) dan refresh halaman me-*load* chunk lazy tanpa error console | PASS (Phase 9) |
| UX-05 | Tidak ada horizontal overflow pada halaman katalog/detail di 390 px | PASS |
| REL-01 | Code splitting route: bundle utama 365 kB (gzip 113 kB), tanpa warning >500 kB | PASS (Phase 9) |
| REL-02 | Restore drill DB + storage berhasil dengan jumlah baris identik | PASS (Phase 9) |

### ORG / EMP — Organization & Identity Enhancement (E-series, **belum dieksekusi**)

Skenario berikut adalah acceptance criteria untuk E1–E8; status awal `NOT_RUN`. Jangan menandai PASS sebelum ada bukti eksekusi pada build E-series dan data HR terverifikasi.

| ID | Skenario | Hasil |
|---|---|---|
| ORG-E01 | Hierarchy typed: satu COMPANY root; DIRECTORATE→COMPANY, DEPARTMENT→DIRECTORATE, DIVISION→DEPARTMENT; DIVISION tanpa child | NOT_RUN |
| ORG-E02 | Pembuatan unit dengan parent tipe salah ditolak dengan pesan field yang jelas | NOT_RUN |
| ORG-E03 | Unit lama tanpa tipe tetap berfungsi dan tidak otomatis mendapat tipe | NOT_RUN |
| EMP-E01 | Admin membuat Employee dengan NIP/email unik; duplikasi ditolak | NOT_RUN |
| EMP-E02 | Employee ditempatkan pada Biro/Division; Directorate/Department ditampilkan turunan dan read-only | NOT_RUN |
| EMP-E03 | Transfer menutup placement lama dan membuat satu placement aktif baru secara transaksional | NOT_RUN |
| EMP-E04 | Dua placement aktif untuk employee yang sama ditolak database (concurrent transfer → 409) | NOT_RUN |
| EMP-E05 | Placement pada unit non-DIVISION/inactive ditolak | NOT_RUN |
| EMP-E06 | Riwayat placement tetap terlihat setelah transfer/termination | NOT_RUN |
| EMP-USER-E01 | Satu Employee hanya dapat memiliki satu akun; akun kedua/relink ditolak 409 | NOT_RUN |
| EMP-USER-E02 | Employee TERMINATED dengan akun ACTIVE terdeteksi oleh pemeriksaan operasional dan diblokir sesuai kebijakan | NOT_RUN |
| EMP-USER-E03 | Akun tanpa `employee_id` = 0 sebelum V18 (tanpa exception; DEC-068) | NOT_RUN |
| AUTHZ-E01 | Grant `includeDescendants=true` pada Directorate memberi akses ke Biro/Division turunan | NOT_RUN |
| AUTHZ-E02 | Grant yang sama tidak memberi akses ke unit sibling/tidak terkait | NOT_RUN |
| AUTHZ-E03 | DENY eksplisit pada Biro/Division anak mengalahkan ALLOW descendant | NOT_RUN |
| AUTHZ-E04 | Grant lama (pre-V17) tetap exact dan tidak mendapat akses turunan setelah upgrade | NOT_RUN |
| AUTHZ-E05 | Revoke satu grant descendant tidak mencabut grant independen lain | NOT_RUN |
| AUTHZ-E06 | Percobaan IDOR `/admin/employees/{uuid}` dari scope lain → 404/403 sesuai kebijakan | NOT_RUN |
| MIGR-E01 | Preflight V18 kosong untuk akun manusia/placement/tipe unit; import HR tercatat | NOT_RUN |
| MIGR-E02 | Distribution snapshot dan request routing tetap berperilaku sama setelah cutover | NOT_RUN |

Bukti otomatis yang sudah tersedia pada build E-series (bukan pengganti UAT manusia):

- ORG-E01/E02/E03, EMP-E04/E05 — `OrganizationHierarchyIntegrationTest`, `EmployeeIntegrationTest`.
- EMP-E01/E03/E06, EMP-USER-E01 — `EmployeeIntegrationTest`, `EmployeeAccountIntegrationTest`.
- EMP-USER-E02 — `EmploymentLifecycleSecurityIntegrationTest` (login ditolak, sesi berakhir) + `tools/ops/run-employment-check.sh` (E8-OPS-01; dev run 0 violations).
- AUTHZ-E01–E05 — `DescendantScopeIntegrationTest`, `AclIntegrationTest.revokingOneGrantKeepsIndependentGrantIntact`.
- AUTHZ-E06 — `EmploymentLifecycleSecurityIntegrationTest.employeeEndpointsDenyAccountsWithoutAdminCapability` (capability guard 403).
- DIST (MIGR-E02 sebagian) — `DistributionIntegrationTest` (snapshot bertahan setelah transfer, penerima non-aktif dikecualikan).
- MIGR-E01 — `tools/preflight/run-e6-preflight.sh` (belum dijalankan dengan data HR).

Status kolom tabel tetap `NOT_RUN` sampai skenario dijalankan manusia pada environment dengan data HR terverifikasi.

Catatan: skenario di atas membutuhkan dataset HR terverifikasi dan keputusan pemetaan unit (blocker B-1/B-2). UAT E-series belum dapat dijadwalkan sebelum itu.

## 3. Defect yang ditemukan saat UAT dan perbaikannya

| Defect | Perbaikan |
|---|---|
| `GET /api/v1/admin/users` 500 saat keyword null (`lower(bytea)`) | Normalisasi keyword non-null + `:keyword = ''`; regression test |
| File policy textarea memecah token berspasi menjadi beberapa token | Split hanya koma/newline; error field dipetakan ke field |
| Dialog menyimpan error lama antar pembukaan | Reset per-open + remount konten |
| Workspace detail overflow 390 px | Layout dua baris, tanpa `nowrap` |
| Dokumen arsip masih menampilkan aksi unggah/tautan | Aksi disembunyikan mengikuti aturan backend |
| Owner unit menyebabkan 500 saat update metadata | `nameFor` null-safe + regression assertion |
| Karyawan luar unit masih 403 meski punya grant workspace | ACL visibility diterapkan di workspace/folder |
| Item "Requests" tampil untuk user tanpa capability request | Aturan navigasi diperbaiki + unit test |
| Chip dokumen hilang saat keyword berubah | Label disimpan per id terpilih |
| Salinan "Akses Anda" salah setelah revoke | Teks mengikuti status `REVOKED/EXPIRED` |
| Template history tidak bisa diunduh dengan peringatan | Dialog peringatan + unduh versi historis terotorisasi |

## 4. Known limitations yang perlu disetujui bisnis

1. Tanpa antivirus/malware scanning; validasi file hanya struktural (ekstensi, MIME, signature, ukuran).
2. Expiry/revoke berlaku pada API call berikutnya; halaman/file yang sudah terbuka tidak ditarik.
3. Tidak ada SSO/MFA; autentikasi lokal dengan lockout sebagai kontrol pengganti.
4. Tidak ada email/WhatsApp/push; notifikasi hanya in-app.
5. Tautan Office hanya dicatat; izin di provider eksternal tidak dikelola.
6. Pencarian metadata saja; isi dokumen tidak diindeks.
7. Expiry distribusi bersifat lazy (dinilai saat dibaca), bukan job terjadwal.
8. Katalog memfilter visibilitas di memori hingga 1000 kandidat per pembacaan.
9. Template arsip belum memiliki alur unarchive pada pilot.
10. Employee master, NIP, typed units, dan descendant ACL belum tersedia sampai E-series selesai; UAT E-series masih `NOT_RUN`.
11. Data HR (NIP/email/placement) dan pemetaan tipe unit harus berasal dari sumber resmi; tidak ada data sintetis pada environment bersama.

## 5. Sign-off

| Peran | Nama | Tanggal | Keputusan |
|---|---|---|---|
| Product Owner | | | |
| Business Owner (dokumen) | | | |
| IT / Security | | | |
| Engineering | | | |

Catatan: kolom keputusan diisi setelah sesi UAT dengan pengguna KCIC. Hasil teknis di atas adalah bukti eksekusi pada lingkungan pilot, bukan pengganti persetujuan bisnis.
