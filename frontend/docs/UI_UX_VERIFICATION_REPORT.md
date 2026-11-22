# KCIC Connect — UI/UX Verification Report

| Metadata | Nilai |
|---|---|
| Produk | KCIC Connect |
| Dokumen | Verification Report |
| Versi | 1.2 |
| Tanggal | 22 November 2026 |
| Lingkungan | Windows, Node 24, Vite dev server `localhost:5173`, Spring Boot `localhost:8080`, PostgreSQL container |
| Data | Data lokal yang ada (admin, 6 akun, 1 dokumen, 198 audit event, 0 template, 0 request milik sendiri) |

---

## 1. Verdict

| Area | Hasil |
|---|---|
| Type check | **PASS** |
| Lint | **PASS** — 0 error, 8 warning (batasan library) |
| Unit test | **PASS** — 24/24 (4 file, termasuk 11 test axe-core) |
| Production build | **PASS** |
| Backend test | **Tidak dijalankan ulang**; tidak ada perubahan backend. Terakhir terverifikasi 84/84 pada E8 (22 Nov 2026) |
| E2E otomatis | **BLOCKED** — kredensial environment tidak tersedia (termasuk suite a11y Playwright) |
| Verifikasi fungsional UI | **PASS (manual browser)** |
| Verifikasi responsif | **PASS (render 5 viewport)** |
| Verifikasi aksesibilitas | **PASS** — axe-core otomatis (vitest + scan browser live) dan pemeriksaan manual; 0 violation setelah perbaikan (lihat §5.3) |

Tidak ada klaim "design-approved" terhadap Figma karena repository tidak memiliki baseline visual resmi. Verdict adalah kesesuaian terhadap `DESIGN_SYSTEM.md` v1.1 dan hasil render aktual.

## 2. Gate yang dijalankan

Semua command dijalankan dari `frontend/` pada 22 November 2026 setelah perubahan terakhir.

| Command | Hasil | Catatan |
|---|---|---|
| `npm run typecheck` | PASS | `tsc -p tsconfig.app.json` dan `tsconfig.node.json` tanpa error |
| `npm run lint` | PASS | 0 error; 8 warning: `react-refresh/only-export-components` (Badge, PaginationBar, Toast, Dialog) dan `react-hook-form/incompatible-library` (form pages). Keduanya batasan library/pola ekspor, bukan defect |
| `npm run test -- --run` | PASS | 4 file, 24 test: navigation (7), API client (4), Badge (2), accessibility/axe-core (11) |
| `npm run build` | PASS | Vite build sukses; `index-*.js` 365.28 kB (gzip 113.40 kB) |
| `mvn -f backend/pom.xml test` | NOT RUN | Tidak ada perubahan backend pada fase ini; hasil E8 84 test, 0 failure/error |

## 3. Verifikasi fungsional (browser)

| # | Skenario | Langkah | Hasil | Bukti |
|---|---|---|---|---|
| F-01 | Sidebar collapse | Klik tombol Ciutkan pada 1536 | Lebar 264 px → 76 px | render |
| F-02 | Persistensi collapse | Reload halaman | Tetap 76 px; `kcic.sidebar.collapsed = "true"` | render |
| F-03 | Drawer mobile | Buka menu pada 768 | Drawer tampil, fokus di dalam `<dialog>`, dapat ditutup | `ui-768-drawer.png` |
| F-04 | Filter collapse tablet | Buka Dokumen pada 768 | Filter terlipat menjadi tombol "Filter dan pencarian"; dapat dibuka | `ui-768-documents-v4.png`, `ui-768-documents-filter-open.png` |
| F-05 | Pagination pindah halaman | Audit → Berikutnya | `Menampilkan 11–20 dari 196 event`, indikator `2 / …` | render |
| F-06 | Ukuran halaman | Audit → 50 | `Menampilkan 1–50 dari 196 event`, `1 / 4` | `ui-1536-audit-pagesize50.png` |
| F-07 | Reset halaman saat filter | Terapkan filter setelah pindah halaman | Kembali ke halaman 0 (perilaku kode + render) | render |
| F-08 | Dialog pada 390 | Buka dialog edit user | Dialog muat dalam lebar 390 tanpa overflow | `ui-390-dialog-edit.png` |
| F-09 | Skip link | Tab pertama dari body | Fokus ke "Lewati ke konten utama" di kiri atas | `ui-1536-skip-link-focus.png` |
| F-10 | Detail dokumen | Buka dokumen SOP-OPS-001 | Aktivitas 8 item + tombol "Tampilkan semua (38)" | `ui-1536-document-detail-v2.png` |
| F-11 | Empty state distribusi | Tab Diterima tanpa data | Empty state jelas, tidak ada tabel kosong | `ui-1536-distributions.png` |
| F-12 | Filter Users/Employees | Buka 1536 dan 768 | Filter card terpisah dari tabel; di 768 terlipat | `ui-1536-users-final.png`, `ui-1536-employees-final.png`, `ui-768-users.png` |

## 4. Matriks viewport

| Halaman | 1536 | 1280 | 1024 | 768 | 390 |
|---|---|---|---|---|---|
| Dashboard | PASS | PASS | PASS | PASS (stack) | PASS |
| Dokumen | PASS | — | — | PASS | PASS |
| Detail dokumen | PASS | — | — | — | PASS |
| Users | PASS | — | — | PASS | — |
| Employees | PASS | — | — | — | — |
| Requests | PASS | — | — | — | — |
| Distribusi | PASS | — | — | — | — |
| Templates | PASS | — | — | — | — |
| Notifikasi | PASS | — | — | — | — |
| Audit | PASS | — | — | — | — |
| Workspaces | PASS | — | — | — | — |
| Organization | PASS | — | — | — | — |
| Authorization | PASS | — | — | — | — |
| Master Data | PASS | — | — | — | — |
| Migrasi akun | PASS | — | — | — | — |
| Ubah password | PASS | — | — | — | — |

Keterangan:
- PASS berarti halaman dirender dengan data nyata dan tidak ada horizontal overflow (`document.documentElement.scrollWidth` sama dengan lebar viewport pada halaman yang diukur).
- "—" berarti tidak ada screenshot tersimpan pada viewport tersebut. Halaman tersebut memakai shell dan primitif yang sama dengan halaman yang diuji pada viewport itu; ini bukan klaim render per halaman.
- Dashboard, Dokumen, Detail dokumen, Users, Drawer, Dialog, dan Skip link adalah halaman yang diuji lintas viewport karena mewakili seluruh pola layout.

## 5. Pemeriksaan aksesibilitas manual (WCAG 2.2 AA)

| Kriteria | Pemeriksaan | Hasil |
|---|---|---|
| 2.4.1 Bypass blocks | Skip link sebagai fokus pertama | PASS (`ui-1536-skip-link-focus.png`) |
| 2.4.7 Focus visible | Global `:focus-visible` 2 px primary; input memakai ring | PASS (inspeksi CSS + keyboard) |
| 2.1.1 Keyboard | NavLink, filter toggle, pagination, dialog dapat dioperasikan keyboard | PASS (Tab/Enter/Escape) |
| 4.1.2 Name, role, value | Tombol ikon memakai `aria-label`/`sr-only`; toggle filter `aria-expanded`/`aria-controls` | PASS (inspeksi kode) |
| 4.1.3 Status messages | Rentang pagination `aria-live="polite"`; toast diumumkan | PASS (inspeksi kode) |
| 1.3.1 Info and relationships | Satu `h1` per halaman; `th scope="col"`; `nav aria-label` | PASS (inspeksi + render) |
| 1.4.3 Contrast | Pasangan token dihitung dengan rumus kontras WCAG | PASS (lihat §5.1) |
| 1.4.11 Non-text contrast | Border input `line-strong` dan focus ring terhadap permukaan | PASS (visual) |
| 2.5.8 Target size | Kontrol utama 32–40 px; tombol compact 32 px | PASS |
| 2.3.3 Animation | `prefers-reduced-motion` mematikan transisi/animasi | PASS (inspeksi CSS) |
| 3.3.1 Error identification | Error field di bawah input, `role="alert"`, `aria-invalid` | PASS (inspeksi komponen) |
| 3.3.2 Labels | Semua input memakai `label htmlFor` melalui `InputField` | PASS (inspeksi + render) |

### 5.1 Rasio kontras pasangan token

Dihitung dengan rumus relative luminance WCAG 2.x:

| Pasangan | Rasio | Ambang | Hasil |
|---|---|---|---|
| Putih pada primary `#8B1A1A` | 9.29:1 | 4.5:1 | PASS |
| `white/80` pada primary | 6.40:1 | 4.5:1 | PASS |
| `white/65` (label grup) pada primary | 4.69:1 | 4.5:1 | PASS (margin tipis; hanya untuk label 11 px semibold) |
| `white/75` (subjudul sidebar/login/ubah password) pada primary | 5.81:1 | 4.5:1 | PASS |
| `gold #D6B76E` pada `primary-pressed #641212` (brand mark) | 6.63:1 | 4.5:1 | PASS |
| `ink-secondary #5F6872` pada putih | 5.66:1 | 4.5:1 | PASS |
| `gold-foreground #70551B` pada `gold-soft #F6F0E3` | 6.16:1 | 4.5:1 | PASS |
| `danger #B42318` pada `danger-bg #FDEDEC` | 5.79:1 | 4.5:1 | PASS |

Batas: rasio dihitung dari nilai token, bukan sampling piksel render; opacity dan anti-aliasing dapat menurunkan kontras efektif sedikit. `white/65` adalah pasangan paling tipis dan disarankan dinaikkan bila label grup diperbesar.

### 5.2 Yang tidak diverifikasi

- Pembaca layar nyata (NVDA/JAWS/VoiceOver) tidak diuji.
- Zoom 200% dan reflow 320 px tidak diuji khusus; layout 390 px sudah diuji.
- Suite a11y Playwright (`e2e/a11y.spec.ts`) belum dapat dijalankan tanpa kredensial E2E; scan browser live di bawah menjadi bukti pengganti pada lingkungan lokal.

### 5.3 Audit otomatis axe-core

Pipeline yang ditambahkan (22 November 2026):

| Lapisan | Berkas | Cakupan | Status |
|---|---|---|---|
| Vitest (jsdom) | `src/test/axe.ts`, `src/test/accessibility.test.tsx` | 11 test: Button, Badge/StatusBadge, PageHeader, Card/DetailRow, FilterPanel, PaginationBar, Dialog/ConfirmDialog, Field (input/select/textarea), Feedback (alert/loading/empty/error/forbidden), Toast, komposisi page-level (skip link, landmark, tabel, footer) | PASS — bagian dari `npm run test` |
| Playwright | `e2e/a11y.spec.ts` (`@axe-core/playwright`) | Login, 15 route terautentikasi, logout, dan 2 route pada 390 px; termasuk color-contrast di browser nyata | BLOCKED — kredensial E2E tidak tersedia |
| Scan browser live | axe-core 4.14.1 disuntikkan ke halaman dev server | 16 route terautentikasi + `/login` pada 1536 px; `/login`, `/`, `/documents`, detail dokumen pada 390 px | PASS — 0 violation setelah perbaikan |

Aturan yang dipakai: tag `wcag2a`, `wcag2aa`, `wcag21a`, `wcag21aa`, `wcag22aa`. Pada jsdom, `color-contrast` dinonaktifkan (tanpa layout) dan diverifikasi di browser; aturan landmark page-level hanya aktif untuk test komposisi halaman. Polyfill `showModal`/`close` `<dialog>` ditambahkan di `src/test/setup.ts` karena jsdom belum mengimplementasikannya.

Temuan scan awal dan perbaikannya:

| # | Rule axe | Lokasi | Penyebab | Perbaikan | Rasio sesudah |
|---|---|---|---|---|---|
| A-01 | `color-contrast` | Brand mark sidebar (`Sidebar.tsx`) | Gold pada `bg-white/10` di atas maroon = 3.88:1 | Latar diganti `bg-primary-pressed` | 6.63:1 |
| A-02 | `color-contrast` | Subjudul `text-white/60` di Sidebar, LoginPage, ChangePasswordPage | 4.19:1 pada primary | Diganti `text-white/75` | 5.81:1 |
| A-03 | `color-contrast` | SHA-256 versi dokumen (`DocumentDetailPage.tsx`, 4 node) | `text-ink-disabled` 2.84:1 pada putih | Diganti `text-ink-secondary` | 5.66:1 |
| A-04 | `listitem` | Baris versi aktif di detail dokumen | `<li>` tanpa induk `<ul>` | Dibungkus `<ul className="divide-y divide-line">` | — |

Verifikasi ulang setelah perbaikan: 20 scan (17 route @1536 + 3 route @390 + login @390) menghasilkan **0 violation**. Sisa pemakaian `text-ink-disabled` diperiksa dan hanya berlaku untuk ikon, placeholder, kontrol disabled, atau elemen `aria-hidden` — bukan teks informatif yang wajib kontras.

## 6. E2E otomatis — BLOCKED

- Suite Playwright membutuhkan `KCIC_E2E_ADMIN_USERNAME`, `KCIC_E2E_ADMIN_PASSWORD`, `KCIC_E2E_EMPLOYEE_USERNAME`, `KCIC_E2E_EMPLOYEE_PASSWORD`.
- Pemeriksaan environment tidak menemukan variabel tersebut.
- `README` menyatakan suite gagal cepat tanpa kredensial dan password tidak boleh disimpan di repository.
- Status: **BLOCKED**, bukan PASS. Tidak ada permintaan atau penggunaan kredensial di luar akun lokal pengujian manual.

## 7. Regresi yang diperiksa

| Risiko | Pemeriksaan | Hasil |
|---|---|---|
| Route hilang | Semua 25 route di `router.tsx` dirender atau diperiksa | Tidak ada route berubah |
| Menu hilang untuk role | Filter capability tetap sama | Tidak ada perubahan logika |
| API berubah | Grep pemanggilan API; hanya UI | Tidak ada perubahan kontrak |
| Double submit | Tombol loading menonaktifkan aksi | Komponen `Button` menangani |
| Overflow horizontal | `scrollWidth` diukur pada halaman uji | Sama dengan viewport |
| Data karangan | Semua angka berasal dari query | Tidak ada mock baru |

## 8. Sisa pekerjaan verifikasi

1. Jalankan E2E (termasuk `e2e/a11y.spec.ts`) dengan kredensial environment yang sah di CI/staging.
2. Uji dengan pembaca layar sebelum UAT formal.
3. Uji zoom 200% dan reflow 320 px.

## 9. Addendum — UI taste pass (22 November 2026)

Pass refinement visual (copy, penempatan, bento, tabel, visibilitas aksi) setelah baseline v1.1. Tidak ada perubahan API, schema, authorization, atau data.

### 9.1 Gate

| Command | Hasil |
|---|---|
| `npm run typecheck` | PASS |
| `npm run lint` | PASS — 0 errors, 8 warnings (warning library yang sama, tidak bertambah) |
| `npm run test -- --run` | PASS — 24/24 (4 file; komposisi page-level axe kini memakai `Table` + `ButtonLink`) |
| `npm run build` | PASS — `index-*.js` 272.68 kB (gzip 83.51 kB); chunk `Button-*.js` 107.45 kB (gzip 36.32 kB) |

### 9.2 Scan axe live setelah perubahan

10 scan (5 route × 2 viewport): `/`, `/admin/users`, `/admin/audit`, `/documents/{id}`, `/workspaces` pada 1536×864 dan 390×844, tag WCAG 2.0–2.2 A/AA. Hasil: **0 violation** pada seluruh scan. Tile bento maroon memakai pasangan kontras yang sudah terverifikasi (white/75 pada primary 5.81:1).

### 9.3 Bukti visual

- Sebelum: `docs/ux-evidence/taste-before-1536-*.png` (10) dan `taste-before-390-*.png` (3).
- Sesudah: `docs/ux-evidence/taste-after-1536-{dashboard,document-detail,users,audit,workspaces}.png` dan `taste-after-390-{dashboard,users,document-detail}.png`.
- Pemeriksaan manual: bento row 4 tile (2 kolom di 390 px), tabel scroll horizontal tanpa overflow halaman, aksi baris terlihat sebagai tombol, riwayat versi tidak menduplikasi versi aktif.

### 9.4 Batasan

Suite Playwright tetap BLOCKED tanpa kredensial `KCIC_E2E_*`. Verifikasi visual dilakukan pada browser lokal terautentikasi sebagai admin; peran lain tidak dirender ulang pada pass ini (logika capability tidak berubah).
