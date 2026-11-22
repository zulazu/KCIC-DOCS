# KCIC Connect — UI/UX Design Specification

| Metadata | Nilai |
|---|---|
| Produk | KCIC Connect |
| Dokumen | Design Specification (implementation contract) |
| Versi | 1.0 |
| Tanggal | 22 November 2026 |
| Sumber otoritatif | `DESIGN_SYSTEM.md` v1.1 (token dan visual), `PRD.md` (scope), `ARCHITECTURE.md` (batas sistem) |
| Status | Berlaku untuk pilot |

Dokumen ini menerjemahkan design system menjadi aturan implementasi yang dapat diperiksa. Bila terjadi konflik, `DESIGN_SYSTEM.md` menang untuk token visual dan PRD menang untuk perilaku produk.

---

## 1. Arah desain

**Quiet enterprise workspace.** Antarmuka harus terasa seperti alat kerja internal yang tenang: hierarki jelas, satu aksi utama per halaman, data padat tetapi terbaca, dan brand maroon dipakai secara terukur. Referensi rasa: sistem dokumen perusahaan (bukan SaaS marketing page, bukan dashboard template).

Prinsip yang mengikat implementasi:

1. Konten sebelum dekorasi. Tidak ada ilustrasi, gradien dekoratif, atau kartu kosong untuk mengisi ruang.
2. Satu `h1` per halaman. Judul menyebut objek kerja, bukan slogan.
3. Angka dan status berasal dari API nyata. Tidak ada data contoh.
4. Navigasi kiri adalah satu-satunya navigasi utama di desktop.
5. Biru tidak dipakai untuk brand, navigasi, atau aksi utama.

## 2. Layout shell

| Area | Aturan |
|---|---|
| Sidebar desktop | Fixed left, lebar 264 px (expanded) / 76 px (collapsed), maroon `#8B1A1A` |
| Sidebar otomatis collapse | Default collapsed saat viewport < 1280 px; preferensi disimpan di `kcic.sidebar.collapsed` |
| Drawer | `< 1024 px`; tombol menu di header; drawer lebar 280 px, `max-w-[85vw]` |
| Header | Tinggi 64 px; breadcrumb kiri, aksi global (notifikasi, identitas, keluar) kanan |
| Konten | `max-w-[1440px]`, padding `16px` (< lg) / `32px` (lg+) |
| Footer | Satu baris identitas produk; tidak ada tagline berulang |
| Dashboard | Dua kolom independen pada `xl`: konten utama fleksibel + rail 360 px |

Breakpoint yang dipakai (Tailwind default, tidak dikustom):

| Nama | Lebar | Konsekuensi layout |
|---|---|---|
| base | < 640 | Satu kolom; tabel menjadi daftar kartu; aksi full-width |
| sm | ≥ 640 | Grid 2 kolom untuk filter/form pendek |
| md | ≥ 768 | Tabel tetap; filter masih collapsible |
| lg | ≥ 1024 | Sidebar muncul; filter panel selalu terbuka |
| xl | ≥ 1280 | Dashboard rail kanan; sidebar default collapsed |

## 3. Typography

| Peran | Ukuran / line-height | Weight | Warna |
|---|---|---|---|
| Page title (`h1`) | 26/32, `sm`: 28/36 | Bold | `ink` |
| Section title (`h2`) | 16/24 | Semibold | `ink` |
| Body | 14/22 | Regular | `ink` |
| Secondary/meta | 12–13/18–20 | Regular / medium | `ink-secondary` |
| Micro label (group nav, table header) | 11/16, tracking 0.08em | Semibold | `ink-secondary` / `white/65` |
| Identifier (kode dokumen, NIP) | Mono 12/18 | Semibold | `ink-secondary` |

Aturan:

- Maksimal dua ukuran judul per layar (page title dan section title).
- Tidak ada heading dekoratif besar (display size) di halaman kerja.
- Angka metrik memakai `tabular-nums` agar tidak bergeser.
- Jangan memakai uppercase untuk kalimat; uppercase hanya untuk micro label.

## 4. Spacing dan radius

- Skala spacing: 4, 8, 12, 16, 20, 24, 32 px. Jarak antar-card 16 px; jarak section 20–24 px.
- Radius: card 12 px (`--radius-card`), kontrol 8 px, chip/badge 6 px atau pill.
- Shadow: hanya `--shadow-card` (`0 1px 2px rgb(23 23 23 / 0.04)`). Tidak ada shadow bertumpuk.
- Border: `1px` `line` untuk pemisah; `line-strong` untuk kontrol input.
- Ruang kosong tidak boleh dipakai untuk "bernapas" berlebihan: tinggi konten utama ditentukan data, bukan `min-h-screen` paksa.

## 5. Warna dan pemakaian

Token resmi ada di `DESIGN_SYSTEM.md` §2. Aturan pemakaian:

| Kebutuhan | Token |
|---|---|
| Aksi utama, active nav accent | `primary` (#8B1A1A) |
| Hover/pressed primary | `primary-hover` / `primary-pressed` |
| Latar halaman | `background` |
| Permukaan card/tabel | `surface` |
| Header tabel, chip pasif | `surface-muted` |
| Aksen brand halus (indikator, avatar admin) | `gold`, `gold-soft`, `gold-foreground` |
| Status | `success`, `warning`, `danger`, `info`, `neutral` + pasangan `*-bg` |
| Aksi destruktif | `danger` / `danger-hover` / `danger-pressed` |

- Teks pada maroon: putih atau `white/80`; label grup `white/65`; jangan memakai abu-abu gelap di atas maroon.
- Status selalu disertai teks (badge), bukan warna saja.
- Link dalam teks memakai `primary` dengan underline saat hover.

## 6. Navigasi

- Grup: Dashboard; Workspace (Dokumen, Workspaces); Requests; Distribution; Templates; Administration.
- Grup mengikuti sitemap PRD; item difilter berdasarkan capability dari `/auth/me`.
- Item aktif: latar `white/15`, teks putih, indikator gold 3 px di tepi kiri, `aria-current` dari `NavLink`.
- Item non-aktif: `white/80`, hover `white/10`.
- Saat collapsed: ikon saja, label tersedia via `title` dan `sr-only`; tooltip bukan satu-satunya akses nama.
- Drawer mobile memakai `<dialog>` native (focus trap dan Escape gratis), tombol tutup berlabel, klik backdrop menutup.
- Badge notifikasi belum dibaca tampil di header, bukan di dalam sidebar.

## 7. Pola halaman

### 7.1 Halaman daftar (Dokumen, Requests, Distribusi, Template, Users, Employees, Audit, Notifikasi)

Urutan wajib: `PageHeader` (judul + primary action) → tab (bila ada) → `FilterPanel` → tabel → `PaginationBar`.

- Filter memakai form dengan tombol **Terapkan**; Enter men-submit.
- `FilterPanel` menghitung jumlah filter aktif dari nilai yang sudah diterapkan.
- Reset hanya muncul bila ada filter aktif.
- Tabel: header 11–12 px semibold `ink-secondary` di atas `surface-muted`; baris 56–64 px; hover `background`; aksi teks di kolom kanan.
- Empty state: ikon netral, satu judul, satu kalimat; aksi bila relevan. Jangan memakai tiga paragraf.
- Pagination: teks `Menampilkan <start>–<end> dari <total> <itemLabel>` dengan `aria-live="polite"`, pemilih baris 10/25/50/100, tombol Sebelumnya/Berikutnya, indikator `halaman / total`.
- Perubahan ukuran halaman mereset `page` ke 0; perubahan filter juga mereset ke 0.
- Data halaman sebelumnya dipertahankan saat fetch (`keepPreviousData`), tanpa spinner penuh.

### 7.2 Halaman detail

Urutan: `PageHeader` (judul objek, meta identifier + status, aksi sekunder) → ringkasan → panel kerja.

- Aksi primer halaman detail adalah aksi domain yang paling sering dipakai (contoh: Ajukan akses, Bagikan, Publikasikan).
- Metadata memakai `DetailRow` dua kolom, label 120–140 px.
- Timeline/aktivitas dibatasi 8 item pertama dengan tombol ekspansi.
- Tindakan destruktif memakai `ConfirmDialog` dengan konsekuensi eksplisit.

### 7.3 Form

- Label selalu tampil; placeholder bukan pengganti label.
- Field wajib ditandai `*` dan dijelaskan di awal form bila lebih dari satu.
- Error di bawah field, ringkas, menyebut cara memperbaiki.
- Tombol submit memiliki loading state dan mencegah double submit.
- Setelah sukses: toast ringkas ("Perubahan disimpan") + update cache; navigasi hanya bila alur memang berpindah halaman.

### 7.4 Dashboard

- Tile harus menjawab pertanyaan kerja: apa yang menunggu saya, dokumen apa yang berubah, distribusi apa yang baru, notifikasi apa yang belum dibaca.
- Setiap tile punya loading (`skeleton`, `role="status"`), error (pesan + "Coba lagi"), empty (satu kalimat + tautan), dan success.
- Tile hanya dirender bila capability mengizinkan; jangan merender kartu kosong.
- Angka ringkas boleh menjadi visualisasi; grafik tren hanya boleh dibuat jika endpoint agregat tersedia.
- Tidak ada kartu dekoratif tanpa data.

### 7.5 Halaman diagnostik (Migrasi akun)

- Boleh memuat penjelasan batasan karena menyangkut integritas data, tetapi maksimal dua kalimat.
- Stat tile menampilkan angka nyata dengan tone sesuai risiko.

## 8. Konten dan copy

- Bahasa UI: Indonesia; istilah teknis/identifier tetap bahasa Inggris.
- Judul = nama objek atau tugas ("Dokumen", "Distribusi baru"), bukan kalimat pemasaran.
- Deskripsi halaman hanya bila menambah informasi yang tidak terlihat dari judul dan kontrol. Dilarang mengulang nama menu.
- Label aksi memakai kata kerja: "Upload dokumen", "Terapkan", "Tandai semua dibaca", "Simpan password".
- Toast sukses singkat dan spesifik; toast error menyebut apa yang gagal dan langkah berikutnya.
- Jangan memakai emoji, exclamation mark berlebihan, atau sapaan marketing.

## 9. State matrix

Setiap halaman wajib mendefinisikan: loading, empty, error, forbidden, not-found, success, conflict.

| State | Pola |
|---|---|
| Loading awal | Skeleton untuk tabel/tile; teks `Memuat …` untuk blok |
| Loading aksi | Tombol `loading`, nonaktif, spinner inline |
| Empty | Ikon + judul + satu kalimat; aksi bila pengguna bisa mengisi data |
| Error | Alert `danger` dengan pesan aman + tombol Coba lagi |
| Forbidden | Pesan akses ditolak + tautan kembali; tidak membocorkan keberadaan resource |
| Not found | Halaman 404 dengan tautan ke dashboard |
| Conflict | Pesan konflik (mis. versi berubah) + muat ulang data |

## 10. Accessibility (WCAG 2.2 AA)

- Kontras teks minimal 4.5:1; teks besar 3:1. Pasangan maroon/putih dan token status sudah dipilih untuk memenuhi ini.
- Fokus terlihat: outline 2 px `primary`, offset 2 px; jangan menghapus `:focus-visible`.
- Skip link "Lewati ke konten utama" adalah elemen fokus pertama.
- Semua kontrol punya accessible name (label, `aria-label`, atau teks).
- Dialog memakai `<dialog>`/role yang benar, menangkap fokus, dan dapat ditutup Escape.
- Target sentuh minimal 24×24 px (WCAG 2.2 §2.5.8); kontrol utama 32–40 px.
- Status tidak disampaikan hanya lewat warna.
- `prefers-reduced-motion` mematikan animasi dan transisi.
- Tabel memakai `th` semantik; pagination mengumumkan perubahan rentang.

## 11. Larangan (anti-pattern)

- Kartu identik berulang tanpa perbedaan kepentingan.
- Radius > 16 px, shadow besar, atau gradien dekoratif.
- Warna ungu/biru sebagai aksen brand.
- Judul display besar, eyebrow label di setiap section, layout serba center.
- Paragraf penjelas panjang di halaman kerja.
- Grafik atau angka karangan.
- Modal untuk alur panjang yang seharusnya halaman.
- Teks abu-abu di atas maroon tanpa kontras cukup.

## 12. Checklist implementasi

- [ ] Satu `h1`, hierarki dua level judul.
- [ ] Token warna/radius/shadow dari `globals.css`; tidak ada hex baru.
- [ ] `FilterPanel` + `PaginationBar` pada semua daftar yang didukung backend.
- [ ] Loading/empty/error/forbidden/conflict state tersedia.
- [ ] Fokus keyboard dan skip link berfungsi.
- [ ] Layout diperiksa pada 1536/1280/1024/768/390.
- [ ] Tidak ada data contoh atau teks dekoratif berulang.
