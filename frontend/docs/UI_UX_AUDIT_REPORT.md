# KCIC Connect — UI/UX Audit Report

| Metadata | Nilai |
|---|---|
| Produk | KCIC Connect |
| Dokumen | Complete UI/UX Audit |
| Versi | 1.0 |
| Tanggal | 22 November 2026 |
| Metode | `design-review:ui-alignment-review` (mode regression) |
| Evidence | Render langsung aplikasi lokal + screenshot di `frontend/docs/ux-evidence/` |
| Status | Audit selesai; seluruh temuan must-fix sudah diimplementasikan dan diverifikasi |

---

## 1. Ringkasan

Aplikasi sudah stabil secara fungsional, tetapi antarmuka sebelum audit memperlihatkan gejala UI generik: dashboard berupa tumpukan kartu seragam, deskripsi halaman yang mengulang nama menu, filter yang berbeda-beda antarhalaman, pagination yang tidak seragam, dan satu halaman dengan daftar aktivitas sepanjang ±3.000 px. Audit ini menginventaris seluruh route, mengklasifikasikan temuan, lalu memperbaikinya pada shared primitives dan halaman fitur tanpa mengubah kontrak API, authorization, maupun alur bisnis.

Hasil akhir: 25 route diaudit, 14 temuan dicatat (5 must-fix, 5 should-fix, 3 polish, 1 needs-design-decision), 12 sudah diperbaiki di kode, 2 didokumentasikan sebagai acceptable difference/decision.

## 2. Metode dan batas bukti

- Mode review: **regression** — repository tidak memiliki frame Figma atau baseline screenshot resmi. Baseline yang dipakai adalah `DESIGN_SYSTEM.md` v1.1 beserta aturan visual pada brief audit.
- Konsekuensi: verdict bukan "design-approved" terhadap Figma. Verdict adalah kesesuaian terhadap design system tertulis dan hasil render aktual.
- Render diverifikasi dengan browser Playwright pada aplikasi lokal (`localhost:5173`, backend `localhost:8080`) memakai akun administrator nyata dan data lokal yang ada. Tidak ada data mock yang disuntikkan.
- Viewport: 1536×960, 1280×900, 1024×800, 768×900, 390×844 (device scale factor 1).
- Dynamic content (nama, waktu, jumlah data) tidak dimask karena halaman diaudit secara visual, bukan diff piksel.
- Bukti screenshot: `frontend/docs/ux-evidence/` (35 file).

## 3. Inventaris route

| # | Route | Halaman | Cakupan audit | Bukti |
|---|---|---|---|---|
| 1 | `/login` | Login | Layout, form, error state | kode + render |
| 2 | `/change-password` | Ubah password | Layout, form, validasi | `ui-1536-change-password.png` |
| 3 | `/` | Dashboard | Bento, hierarchy, 5 viewport | `ui-1536-dashboard-v4.png`, `ui-1280/1024/390-dashboard.png` |
| 4 | `/documents` | Dokumen | Tabel, filter, pagination, 768/390 | `ui-1536-documents-v3.png`, `ui-768-documents-v4.png`, `ui-768-documents-filter-open.png`, `ui-390-documents-v2.png` |
| 5 | `/documents/:id` | Detail dokumen | Header, versi, aktivitas, 390 | `ui-1536-document-detail-final.png`, `ui-390-document-detail.png` |
| 6 | `/workspaces` | Workspaces | Tabel + filter | `ui-1536-workspaces.png` |
| 7 | `/workspaces/:id` | Detail workspace | Header + folder | kode + render |
| 8 | `/requests` | Requests | Tabel + tab | `ui-1536-requests.png` |
| 9 | `/requests/:id` | Detail request | Workflow panel + timeline | kode + render |
| 10 | `/distributions` | Distribusi | Tab + filter + empty state | `ui-1536-distributions.png` |
| 11 | `/distributions/:id` | Detail distribusi | Recipient + revoke dialog | kode + render |
| 12 | `/templates` | Template | Filter 5 kolom + tabel | `ui-1536-templates-v2.png` |
| 13 | `/templates/:id` | Detail template | Versi + publikasi | kode + render |
| 14 | `/notifications` | Notifikasi | List + pagination | `ui-1536-notifications.png` |
| 15 | `/admin/users` | User Management | Tabel + filter | `ui-1536-users.png` |
| 16 | `/admin/users/:id` | Detail user | Form + role/ACL | `ui-390-dialog-edit.png` (dialog edit) |
| 17 | `/admin/employees` | Employee | Tabel + filter | `ui-1536-employees.png` |
| 18 | `/admin/employees/:id` | Detail employee | Placement + akun | kode + render |
| 19 | `/admin/roles` | Authorization | Tabel role | `ui-1536-roles.png` |
| 20 | `/admin/roles/:id` | Detail role | Permission matrix | kode + render |
| 21 | `/admin/organization` | Organization | Pohon unit + aksi | `ui-1536-organization.png` |
| 22 | `/admin/master-data` | Master Data | Tab + tabel | `ui-1536-master-data.png` |
| 23 | `/admin/audit` | Audit Trail | Filter + pagination | `ui-1536-audit.png`, `ui-1536-audit-pagesize50.png` |
| 24 | `/admin/migration` | Migrasi akun | Stat tiles + daftar | `ui-1536-migration.png` |
| 25 | `*` | Not found | Empty state + kembali | kode |

Catatan cakupan: halaman detail yang tidak memiliki record lokal (beberapa request/distribusi/template) diaudit melalui kode komponen bersama dan render halaman detail yang tersedia (dokumen, user, employee). Tidak ada klaim render untuk state yang datanya tidak ada.

## 4. Ringkasan temuan

| Severity | Jumlah | Status |
|---|---|---|
| Must-fix | 5 | 5 diperbaiki |
| Should-fix | 5 | 5 diperbaiki |
| Polish | 3 | 2 diperbaiki, 1 diterima |
| Needs-design-decision / acceptable | 1 | didokumentasikan |

## 5. Must-fix

### UX-001 — Dashboard tidak punya hierarchy dan menyisakan lubang layout besar

- Kategori: layout, hierarchy
- Bukti: render 1536 sebelum perbaikan; kartu-kartu berbobot sama dalam satu kolom, kolom kanan kosong dan halaman terasa seperti template.
- Perbaikan: dashboard ditulis ulang menjadi dua kolom independen `xl:grid-cols-[minmax(0,1fr)_360px]`; tile fungsional per capability (request ditugaskan, dokumen terbaru, distribusi terbaru, notifikasi, akun, administrasi). Tile tanpa izin tidak dirender, bukan dirender kosong.
- Verifikasi: `ui-1536-dashboard-v4.png` dan `ui-1536-dashboard-final.png` (tanpa lubang layout), `ui-1280-dashboard.png`, `ui-1024-dashboard.png`, `ui-390-dashboard.png` (stack satu kolom).

### UX-002 — Hook React dipanggil setelah early return di dashboard

- Kategori: correctness (lint)
- Bukti: `npm run lint` melaporkan 5× `react-hooks/rules-of-hooks`.
- Perbaikan: seluruh `useQuery` dan turunannya dipindah ke atas early return; `if (!me) return null` menjadi guard terakhir sebelum render.
- Verifikasi: lint 0 error (lihat `UI_UX_VERIFICATION_REPORT.md`).

### UX-003 — Pagination tidak seragam dan tanpa pilihan ukuran halaman

- Kategori: component, data table
- Bukti: beberapa halaman hanya punya tombol Sebelumnya/Berikutnya; tidak ada rentang data, tidak ada 10/25/50/100.
- Perbaikan: `PaginationBar` bersama dengan teks rentang `aria-live`, pemilih baris 10/25/50/100, reset ke halaman 0 saat ukuran berubah, dan `keepPreviousData` saat pindah halaman.
- Verifikasi: audit 1536 → `Menampilkan 1–50 dari 196 event`, halaman `1 / 4` (`ui-1536-audit-pagesize50.png`); notifikasi/employee/dokumen memakai pola yang sama.

### UX-004 — Filter panel 768 px memakan seluruh layar

- Kategori: responsive
- Bukti: pada 768 px filter dokumen tampil sebagai form penuh sebelum daftar; pengguna harus melewati filter untuk melihat data.
- Perbaikan: `FilterPanel` memakai toggle collapsible di bawah `lg` (`<button aria-expanded aria-controls>` + badge jumlah filter aktif); isi panel hanya selalu terbuka pada `lg+`.
- Verifikasi: `ui-768-documents-v4.png` (collapsed) dan `ui-768-documents-filter-open.png` (expanded).

### UX-005 — Daftar aktivitas dokumen sepanjang ±3.000 px

- Kategori: layout, content density
- Bukti: 38 event audit dirender sekaligus; panel kanan mendominasi halaman detail.
- Perbaikan: tampilkan 8 event pertama dengan tombol `Tampilkan semua (n)` / `Tampilkan lebih sedikit` (`aria-expanded`).
- Verifikasi: `ui-1536-document-detail-final.png`, `ui-390-document-detail.png`.

## 6. Should-fix

### UX-006 — Deskripsi halaman mengulang nama menu

- Kategori: copy
- Contoh sebelum: halaman Workspaces mendeskripsikan ulang "workspace"; Roles berjudul "Roles" dengan deskripsi redundan.
- Perbaikan: deskripsi hanya dipertahankan bila menambah informasi (scope, konsekuensi); judul "Authorization" tanpa deskripsi; copy dipangkas di Workspaces, Organization, Document detail, Migration.
- Verifikasi: screenshot 1536 halaman terkait.

### UX-007 — Filter card berbeda antarhalaman

- Kategori: component, consistency
- Bukti: Dokumen memakai card dengan Reset; Distribusi memakai form tanpa Reset/count; Audit memakai card berpadding ganda; Users/Employee menaruh filter di dalam card tabel.
- Perbaikan: semua halaman daftar dengan filter multi-field memakai `FilterPanel` dengan `activeCount` dihitung dari nilai yang sudah diterapkan (bukan ketikan yang belum disubmit), tombol Terapkan, dan Reset hanya saat ada filter. Halaman dengan satu pencarian (Workspaces, Organization, tab Master Data) tetap memakai toolbar inline yang seragam — variasi ini didokumentasikan di `COMPONENT_STANDARD.md`.
- Verifikasi: `ui-1536-documents-v3.png`, `ui-1536-templates-v2.png`, `ui-1536-audit.png`, `ui-1536-users-final.png`, `ui-1536-employees-final.png`, `ui-768-users.png`.

### UX-008 — Daftar administrasi di dashboard terlalu panjang

- Kategori: hierarchy
- Bukti: seluruh menu admin dirender sebagai daftar besar di kolom utama.
- Perbaikan: pindah ke rail kanan sebagai daftar ringkas; konten kerja (request/dokumen/distribusi) memegang kolom utama.
- Verifikasi: `ui-1536-dashboard-v4.png`.

### UX-009 — Tombol Terapkan pada filter Template melayang di tengah baris

- Kategori: spacing/layout
- Bukti: grid 5 kolom menyisakan satu sel kosong di kanan tombol.
- Perbaikan: action row `sm:col-span-2 xl:col-span-4` dengan `justify-end`; Reset berdampingan.
- Verifikasi: `ui-1536-templates-v2.png`.

### UX-010 — Halaman Migrasi akun terlalu padat penjelasan

- Kategori: copy
- Bukti: callout 3 baris + deskripsi 2 baris mengulang batasan yang sama.
- Perbaikan: deskripsi menjadi satu kalimat; callout dua kalimat yang mempertahankan batasan HR dan bootstrap admin.
- Verifikasi: `ui-1536-migration-final.png`.

## 7. Polish

| ID | Temuan | Tindakan |
|---|---|---|
| UX-011 | Dialog konfirmasi memakai tombol ad-hoc, tidak konsisten dengan `Button` | `ConfirmDialog` memakai `Button` (secondary/brand/danger) + loading state |
| UX-012 | Tagline "Semua akses dokumen tercatat pada audit trail." diulang di footer setiap halaman | Dihapus; footer hanya memuat identitas produk |
| UX-013 | Beberapa empty state memakai paragraf penjelas panjang | Diterima untuk halaman diagnostik (Migrasi) karena menjelaskan batasan data; halaman lain sudah ringkas |

## 8. Acceptable difference dan keputusan desain

- **ADR-UI-01 — Tidak ada grafik chart di dashboard.** Brief meminta visualisasi; backend belum menyediakan endpoint agregat time-series. Membuat chart dengan data karangan akan melanggar aturan "no fake data". Dashboard memakai angka dan status nyata (jumlah request, versi, distribusi, notifikasi unread) sebagai visualisasi fungsional. Grafik tren memerlukan endpoint agregasi baru dan keputusan produk.
- **ADR-UI-02 — Master Data tidak memakai pagination server-side.** Endpoint master data mengembalikan daftar konfigurasi kecil yang dibatasi proses bisnis (kategori, tipe, klasifikasi, tag, file policy). Pagination server-side akan mengubah kontrak API tanpa kebutuhan nyata. Tabel tetap memakai pola header/aksi yang sama.
- **ADR-UI-03 — Bahasa label navigasi campuran.** Nama grup mengikuti sitemap PRD (Workspace, Requests, Distribution, Templates, Administration). Label item seperti "Employee", "Organization", "Authorization" dipertahankan karena istilah domain administratif yang sudah dipakai dokumen produk. Standardisasi bahasa menu adalah keputusan istilah produk, bukan perbaikan visual.
- **ADR-UI-04 — Sort kolom belum tersedia.** Endpoint daftar belum mengekspos parameter sort; menambahkan kontrol UI tanpa dukungan backend akan menyesatkan.

## 9. Setup / evidence issues

- Tidak ada frame Figma atau baseline screenshot resmi; verdict memakai design system tertulis (lihat §2).
- E2E otomatis tidak dapat dijalankan: environment tidak memiliki `KCIC_E2E_ADMIN_USERNAME/PASSWORD` dan `KCIC_E2E_EMPLOYEE_USERNAME/PASSWORD`; README menyatakan suite gagal cepat tanpa kredensial dan password tidak boleh di-commit. Verifikasi fungsional dilakukan manual melalui browser dengan akun lokal.
- Halaman detail tanpa record lokal tidak dapat dirender dengan data nyata; diverifikasi lewat kode dan halaman detail lain yang berbagi komponen.

## 10. Artefak

- Screenshot: `frontend/docs/ux-evidence/` — 35 PNG, termasuk dashboard 4 viewport, dokumen 768/390, drawer mobile, dialog 390, skip link focus, audit page size 50, dan halaman administrasi 1536.
- Design system: `DESIGN_SYSTEM.md` v1.1.
- Spesifikasi dan standar: `frontend/docs/UI_UX_DESIGN_SPECIFICATION.md`, `frontend/docs/COMPONENT_STANDARD.md`.
- Implementasi dan verifikasi: `frontend/docs/UI_UX_IMPLEMENTATION_REPORT.md`, `frontend/docs/UI_UX_VERIFICATION_REPORT.md`.
