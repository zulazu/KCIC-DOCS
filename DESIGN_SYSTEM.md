# KCIC Docs — Design System

| Metadata | Nilai |
|---|---|
| Product | KCIC Docs |
| Document | UI/UX Design System Specification |
| Version | 1.1 |
| Status | Baseline visual dan interaction specification |
| Tanggal | 22 November 2026 |
| Referensi | `PRD.md`, `ARCHITECTURE.md`, `frontend/docs/UI_UX_AUDIT_REPORT.md`, `frontend/docs/UI_UX_DESIGN_SPECIFICATION.md`, `frontend/docs/COMPONENT_STANDARD.md`, `frontend/docs/UI_UX_IMPLEMENTATION_REPORT.md`, `frontend/docs/UI_UX_VERIFICATION_REPORT.md` |

---

## 1. Design Direction

KCIC Docs harus terasa sebagai **enterprise document workspace**: profesional, tepercaya, terstruktur, ringan digunakan, dan tidak terlihat seperti dashboard template generik.

### 1.1 Design principles

1. **Clarity before decoration:** owner, classification, version, status, dan permission harus mudah ditemukan.
2. **Task-oriented navigation:** pengguna dapat menjangkau Repository, Request, Distribution, Template, dan Administration melalui left sidebar.
3. **Consistent hierarchy:** satu primary action yang jelas per halaman; secondary action tidak bersaing secara visual.
4. **Trust and control:** tindakan berisiko membutuhkan konfirmasi dan feedback yang dapat dipahami.
5. **Quiet enterprise UI:** gunakan ruang putih, border silver, typography jelas, dan warna brand secara terukur.
6. **Accessible by default:** warna bukan satu-satunya penanda status; keyboard, focus, contrast, dan responsive layout wajib.
7. **Data-dense but readable:** tabel, filter, metadata, dan timeline dioptimalkan untuk pekerjaan administratif sehari-hari.

### 1.2 Brand expression

Warna utama adalah maroon `#8B1A1A`, didampingi putih dan silver. Hitam digunakan untuk teks/kontras serta elemen sekunder. Soft gold memberi aksen hangat dan formal, tetapi tidak boleh mendominasi atau mengganggu hierarchy.

---

## 2. Color System

### 2.1 Core brand palette

| Token | Hex | Penggunaan |
|---|---|---|
| `brand.primary` | `#8B1A1A` | Primary action, selected navigation accent, key brand marker |
| `brand.primary-hover` | `#751616` | Hover/pressed primary control |
| `brand.primary-pressed` | `#641212` | Active/pressed interaction |
| `brand.primary-subtle` | `#F8EEEE` | Background selected menu, subtle emphasis |
| `brand.primary-soft` | `#F1DADA` | Soft surface/border emphasis |
| `brand.white` | `#FFFFFF` | Main card/surface, reverse text on primary |
| `brand.silver-50` | `#F7F8FA` | Application background |
| `brand.silver-100` | `#EEF0F3` | Secondary surface |
| `brand.silver-200` | `#E0E4E8` | Default border/divider |
| `brand.silver-300` | `#C2C8D0` | Strong border, disabled surface |
| `brand.silver-500` | `#929AA4` | Icons/secondary visual only; not small text on white |
| `brand.black` | `#171717` | Primary text, dark secondary controls |
| `brand.black-soft` | `#2B2B2B` | Dark surface/secondary hover |
| `brand.gold` | `#D6B76E` | Restrained decorative/highlight accent |
| `brand.gold-soft` | `#F6F0E3` | Soft gold surface, optional callout background |
| `brand.gold-foreground` | `#70551B` | Text/icon on gold surface or accessible gold emphasis |

### 2.2 Primary scale

| Token | Hex |
|---|---|
| `primary-50` | `#F8EEEE` |
| `primary-100` | `#F1DADA` |
| `primary-200` | `#E2B5B5` |
| `primary-300` | `#CD8585` |
| `primary-400` | `#B55757` |
| `primary-500` | `#8B1A1A` |
| `primary-600` | `#751616` |
| `primary-700` | `#641212` |
| `primary-800` | `#541010` |
| `primary-900` | `#3D0B0B` |

### 2.3 Semantic colors

Semantic colors are for state communication, not brand replacement.

| Token | Hex | Usage |
|---|---|---|
| `status.success` | `#27734D` | Completed, active, approved, successful operation |
| `status.success-bg` | `#EAF5EF` | Success badge/alert surface |
| `status.warning` | `#8A5A12` | Pending, needs attention, approaching expiry |
| `status.warning-bg` | `#FBF3E3` | Warning surface |
| `status.danger` | `#B42318` | Rejected, failed, revoke/destructive action |
| `status.danger-hover` | `#9A1D14` | Hover destructive control |
| `status.danger-pressed` | `#801810` | Active/pressed destructive control |
| `status.danger-bg` | `#FDEDEC` | Error/destructive feedback surface |
| `status.info` | `#285B87` | Informational state or neutral notice |
| `status.info-bg` | `#EAF2F8` | Info surface |
| `status.neutral` | `#525B66` | Draft, inactive, unknown, or neutral state |
| `status.neutral-bg` | `#EEF0F3` | Neutral badge surface |

Aksi destructive menggunakan semantic danger, bukan primary maroon. Gold tidak digunakan untuk menunjukkan semua status warning/error; status semantic di atas lebih jelas.

### 2.4 Usage ratio and hierarchy

- Gunakan maroon pada primary CTA, active navigation marker, selected state, dan brand anchor.
- Jangan memberi background maroon pada semua card/header; biarkan surface tetap white/silver.
- Silver adalah warna struktur (border, divider, background), bukan warna teks kecil di atas putih.
- Black/near-black adalah teks utama dan kontrol sekunder.
- Soft gold digunakan maksimal sebagai aksen kecil: highlight, decorative line, selected premium detail, atau badge khusus yang memiliki makna.
- Jangan mencampur gold dengan maroon pada setiap komponen; gunakan hanya bila memperjelas hierarchy.
- Hindari gradient maroon-gold sebagai background default.

### 2.5 Contrast and accessibility

Perkiraan contrast ratio dari warna baseline:

- `#8B1A1A` terhadap putih: sekitar **9.29:1**; cocok untuk white text pada primary button.
- `#171717` terhadap putih: sekitar **17.93:1**.
- `#5F6872` terhadap putih: sekitar **5.66:1**; cocok untuk secondary text normal.
- `#BFC5CC` terhadap putih hanya sekitar **1.74:1**; jangan digunakan untuk teks atau icon penting di atas putih.
- `#D6B76E` terhadap putih hanya sekitar **1.93:1**; gunakan sebagai dekorasi/background, bukan teks putih.
- `#70551B` terhadap putih sekitar **6.99:1**; gunakan sebagai foreground di atas soft-gold/white bila diperlukan.

Target minimum: WCAG AA 4.5:1 untuk teks normal dan 3:1 untuk teks besar/komponen visual penting. Verifikasi semua pasangan warna aktual saat implementasi; jangan menganggap seluruh variasi token otomatis accessible.

---

## 3. Typography

### 3.1 Font family

Rekomendasi baseline:

- UI/body: `Inter`, dengan fallback `ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif`.
- Monospace: `JetBrains Mono`, fallback `ui-monospace, SFMono-Regular, Consolas, monospace` untuk code, ID teknis, dan data identifier bila relevan.

Jangan mengunduh/menanamkan font dari sumber tidak jelas. Pastikan license dan self-hosting policy disetujui bila font file ditambahkan ke repository.

### 3.2 Type scale

| Token | Size / line-height | Weight | Usage |
|---|---|---|---|
| `display` | 32 / 40 px | 700 | Login/empty hero atau headline khusus, jarang dipakai |
| `page-title` | 28 / 36 px | 700 | Page title utama desktop |
| `section-title` | 20 / 28 px | 600 | Section/card heading |
| `subsection-title` | 16 / 24 px | 600 | Subsection and table group |
| `body` | 14 / 22 px | 400 | Default text/data table |
| `body-strong` | 14 / 22 px | 600 | Emphasis |
| `body-large` | 16 / 24 px | 400 | Lead/helper paragraphs |
| `label` | 12 / 16 px | 600 | Form label, metadata label |
| `caption` | 12 / 16 px | 400 | Helper text, timestamp, subtle metadata |
| `micro` | 11 / 14 px | 500 | Dense table metadata only when readable |

Gunakan sentence case. Hindari seluruh heading menggunakan uppercase. Jangan menurunkan body text di bawah 14 px untuk informasi utama.

---

## 4. Layout and Spacing

### 4.1 Application shell

| Element | Desktop baseline | Notes |
|---|---:|---|
| Sidebar expanded width | 264 px | Fixed width, vertical scroll jika menu panjang |
| Sidebar compact width | 76 px | Icon-only; accessible tooltip/label tetap wajib |
| Top header height | 64 px | Breadcrumb/page context, notification, user menu |
| Main content horizontal padding | 28–32 px | Menyesuaikan viewport |
| Main content vertical padding | 24–32 px | Konsisten per halaman |
| Content maximum width | 1440 px | Untuk halaman data padat; form/detail dapat lebih sempit |
| Standard card radius | 12 px | Hindari radius terlalu besar |
| Control height | 40 px | Standard input/button; compact table control 32–36 px |

### 4.2 App shell layout

```text
┌──────────────────────────────────────────────────────────────────────────┐
│  LEFT SIDEBAR      │  HEADER: Breadcrumb / Notifications / User Menu     │
│                    ├─────────────────────────────────────────────────────┤
│  KCIC DOCS      │                                                     │
│  Dashboard         │  Page title                      Primary action     │
│  Repository        │  Short page description                              │
│  Requests          │                                                     │
│  Distribution      │  ┌───────────────────────────────────────────────┐  │
│  Templates         │  │ Main content: filter / table / cards / form   │  │
│                    │  │                                               │  │
│  ADMINISTRATION    │  └───────────────────────────────────────────────┘  │
│  Users / Roles     │                                                     │
│  Master Data       │                                                     │
│  Audit             │                                                     │
│                    │  FOOTER / pagination where relevant                 │
└──────────────────────────────────────────────────────────────────────────┘
```

### 4.3 Spacing scale

Gunakan kelipatan 4 px:

`4, 8, 12, 16, 20, 24, 32, 40, 48, 64`.

- 4–8 px: icon/text gap dan micro spacing.
- 12–16 px: input group, control group, card internal small spacing.
- 20–24 px: card padding dan form section.
- 32–40 px: section gap.
- 48–64 px: page-level separation.

### 4.4 Responsive breakpoints

| Breakpoint | Behavior |
|---|---|
| `< 1024 px` | Sidebar off-canvas drawer; single-column content; filter panel collapsed behind "Filter dan pencarian"; table may use horizontal scroll |
| `1024–1279 px` | Desktop shell dengan sidebar; default state collapsed (76 px) karena lebar layar `< 1280`; konten single-column; dashboard belum memakai rail kanan |
| `1280–1535 px` | Full sidebar (264 px) dan dashboard rail kanan 360 px |
| `>= 1536 px` | Full shell; content tetap dibatasi `max-w-[1440px]` |

Breakpoint implementasi: drawer `< lg (1024)`, sidebar desktop `>= lg`, collapse default saat viewport `< 1280`. Preferensi collapse disimpan pada `localStorage` (`kcic.sidebar.collapsed`) dan hanya memengaruhi desktop.

Responsive design tidak berarti seluruh data table harus dipaksa menjadi card. Pilih horizontal scrolling, essential columns, detail drawer, atau list-card berdasarkan task dan accessibility.

---

## 5. Navigation Design — Left Sidebar

### 5.1 Menu hierarchy

Menu utama mengikuti PRD. Menu administrasi tampil hanya untuk user yang memiliki capability terkait.

```text
KCIC DOCS
├── Dashboard
├── Repository
│   ├── All Documents
│   ├── My Documents
│   ├── Shared With Me
│   ├── Recent Documents
│   └── Workspaces
├── Requests
│   ├── My Requests
│   ├── Assigned Requests
│   └── Approval Inbox
├── Distribution
│   ├── Sent
│   └── Received
├── Templates
│   └── Template Catalog

ADMINISTRATION
├── Overview
├── User Management
├── Authorization
├── Organization
├── Master Data
├── Repository Governance
├── Template Administration
├── Request Administration
└── Audit Trail
```

Favorites, notifications, and profile boleh tampil sebagai utility entry pada sidebar/header sesuai hasil interaction design. Jangan menambah menu yang belum didukung route/permission.

### 5.2 Interaction

- Desktop default: expanded sidebar pada viewport `>= 1280 px`; collapsed pada viewport lebih sempit selama state belum diubah pengguna.
- Collapse state menyisakan icon dengan tooltip dan accessible label (`sr-only`).
- Mobile/tablet drawer (viewport `< 1024 px`) dibuka melalui menu button; drawer menggunakan native `<dialog>` sehingga fokus keyboard terperangkap dan kembali ke trigger saat ditutup.
- Active item: background `white/15`, teks putih, indicator 3 px gold di sisi kiri, radius 8 px. Hover memakai `white/10`.
- Group label memakai `white/65`; kontras terhadap maroon memenuhi WCAG AA.
- Current page title/breadcrumb tetap ditampilkan pada header aplikasi (section) dan breadcrumb in-page pada halaman detail.
- Sidebar tidak memuat daftar folder/document yang terlalu panjang; gunakan route/workspace selector pada content area.

### 5.3 Permission-aware navigation

Frontend menerima capability summary dari backend untuk menentukan navigasi yang terlihat. Jika permission berubah saat session aktif, frontend harus refresh/re-fetch identity/capability state pada titik yang wajar. API tetap menolak akses yang tidak sah.

---

## 6. Component Standards

### 6.1 Buttons

| Variant | Appearance | Usage |
|---|---|---|
| Primary | Maroon background, white label | Satu primary action utama per context |
| Secondary | White/silver border, dark label | Aksi alternatif |
| Tertiary/Ghost | Transparent, dark label; subtle hover | Aksi ringan pada toolbar |
| Danger | Semantic red background/outline | Delete/revoke/deactivate bila destructive |
| Gold accent | Gold surface + dark gold foreground | Sangat terbatas untuk aksen khusus, bukan default action |

Button wajib mempunyai default/hover/focus/active/disabled/loading state. Jangan menaruh banyak primary button pada satu toolbar.

### 6.2 Inputs and forms

- Label berada di atas input; required indicator jelas.
- Placeholder tidak menggantikan label.
- Error berada dekat field dan mempunyai teks spesifik.
- Input height default 40 px; textarea menyesuaikan konten.
- Search field menampilkan clear action bila relevan.
- Select/combobox harus mendukung keyboard dan long list search.
- Form panjang dikelompokkan berdasarkan domain; gunakan sticky action footer hanya bila benar-benar membantu.
- Saat submit, disable action yang sama untuk mencegah double submission.

### 6.3 Cards and surfaces

- Main surface putih, border silver `silver-200`, shadow sangat halus atau tanpa shadow.
- Gunakan card untuk grouping bermakna, bukan membungkus setiap control.
- Hindari nested card berlapis-lapis.
- Header card menyertakan title dan action hanya bila konteks perlu.

### 6.4 Tables

- Header table mudah dibedakan dari row.
- Row height 48–56 px untuk data utama.
- Alignment: text left, numeric/date konsisten, action pada kolom terakhir.
- Pagination server-side; tampilkan jumlah item bila tersedia.
- Filter/search memiliki clear/reset state.
- Row action tidak boleh mengungkap aksi yang user tidak punya permission.
- Empty state menjelaskan cara mendapatkan data atau menghapus filter.
- Loading menggunakan skeleton atau progress yang tidak menggeser layout secara ekstrem.
- Pada layar sempit, tampilkan kolom penting dan sediakan detail view untuk data sekunder.

### 6.5 Status badges

Gunakan semantic token dan label teks. Contoh:

| State | Badge |
|---|---|
| ACTIVE / PUBLISHED / APPROVED / FULFILLED | Success |
| PENDING / UNDER_REVIEW / IN_PROGRESS / EXPIRING | Warning |
| REJECTED / REVOKED / FAILED / LOCKED | Danger |
| DRAFT / INACTIVE / ARCHIVED / CLOSED | Neutral |
| SUBMITTED / INFORMATIONAL | Info |

Mapping di atas adalah baseline visual; status bisnis tetap ditampilkan sesuai nama/status yang benar. Jangan mengubah arti bisnis hanya karena warna badge.

### 6.6 Dialog and confirmation

- Confirmation modal hanya untuk aksi yang berisiko atau irreversible.
- Dialog menyebut resource yang terpengaruh dan konsekuensi aksi.
- Tombol cancel dan confirm harus jelas.
- Destructive confirm memakai danger styling.
- Untuk form kompleks, gunakan page route atau side panel yang memadai, bukan modal kecil.

### 6.7 Feedback

- Toast untuk konfirmasi singkat; bukan tempat menampung error validation panjang.
- Inline alert untuk kondisi yang perlu perhatian saat pengguna berada di halaman terkait.
- Error state menyertakan tindakan yang dapat dilakukan (retry, kembali, perbaiki filter) bila aman.
- Jangan menampilkan stack trace/SQL/internal identifier sensitif.

### 6.8 Page header

- Satu `PageHeader` per halaman: h1 26 px (mobile) / 28 px (≥ sm), satu baris deskripsi opsional maksimal 3 baris, lalu meta chips.
- Primary action berada di kanan dan sejajar dengan baseline judul; maksimal satu tombol primary.
- Jangan memakai deskripsi untuk mengulang nama menu atau menjelaskan fungsi dasar halaman. Deskripsi hanya untuk konteks yang tidak terlihat dari judul.
- Halaman detail menempatkan identifier (kode dokumen/request/distribution/template) sebagai chip mono di baris meta, bukan sebagai subtitle abu-abu.
- Aksi sekunder yang banyak (mis. detail dokumen) dibungkus `flex flex-wrap` agar turun baris rapi di layar sempit tanpa overflow.

### 6.9 Filter panel

- Filter dan pencarian dibungkus komponen `FilterPanel`.
- Viewport `< 1024 px`: filter tersembunyi di balik tombol "Filter dan pencarian" dengan `aria-expanded`, `aria-controls`, chevron berputar, dan badge jumlah filter aktif.
- Viewport `>= 1024 px`: panel selalu terbuka, tanpa tombol toggle.
- Tombol "Terapkan" berada pada baris aksi di akhir grid, rata kanan pada desktop; reset hanya muncul saat ada filter aktif.
- Jumlah filter aktif dihitung dari nilai yang benar-benar diterapkan, bukan dari input yang belum di-submit.

### 6.10 Pagination

- Semua daftar data memakai komponen `PaginationBar` dengan pagination server-side.
- Teks rentang: `Menampilkan <start>–<end> dari <total> <itemLabel>` dalam `aria-live="polite"`.
- Pilihan ukuran halaman: `10 / 25 / 50 / 100` baris (`aria-label="Baris per halaman"`).
- Navigasi halaman memakai tombol sebelumnya/berikutnya dengan accessible label; nomor halaman ditampilkan sebagai `x / y`.
- Mengubah ukuran halaman mereset halaman ke 1.
- `keepPreviousData` dipakai agar tabel tidak berkedip saat berpindah halaman.

---

## 7. Domain-Specific UI Patterns

### 7.1 Document list and detail

List/document detail harus menampilkan informasi sesuai permission:

- Title dan optional document code.
- Workspace/folder.
- Category dan document type.
- Classification dengan label/icon, bukan warna saja.
- Owner user/unit.
- Current version dan last updated.
- Document status.
- Action yang tersedia: view/preview/download/request access/update metadata/create version/share/archive, sesuai permission.

Jangan menampilkan internal storage key atau signed URL.

### 7.2 Document version history

- Tampilkan nomor/label versi, tanggal dibuat, uploader, change summary, dan status.
- Current version harus mudah dikenali.
- Versi lama dapat diakses sesuai authorization dan policy.
- Perubahan current version memerlukan confirmation bila berdampak pada pengguna lain.

### 7.3 Classification and permission

- Classification ditampilkan sebagai label yang konsisten.
- Permission panel menjelaskan penerima, jenis akses, sumber grant (manual/request/distribution/inherited), granted by, expiry, dan revoke action bila authorized.
- Jangan membuat ACL terlihat seperti kontrol sederhana yang mengabaikan inheritance/explicit deny.

### 7.4 Request timeline

Tampilkan timeline status, actor, waktu, approval/rejection reason, fulfillment result, dan komentar sesuai permission. Hindari menyatukan status request dan notification state.

### 7.5 Distribution

Detail distribution membedakan sent/received dan recipient status. Label READ harus menjelaskan bahwa penerima membuka detail distribution, bukan bukti membaca isi dokumen.

### 7.6 Template catalog

Template card/list menampilkan template code/name, owner, category, current published version, publication date, dan status. Draft/archived tidak muncul pada katalog umum kecuali user memiliki administration permission.

### 7.7 Dashboard

- Dashboard adalah bento fungsional, bukan kumpulan kartu dekoratif: setiap tile memakai data nyata dan capability pengguna.
- Tile yang tampil: Request ditugaskan, Dokumen terbaru, Distribusi terbaru (kolom kiri); Notifikasi, Akun & akses, Administrasi (rail kanan 360 px).
- Tile tanpa capability tidak dirender; tile dengan data kosong menampilkan empty state singkat, bukan angka palsu.
- Setiap tile memiliki loading skeleton, error + "Coba lagi", dan tautan menuju daftar lengkap.
- Layout desktop memakai dua kolom independen (`grid xl:grid-cols-[minmax(0,1fr)_360px]`) agar tidak muncul celah vertikal akibat tinggi kartu berbeda; di bawah `xl` seluruh tile bertumpuk satu kolom.
- Jangan menampilkan chart yang tidak memiliki data sumber; ringkasan angka (mis. jumlah belum dibaca) lebih jujur daripada grafik dekoratif.

---

## 8. Content and Microcopy

- Gunakan bahasa Indonesia yang jelas untuk label dan helper text; istilah teknis/domain yang telah disepakati (Repository, Request, Distribution, Template, Workspace) boleh dipertahankan.
- Gunakan kata kerja eksplisit: `Unggah dokumen`, `Buat request`, `Kirim distribusi`, `Cabut akses`, `Arsipkan dokumen`.
- Hindari label ambigu seperti `Submit`, `Process`, atau `Manage` tanpa konteks.
- Error harus menjelaskan apa yang salah dan langkah aman berikutnya.
- Jangan menyalahkan pengguna.
- Gunakan format tanggal konsisten dan tampilkan zona waktu bila waktu absolut penting.
- Jangan menyebut file telah aman dari malware karena antivirus ditunda.

---

## 9. Accessibility Requirements

- Semua interactive control dapat dioperasikan dengan keyboard.
- Focus indicator terlihat jelas dan tidak hanya mengandalkan color shift.
- Sidebar/drawer mempunyai semantic navigation landmark dan accessible names.
- Modal/drawer mengelola focus dengan benar.
- Form label terhubung secara programatik; error terasosiasi ke field.
- Status memakai teks/icon bersama warna.
- Contrast ratio memenuhi WCAG AA.
- Klik target nyaman; target 40 px untuk standard controls dan 44 px untuk touch-oriented controls bila layout memungkinkan.
- Reduced motion dihormati; animasi hanya untuk feedback/navigation kecil.
- Jangan memakai red/green saja untuk membedakan status.

---

## 10. Motion and Interaction

- Motion bersifat functional, tidak dekoratif berlebihan.
- Durasi umum 120–200 ms untuk hover/expand/fade sederhana.
- Drawer/modal memiliki enter/exit transition singkat dan reduced-motion fallback.
- Loading menampilkan progress/skeleton; jangan memutar animasi panjang tanpa feedback.
- Hindari parallax, heavy gradient, glassmorphism, animated background, dan auto-playing animation pada aplikasi operasional.

---

## 11. Design Token Implementation Guidance

Expose token sebagai CSS custom properties dan map ke Tailwind theme/config sesuai versi Tailwind yang digunakan. Contoh semantic CSS token:

```css
:root {
  --color-primary: #8B1A1A;
  --color-primary-hover: #751616;
  --color-primary-pressed: #641212;
  --color-primary-subtle: #F8EEEE;

  --color-background: #F7F8FA;
  --color-surface: #FFFFFF;
  --color-surface-muted: #EEF0F3;
  --color-border: #E0E4E8;
  --color-border-strong: #C2C8D0;

  --color-text: #171717;
  --color-text-secondary: #5F6872;
  --color-text-disabled: #929AA4;

  --color-gold: #D6B76E;
  --color-gold-soft: #F6F0E3;
  --color-gold-foreground: #70551B;

  --color-success: #27734D;
  --color-warning: #8A5A12;
  --color-danger: #B42318;
  --color-info: #285B87;
}
```

Jangan menuliskan nilai hex brand berulang-ulang pada setiap component. Komponen harus menggunakan semantic token, sehingga theme adjustment dilakukan di satu tempat.

---

## 12. Visual QA Checklist

- [ ] Maroon `#8B1A1A` terlihat sebagai warna utama tanpa membuat seluruh UI terlalu gelap.
- [ ] Putih dan silver mendominasi surface/layout.
- [ ] Hitam/near-black digunakan untuk teks utama dan secondary controls.
- [ ] Soft gold hanya menjadi aksen yang terukur.
- [ ] Left sidebar terlihat pada seluruh authenticated desktop route.
- [ ] Sidebar compact state tetap dapat digunakan dengan keyboard/screen reader.
- [ ] Mobile/tablet menggunakan drawer yang dapat dibuka/ditutup.
- [ ] Active menu state jelas dan konsisten.
- [ ] Setiap halaman mempunyai title, breadcrumb/context, dan primary action yang tepat.
- [ ] Table, form, dialog, toast, badge, dan empty/loading/error states konsisten.
- [ ] Tidak ada teks penting dengan contrast di bawah WCAG AA.
- [ ] Warna tidak menjadi satu-satunya pembeda status.
- [ ] Tidak ada placeholder yang menggantikan label.
- [ ] Tidak ada layout overflow horizontal yang tidak disengaja.
- [ ] Tidak ada decorative effect yang mengurangi keterbacaan atau performance.

---

## 13. Design Definition of Done

Design system siap digunakan jika token telah diimplementasikan, shared UI primitives konsisten, AppShell dengan left sidebar berfungsi, responsive navigation berjalan, accessibility baseline dipenuhi, dan seluruh fitur baru tidak memperkenalkan visual token/komponen berbeda tanpa alasan yang terdokumentasi.

---

## 14. Addendum 2026-11-23 — Rebuild UI (keputusan pemilik produk)

Keputusan pemilik produk terbaru mengizinkan penyimpangan dari dokumen ini selama
palet, left sidebar, dan seluruh fitur tetap ada. Yang berubah dan menjadi standar baru:

- **Tipografi:** display `EB Garamond` (heading halaman, angka statistik, brand login),
  body `Lato` 15 px/24 px. Token `--font-display` dan `--font-sans` di `globals.css`.
- **Bento:** kartu memakai `--radius-card: 16px` dan `--shadow-card` berlapis;
  tile statistik/tautan memakai kelas `.tile-hover` (lift 2 px + shadow saat hover).
  Dashboard dan halaman Organization memakai grid bento asimetris.
- **Kontrol:** radius kontrol `--radius-lg: 10px`; tombol primary memakai `--shadow-button`
  dan `active:translate-y-px`; baris tabel lebih padat (`py-2.5`).
- **Navigasi:** item sidebar dapat memiliki `children` (submenu Organization: Direktorat,
  Departemen, Biro/Division). Chevron adalah tombol terpisah dengan `aria-expanded`;
  submenu terbuka otomatis saat anak aktif. Breadcrumb memakai `navTrail()`.
- **Palet tidak berubah** (§2): primary `#8B1A1A`, putih/silver, hitam, soft gold `#D6B76E`.
  Biru tetap dilarang sebagai warna brand/navigasi.
- **Aksesibilitas tetap mengikat** (§9): focus-visible 3 px (gold di dalam sidebar/dialog),
  reduced-motion global, kontras AA, label teks untuk setiap status.

Dokumen generator `design-system/kcic-docs/MASTER.md` memuat catatan override:
palet navy/blue usulan generator ditolak; yang diadopsi hanya tipografi, pola bento,
kepadatan data, dan disiplin aksesibilitas.
