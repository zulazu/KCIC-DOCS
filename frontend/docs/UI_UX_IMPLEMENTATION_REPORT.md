# KCIC Connect — UI/UX Implementation Report

| Metadata | Nilai |
|---|---|
| Produk | KCIC Connect |
| Dokumen | Implementation Report |
| Versi | 1.2 |
| Tanggal | 22 November 2026 |
| Lingkup | Frontend presentation layer (tokens, shared components, pages, copy) |
| Perubahan backend | Tidak ada |
| Status | Implemented dan verified pada gate frontend; E2E otomatis BLOCKED (kredensial environment) |

---

## 1. Ringkasan eksekusi

Pekerjaan dilakukan dalam urutan: inventaris dan render audit → perbaikan token/primitif → redesain dashboard dan hierarchy halaman → standardisasi tabel/filter/pagination → polish detail, dialog, copy → verifikasi responsif dan aksesibilitas → gate dan dokumentasi.

Tidak ada API, schema, migration, authorization, workflow, ACL, atau aturan bisnis yang diubah. Seluruh perubahan berada di React/TypeScript dan Tailwind token.

## 2. Perubahan per area

### 2.1 Token dan primitif

| File | Perubahan | Alasan |
|---|---|---|
| `frontend/src/shared/styles/globals.css` | Menambah `--color-danger-hover` (#9A1D14) dan `--color-danger-pressed` (#801810) | State hover/pressed aksi destruktif sebelumnya tidak punya token |
| `frontend/src/shared/components/FilterPanel.tsx` | Komponen baru: card filter responsif, toggle `< lg` dengan `aria-expanded`/`aria-controls`, badge jumlah filter aktif | Menyatukan filter dan menghemat tinggi layar di tablet/mobile |
| `frontend/src/shared/components/PaginationBar.tsx` | Komponen baru: rentang data `aria-live`, ukuran 10/25/50/100, tombol prev/next, indikator halaman | Standardisasi pagination server-side |
| `frontend/src/shared/components/Dialog.tsx` | `ConfirmDialog` memakai `Button` bersama; tone danger memakai token danger | Konsistensi aksi dan state loading |
| `frontend/src/shared/components/Surface.tsx` | `PageHeader` mendukung `meta` (identifier + status) dengan satu `h1` | Hierarchy halaman detail |

Catatan: tidak ada komponen baru yang bergantung pada library eksternal; semua memakai React dan token yang ada.

### 2.2 Shell dan navigasi

| File | Perubahan |
|---|---|
| `frontend/src/app/layouts/AppShell.tsx` | Footer disederhanakan menjadi identitas produk; tagline audit yang berulang dihapus |
| `frontend/src/app/layouts/Sidebar.tsx` | Item aktif memakai latar `white/15`, indikator gold 3 px, label grup `white/65`; drawer mobile tetap `<dialog>` native |

### 2.3 Dashboard

`frontend/src/features/dashboard/DashboardPage.tsx` ditulis ulang:

- Semua hook dipindah sebelum early return (memperbaiki 5 error `rules-of-hooks`).
- Layout dua kolom independen pada `xl` (`minmax(0,1fr)` + rail 360 px); satu kolom di bawah `xl`.
- Tile fungsional dan capability-gated: request ditugaskan, dokumen terbaru, distribusi terbaru, notifikasi (dengan angka belum dibaca), akun & akses, administrasi ringkas.
- Setiap tile memiliki loading skeleton (`role="status"`), error dengan Coba lagi, dan empty state.
- Tidak ada grafik atau angka karangan; lihat ADR-UI-01 di `UI_UX_AUDIT_REPORT.md`.

### 2.4 Halaman daftar

| File | Perubahan |
|---|---|
| `features/documents/DocumentsPage.tsx` | Filter pindah ke `FilterPanel`; pagination standar; reset page saat filter/size berubah |
| `features/requests/RequestsPage.tsx` | `FilterPanel` + `PaginationBar`; tab status tetap |
| `features/distributions/DistributionsPage.tsx` | Filter card → `FilterPanel`; tombol Terapkan; tab Diterima/Terkirim dengan state terpilih yang jelas |
| `features/templates/TemplatesPage.tsx` | `FilterPanel`; action row `sm:col-span-2 xl:col-span-4`; Reset kondisional |
| `features/notifications/NotificationsPage.tsx` | `PaginationBar` dengan label "notifikasi"; filter belum dibaca tetap ringkas |
| `features/administration/users/UsersPage.tsx` | Filter diekstrak ke `FilterPanel`; card tabel terpisah; header tabel `surface-muted`; `activeFilterCount` |
| `features/administration/employees/EmployeesPage.tsx` | Sama dengan Users; tiga filter (keyword, status, akun) dihitung di badge |
| `features/administration/audit/AuditPage.tsx` | Filter card → `FilterPanel`; pagination dengan ukuran halaman |

Halaman dengan satu kontrol pencarian (Workspaces, Organization, tab Master Data) mempertahankan toolbar inline; aturannya dicatat di `COMPONENT_STANDARD.md` §6.

### 2.5 Halaman detail dan copy

| File | Perubahan |
|---|---|
| `features/documents/DocumentDetailPage.tsx` | Kode dokumen menjadi chip mono di `PageHeader.meta`; deskripsi panel yang mengulang judul dihapus; aktivitas dibatasi 8 item + tombol ekspansi `aria-expanded` |
| `features/administration/roles/RolesPage.tsx` | Deskripsi redundan dihapus; judul "Authorization" |
| `features/administration/organization/OrganizationPage.tsx` | Deskripsi dipangkas menjadi satu kalimat yang menjelaskan fungsi scope |
| `features/workspaces/WorkspacesPage.tsx` | Deskripsi dipangkas |
| `features/administration/migration/MigrationPage.tsx` | Deskripsi satu kalimat; callout HR dipangkas dari tiga kalimat menjadi dua tanpa menghilangkan batasan data |

### 2.6 Aksesibilitas — pipeline axe-core dan perbaikan temuan

| File | Perubahan |
|---|---|
| `frontend/src/test/axe.ts` | Helper axe-core bersama untuk vitest; tag WCAG 2.0–2.2 A/AA; `color-contrast` dinonaktifkan di jsdom (diverifikasi di browser) |
| `frontend/src/test/accessibility.test.tsx` | 11 test axe untuk primitif bersama dan komposisi page-level; gagal bila ada violation |
| `frontend/src/test/setup.ts` | Polyfill minimal `showModal`/`close` `<dialog>` untuk jsdom |
| `frontend/e2e/a11y.spec.ts` | Suite Playwright + `@axe-core/playwright`: login, 15 route terautentikasi, mobile 390; membutuhkan kredensial E2E (BLOCKED lokal) |
| `frontend/src/app/layouts/Sidebar.tsx` | Brand mark: `bg-white/10` → `bg-primary-pressed` (gold 3.88:1 → 6.63:1); subjudul `white/60` → `white/75` (4.19:1 → 5.81:1) |
| `frontend/src/features/auth/LoginPage.tsx`, `ChangePasswordPage.tsx` | Subjudul `white/60` → `white/75` |
| `frontend/src/features/documents/DocumentDetailPage.tsx` | SHA-256 `text-ink-disabled` → `text-ink-secondary` (2.84:1 → 5.66:1); baris versi aktif dibungkus `<ul>` (rule `listitem`) |

Dependency baru hanya dev: `axe-core` dan `@axe-core/playwright`. Hasil scan ulang browser live: 0 violation pada 17 route @1536 dan 4 scan @390. Rincian di `UI_UX_VERIFICATION_REPORT.md` §5.3.

## 3. Perilaku yang benar-benar selesai

1. Navigasi kiri berfungsi pada desktop (expanded/collapsed), drawer pada `< lg`, dan preferensi collapse tersimpan.
2. Dashboard menampilkan data nyata per capability tanpa kartu kosong dan tanpa lubang layout pada 1536/1280/1024/390.
3. Semua daftar dengan filter multi-field memakai `FilterPanel`; di 768 px filter terlipat dan dapat dibuka dengan keyboard.
4. Pagination menampilkan rentang dan total, mendukung 10/25/50/100, dan mereset halaman saat ukuran berubah. Diverifikasi live pada Audit (196 event).
5. Dokumen detail tidak lagi memiliki daftar aktivitas yang mendominasi halaman.
6. Dialog konfirmasi konsisten dan menonaktifkan tombol saat proses berjalan.
7. Copy halaman tidak lagi mengulang nama menu; footer tidak lagi mengulang tagline di setiap halaman.

## 4. Bukti visual (before → after)

| Area | Sebelum | Sesudah |
|---|---|---|
| Dashboard 1536 | Tumpukan kartu satu kolom | `ui-1536-dashboard-final.png`, `ui-1536-dashboard-v4.png` |
| Dokumen 1536 | Filter card tanpa pola seragam | `ui-1536-documents-final.png`, `ui-1536-documents-v3.png` |
| Dokumen 768 | Filter form penuh sebelum data | `ui-768-documents-v4.png`, `ui-768-documents-filter-open.png` |
| Detail dokumen | Aktivitas ±3.000 px | `ui-1536-document-detail-v2.png` |
| Audit pagination | Tanpa rentang/ukuran | `ui-1536-audit-final.png`, `ui-1536-audit-pagesize50.png` |
| Users/Employees | Filter menyatu di card tabel | `ui-1536-users-final.png`, `ui-1536-employees-final.png`, `ui-768-users.png` |
| Migrasi akun | Deskripsi dan callout panjang | `ui-1536-migration-final.png` |
| Mobile drawer & dialog | — | `ui-768-drawer.png`, `ui-390-dialog-edit.png` |

Evidence sebelum perbaikan tidak disimpan sebagai salinan terpisah karena sesi audit berjalan pada working tree yang sama; temuan sebelum diperiksa langsung pada render saat audit dan dicatat di `UI_UX_AUDIT_REPORT.md`. Screenshot yang diarsipkan adalah hasil akhir yang dapat direproduksi.

## 5. Dampak dan kompatibilitas

- Kontrak API: tidak berubah. Halaman memakai parameter `page`, `size`, `keyword`, dan filter yang sudah ada.
- Authorization: tidak berubah. Visibilitas menu dan tile tetap memakai capability dari `/auth/me`; backend tetap otoritas.
- Data: tidak ada perubahan data, migration, atau seed.
- Route: tidak ada route yang dihapus atau diganti; seluruh 25 route tetap dapat diakses.
- Dependency: tidak ada dependency runtime baru; `axe-core` dan `@axe-core/playwright` ditambahkan sebagai devDependency untuk pipeline test.
- Bundle: build sukses; chunk terbesar `index-*.js` 365 kB (gzip 113 kB).

## 6. Known limitations dan residual risk

1. **E2E otomatis tidak dijalankan.** Environment tidak memiliki `KCIC_E2E_*` credentials; status BLOCKED, bukan PASS.
2. **Tidak ada chart time-series** karena belum ada endpoint agregasi; tile angka dipakai sebagai visualisasi fungsional (ADR-UI-01).
3. **Sort kolom belum tersedia** karena API belum mendukung parameter sort (ADR-UI-04).
4. **Master Data tanpa pagination** karena dataset konfigurasi kecil dan endpoint mengembalikan daftar penuh (ADR-UI-02).
5. **Bahasa label navigasi campuran** mengikuti sitemap PRD dan istilah domain; standardisasi bahasa adalah keputusan produk (ADR-UI-03).
6. Audit axe-core sudah menjadi bagian pipeline vitest dan suite Playwright, serta dijalankan live pada browser lokal (0 violation setelah perbaikan). Suite Playwright a11y tetap BLOCKED tanpa kredensial E2E; pembaca layar nyata belum diuji. Ini bukan klaim lulus penuh WCAG untuk seluruh kondisi penggunaan.

## 7. Item deferred

- Grafik dashboard (butuh endpoint agregasi + keputusan produk).
- Sort server-side (butuh kontrak API baru).
- Eksekusi suite a11y Playwright di CI/staging dengan kredensial E2E yang sah (kode suite sudah tersedia).
- Rename istilah navigasi ke satu bahasa (keputusan produk).

## 8. UI taste pass (22 November 2026)

Refinement lanjutan atas sistem yang sudah ada (bukan redesign): satu aksen maroon, satu keluarga grey, token dan radius tetap.

1. **Primitif tabel bersama** — `shared/components/Table.tsx` (`Table`, `THead`, `Th`, `Tr`, `Td`): wrapper `overflow-x-auto` + caption sr-only, header uppercase 11 px pada `surface-muted`, hover tint baris, prop `hover` untuk baris non-interaktif. Delapan tabel dikonversi: Users, Employees, Roles, Workspaces, Master Data, Organization, placement EmployeeDetail, Audit. Tidak ada `<table>` mentah tersisa di `features/`.
2. **Visibilitas aksi** — `ButtonLink` baru di `Button.tsx`; CTA dashboard dan empty-state not-found (Request/Distribution/Template detail) berhenti memakai `<Link>` ber-class tombol. Aksi baris (Kelola, Buka, Permission, Ubah) menjadi secondary compact + chevron; `LinkToUser` di Organization tidak lagi full-reload. Aksi netral lain (Tandai dibaca, aksi folder, jadikan versi aktif, unduh versi template) naik dari ghost ke secondary; aksi destruktif tetap ghost bertint danger.
3. **Bento dashboard** — baris empat tile statistik berbasis capability dengan total nyata dari query yang sudah ada (request ditugaskan, dokumen dapat diakses, distribusi diterima/dikirim, notifikasi belum dibaca); tile unread menjadi maroon solid saat > 0. Footer total pada card request dan blok unread di card notifikasi dihapus karena duplikat dengan tile.
4. **Copy dan penempatan** — deskripsi "N versi tercatat" dihapus; Riwayat versi mengecualikan versi aktif; copy kebijakan AccessPanel diringkas tanpa menghapus aturan deny-precedence dan isolasi sumber grant; helper password Users diringkas; badge akun Employees pindah ke kolom Status; avatar inisial pada Users; UUID audit dipotong dengan `title`; disclosure metadata audit menjadi tombol dengan chevron `group-open:rotate-90`; outcome audit berlabel Indonesia.
5. **Test dan dokumen** — komposisi page-level axe memakai primitif baru; `COMPONENT_STANDARD.md` v1.1 menambah §13 (Table dan aksi baris) serta aturan `ButtonLink`; bukti before/after di `docs/ux-evidence/taste-*.png`; addendum verifikasi §9.

Gate pass ini: typecheck PASS, lint 0 errors/8 warnings, test 24/24, build PASS, 10 scan axe live 0 violation (lihat `UI_UX_VERIFICATION_REPORT.md` §9).
