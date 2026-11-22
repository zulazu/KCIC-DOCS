# KCIC Connect — Component Standard

| Metadata | Nilai |
|---|---|
| Produk | KCIC Connect |
| Dokumen | Shared Component Standard |
| Versi | 1.1 |
| Tanggal | 22 November 2026 |
| Lokasi | `frontend/src/shared/components/` |
| Status | Berlaku; komponen baru wajib mengikuti pola ini |

---

## 1. Prinsip

1. Gunakan komponen bersama sebelum membuat markup baru. Duplikasi visual di feature hanya boleh untuk hal yang benar-benar domain-spesifik.
2. Komponen bersama tidak mengetahui domain (tidak ada import dari `features/`).
3. Semua styling memakai token Tailwind dari `@theme` di `frontend/src/shared/styles/globals.css`; tidak ada hex atau shadow ad-hoc di feature.
4. Setiap kontrol interaktif memiliki accessible name, state, dan focus style.
5. Komponen yang dipakai lintas fitur wajib punya test atau diverifikasi lewat halaman yang mengonsumsinya.

## 2. Inventaris

| Komponen | File | Tujuan |
|---|---|---|
| `Button`, `ButtonLink`, `Spinner` | `Button.tsx` | Aksi dengan variant dan loading; `ButtonLink` untuk navigasi berbentuk tombol |
| `Badge`, `StatusBadge` | `Badge.tsx` | Status dan label |
| `Card`, `CardHeader`, `PageHeader`, `DetailRow` | `Surface.tsx` | Kerangka halaman dan panel |
| `FilterPanel` | `FilterPanel.tsx` | Wadah filter responsif |
| `PaginationBar`, `PAGE_SIZE_OPTIONS` | `PaginationBar.tsx` | Pagination server-side |
| `Dialog`, `ConfirmDialog` | `Dialog.tsx` | Modal dan konfirmasi |
| `Alert`, `LoadingBlock`, `EmptyState`, `ErrorState`, `ForbiddenState` | `Feedback.tsx` | State dan umpan balik |
| `InputField`, `SelectField`, `TextareaField` | `Field.tsx` | Form fields dengan label, helper, dan error |
| `ToastProvider`, `useToast` | `Toast.tsx` | Notifikasi transient |
| `Icon` | `Icon.tsx` | Ikon inline (tanpa dependency eksternal) |
| `Table`, `THead`, `Th`, `Tr`, `Td` | `Table.tsx` | Tabel data dengan header konsisten dan scroll horizontal |

## 3. Button

```tsx
<Button variant="primary" size="default" loading={saving} type="submit">
  Simpan
</Button>
```

| Prop | Nilai | Default |
|---|---|---|
| `variant` | `primary` \| `secondary` \| `ghost` \| `danger` | `primary` |
| `size` | `default` (h-10) \| `compact` (h-8) | `default` |
| `loading` | boolean, menonaktifkan tombol dan menampilkan spinner | `false` |
| `type` | default `button` | `button` |

Aturan:

- Satu `variant="primary"` per area pandang. Di header halaman, hanya primary action yang memakai primary.
- `danger` hanya untuk aksi destruktif yang sudah dikonfirmasi.
- `loading` wajib untuk aksi async; jangan hanya mengganti teks.
- Jangan membuat tombol dengan `<button>` mentah untuk aksi standar.
- Navigasi yang berbentuk tombol (CTA header, empty state, aksi baris) memakai `ButtonLink`; jangan menstyling `<Link>` mentah dengan class tombol.

## 4. PageHeader dan Card

```tsx
<PageHeader
  title="Dokumen"
  description="..."        // opsional, hanya bila menambah informasi
  meta={<><span className="font-mono ...">{documentCode}</span><StatusBadge status={status} /></>}
  action={<Button><Icon name="plus" size={16} />Upload dokumen</Button>}
/>
```

- `PageHeader` merender satu `h1` (26/28 px). Jangan menambahkan `h1` lain di halaman.
- `title` adalah nama objek/tugas; jangan mengulang label navigasi dalam `description`.
- `meta` untuk identifier (mono) dan status; bukan untuk paragraf.
- `Card` default `rounded-card border border-line bg-surface shadow-card`; `CardHeader` memisahkan judul/aksi dengan border bawah.
- `CardHeader.description` opsional; hapus bila hanya mengulang judul.

## 5. Badge dan StatusBadge

```tsx
<StatusBadge status={document.status} />
<Badge tone="gold">Internal</Badge>
```

- `StatusBadge` memetakan status domain ke tone dan label yang dapat dibaca; jangan menulis warna status manual.
- Untuk status yang belum terpetakan, sediakan `label` eksplisit agar tidak tampil sebagai kode mentah.
- Warna tidak boleh menjadi satu-satunya pembeda; badge selalu memuat teks.

## 6. FilterPanel

```tsx
const activeFilterCount = [keyword, status].filter(Boolean).length;

<FilterPanel activeCount={activeFilterCount}>
  <form onSubmit={apply} className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4">
    ...
    <div className="flex items-end justify-end gap-2 sm:col-span-2 xl:col-span-4">
      <Button type="submit"><Icon name="search" size={16} />Terapkan</Button>
      {hasFilters ? <Button variant="ghost" size="compact" onClick={reset}>Reset</Button> : null}
    </div>
  </form>
</FilterPanel>
```

- `activeCount` dihitung dari nilai yang sudah diterapkan (bukan state input yang belum disubmit).
- Panel selalu terbuka pada `lg+`; di bawah `lg` menjadi toggle dengan `aria-expanded`/`aria-controls` dan badge jumlah filter.
- Form wajib bisa disubmit dengan Enter.
- Tombol Terapkan berada di kanan (kecuali form satu baris sederhana); Reset hanya muncul bila ada filter aktif.
- Mengubah filter mereset `page` ke 0.
- Variasi yang diizinkan: halaman dengan **satu** kontrol pencarian (Workspaces, Organization, tab Master Data) boleh memakai toolbar inline di dalam card tabel. Halaman dengan dua filter atau lebih wajib memakai `FilterPanel` + card tabel terpisah (Dokumen, Requests, Distribusi, Template, Users, Employees, Audit).

## 7. PaginationBar

```tsx
<PaginationBar
  page={page}
  size={size}
  totalItems={data.totalItems}
  totalPages={data.totalPages}
  itemLabel="dokumen"
  onPageChange={setPage}
  onSizeChange={(next) => { setSize(next); setPage(0); }}
/>
```

- `itemLabel` berupa kata benda yang sesuai halaman ("dokumen", "event", "employee").
- Komponen menampilkan `Menampilkan <start>–<end> dari <total> <label>` dengan `aria-live="polite"`.
- Ukuran halaman: `10 | 25 | 50 | 100` dari `PAGE_SIZE_OPTIONS`, dengan `aria-label="Baris per halaman"`.
- Jangan membuat pagination manual; jangan memuat seluruh data lalu memotong di client untuk tabel besar.
- Gunakan `keepPreviousData` pada query agar perpindahan halaman tidak mengosongkan tabel.

## 8. Dialog dan ConfirmDialog

```tsx
<ConfirmDialog
  open={confirmOpen}
  title="Cabut akses?"
  description="Penerima kehilangan akses unduh segera."
  confirmLabel="Cabut akses"
  tone="danger"
  loading={revoking}
  onConfirm={revoke}
  onClose={close}
/>
```

- `Dialog` untuk form singkat; alur panjang memakai halaman.
- `ConfirmDialog` memakai `Button` bersama (secondary + primary/danger) dan menonaktifkan konfirmasi saat loading.
- Fokus masuk ke dialog, Escape menutup, backdrop dapat menutup (kecuali proses sedang berjalan).
- Judul menyebut aksi; deskripsi menyebut konsekuensi, bukan mengulang judul.

## 9. State komponen

```tsx
if (query.isPending) return <LoadingBlock label="Memuat dokumen…" />;
if (query.isError) return <ErrorState message={safeMessage} onRetry={refetch} />;
if (items.length === 0) return <EmptyState title="Belum ada dokumen" description="..." action={...} />;
```

| Komponen | Kapan |
|---|---|
| `LoadingBlock` | Pemuatan awal blok/tabel; skeleton + `role="status"` |
| `ErrorState` | Query gagal; pesan aman + Coba lagi |
| `EmptyState` | Data kosong; satu kalimat, aksi bila relevan |
| `ForbiddenState` | 403; tanpa membocorkan keberadaan resource |
| `Alert` | Pesan inline (info/warning/danger/success); `danger` otomatis `role="alert"` |
| `useToast().notify` | Hasil mutasi singkat ("Perubahan disimpan") |

Jangan memakai `alert()` browser, spinner tanpa teks, atau empty state kosong tanpa penjelasan.

## 10. Form fields

- Gunakan `InputField`/`SelectField`/`TextareaField` agar label, helper, error, dan `aria-invalid` konsisten.
- Error ditampilkan di bawah field dengan `role="alert"` dan dihubungkan ke input.
- Validasi client hanya untuk UX; validasi otoritatif tetap backend.
- Tombol submit wajib `loading` dan mencegah double submit.

## 11. Token dan styling

- Warna: `primary`, `primary-hover`, `primary-pressed`, `primary-subtle`, `primary-soft`, `background`, `surface`, `surface-muted`, `line`, `line-strong`, `ink`, `ink-secondary`, `ink-disabled`, `gold*`, `success*`, `warning*`, `danger*`, `info*`, `neutral*`.
- Radius: `rounded-card` untuk panel; `rounded-lg` untuk kontrol; `rounded-full` untuk avatar/pill.
- Shadow: hanya `shadow-card`.
- Spacing: kelipatan 4 px; gap card 16 px.
- Focus: `:focus-visible` global 2 px primary. Jangan menambahkan `outline-none` tanpa pengganti.
- Motion: transisi 150–200 ms; hormati `prefers-reduced-motion`.

## 12. Review checklist komponen

- [ ] Memakai komponen bersama, bukan markup duplikat.
- [ ] Accessible name, state, dan keyboard path ada.
- [ ] Loading/disabled/error state jelas.
- [ ] Tidak ada hex/shadow/radius di luar token.
- [ ] Responsif pada 390/768/1024/1536.
- [ ] Tidak ada teks dekoratif yang mengulang konteks.

## 13. Table dan aksi baris

```tsx
<Table caption="Daftar pengguna">
  <THead>
    <Th>Pengguna</Th>
    <Th className="hidden md:table-cell">Unit</Th>
    <Th align="right">Aksi</Th>
  </THead>
  <tbody>
    <Tr>
      <Td>…</Td>
      <Td className="hidden md:table-cell">…</Td>
      <Td>
        <div className="flex justify-end">
          <ButtonLink to={`/admin/users/${user.id}`} variant="secondary" size="compact">
            Kelola
            <Icon name="chevron-right" size={14} />
          </ButtonLink>
        </div>
      </Td>
    </Tr>
  </tbody>
</Table>
```

- `Table` sudah menyediakan wrapper `overflow-x-auto` dan `caption` sr-only; jangan menambah wrapper scroll sendiri di feature.
- `Th` uppercase 11 px di atas `bg-surface-muted`; kolom sekunder disembunyikan bertahap (`hidden md:table-cell`, `lg:table-cell`, `xl:table-cell`) agar tabel tetap terbaca di 390 px.
- `Tr` memiliki hover tint `primary-subtle`; pakai `hover={false}` untuk baris yang tidak interaktif.
- Tanggal dan angka memakai `tabular-nums`; identifier panjang (UUID) dipotong dengan `title` berisi nilai penuh.
- Aksi baris memakai `Button`/`ButtonLink` `variant="secondary" size="compact"` ditambah chevron, bukan text link polos. Aksi destruktif tetap ghost dengan tint danger (`text-danger hover:bg-danger-bg`).
- Disclosure (mis. metadata audit) memakai tombol bergaya secondary dengan chevron yang berputar via `group-open:rotate-90`, bukan teks polos.
