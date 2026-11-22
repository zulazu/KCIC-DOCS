# E6 — Unit Mapping Proposal (KCIC-HO / KCIC-OPS)

Status: **recommendation approved by the product owner (2026-11-22) for application on the production-like cutover database; not applied to dev.** Dev keeps the `UAT-CO` fixture and is not the cutover target (DEC-067). Pemetaan tipe unit adalah keputusan pemilik struktur organisasi; dokumen ini menetapkan rekomendasi, bukti, guard, dan cara penerapannya.

## 1. Inventory saat ini (dev, 2026-11-22)

| Unit | Nama | Tipe | Parent | Status | Catatan |
|---|---|---|---|---|---|
| KCIC-HO | Head Office | `NULL` | — | ACTIVE | deskripsi "Kantor pusat" |
| KCIC-OPS | Operations | `NULL` | — | ACTIVE | operasional |
| UAT-CO | UAT Perusahaan | COMPANY | — | ACTIVE | fixture acceptance E5, bukan data HR |
| UAT-DIR | UAT Direktorat | DIRECTORATE | UAT-CO | ACTIVE | fixture |
| UAT-DEPT | UAT Departemen | DEPARTMENT | UAT-DIR | ACTIVE | fixture |
| UAT-BIRO | UAT Biro/Division | DIVISION | UAT-DEPT | ACTIVE | fixture; dipakai placement `uat.biro` |

## 2. Rekomendasi

| Unit | Tipe yang direkomendasikan | Parent | Alasan |
|---|---|---|---|
| KCIC-HO | `COMPANY` | — (root) | Satu-satunya kandidat entitas puncak legacy; "Kantor pusat" merepresentasikan entitas perusahaan. |
| KCIC-OPS | `DIRECTORATE` | KCIC-HO | Unit operasional berada satu level di bawah entitas perusahaan. |

Konsekuensi yang harus diputuskan bersama HR/org owner:

1. **Belum ada DEPARTMENT/DIVISION legacy.** Karyawan hanya boleh ditempatkan pada `DIVISION`; sampai HR memberikan daftar Biro/Division resmi, belum ada unit yang sah untuk placement. Jangan membuat unit hanya agar gate PASS.
2. Jika KCIC-HO ternyata bukan entitas perusahaan (mis. sebuah direktorat), root COMPANY harus berasal dari data resmi dan rekomendasi ini batal.
3. Tipe unit tidak mengubah keanggotaan legacy maupun grant; perubahan hanya metadata klasifikasi dan dapat dibalik.
4. **Semua unit harus bertipe sebelum V18**, termasuk unit `INACTIVE`. V18 memakai `ALTER COLUMN unit_type SET NOT NULL` untuk seluruh tabel, sehingga E6-05 menghitung unit tanpa tipe pada semua status. Unit non-aktif yang tidak dipakai tetap harus dipetakan tipenya (bukan dihapus) atau diputuskan eksplisit oleh org owner; jangan membuat unit baru hanya agar gate PASS.

## 3. Mengapa belum diterapkan di dev

Dev masih memiliki **dua kandidat COMPANY aktif** jika KCIC-HO dipetakan sekarang: `UAT-CO` (fixture) + `KCIC-HO`. Aplikasi hanya mengizinkan satu COMPANY aktif, dan preflight E6-07 menolaknya.

- Fixture UAT tidak boleh dinonaktifkan/dihapus hanya untuk meloloskan gate: placement karyawan `uat.biro` bergantung pada `UAT-BIRO` yang aktif.
- Karena itu dev adalah environment UAT, bukan target cutover. Bukti guard: menjalankan `e6_apply_unit_mapping.sql` di dev berhenti dengan
  `E6 mapping aborted: 1 other ACTIVE COMPANY unit(s) exist; resolve the root first` (tanpa perubahan data).
- Penerapan mapping dilakukan pada database production-like (restore produksi) yang tidak berisi fixture UAT.

## 4. Prosedur penerapan (setelah sign-off)

1. Jalankan inventory read-only:
   ```bash
   docker exec -i kcic-postgres psql -X -U kcic -d <db> < tools/preflight/e6_unit_inventory.sql
   ```
2. Isi tabel sign-off di bawah. Tanpa dua persetujuan, jangan lanjut.
3. Terapkan mapping (satu transaksi, guard otomatis):
   ```bash
   docker exec -i kcic-postgres psql -X -v ON_ERROR_STOP=1 -U kcic -d <db> \
     < tools/preflight/e6_apply_unit_mapping.sql
   ```
4. Verifikasi: preflight E6-05 (semua unit, termasuk non-aktif), E6-06, E6-07 PASS; `KCIC-OPS.parent_id = KCIC-HO`.
5. Simpan output sebagai evidence `docs/organization-identity/E6_UNIT_MAPPING_<env>_<date>.txt`.

Mapping ini sudah dijalankan end-to-end pada database throwaway oleh `tools/preflight/run-e6-rehearsal.sh` (KCIC-HO/KCIC-OPS → preflight PASS → V18); evidence: `docs/organization-identity/E6_REHEARSAL_2026-11-22_r3.txt`.

## 5. Sign-off

| Peran | Nama | Keputusan | Tanggal | Bukti |
|---|---|---|---|---|
| Product owner | — | Menyetujui rekomendasi: `KCIC-HO → COMPANY`, `KCIC-OPS → DIRECTORATE` di bawah KCIC-HO | 2026-11-22 | arahan "saya setuju dan ikut rekomendasi anda"; `docs/PROGRESS.md` |
| Pemilik struktur organisasi | | Konfirmasi bagan org resmi (terutama status KCIC-HO sebagai entitas perusahaan) | | bagan org resmi |
| HR data owner | | | | daftar Biro/Division |
| Engineering (E6) | | | | preflight output |

Persetujuan product owner mengesahkan pemetaan dua unit legacy. Daftar Biro/Division resmi tetap wajib sebelum ada placement karyawan (konsekuensi #1); tanpa itu E6-02 tetap tidak dapat lulus pada target cutover.

## 6. Reversal

Hanya berlaku **sebelum V18** (setelah V18 kolom `unit_type` bersifat `NOT NULL`). Jika pemetaan keliru dan belum ada data turunan yang bergantung:

```sql
BEGIN;
UPDATE organization.organization_units SET unit_type = NULL
 WHERE lower(unit_code) IN ('kcic-ho', 'kcic-ops');
UPDATE organization.organization_units SET parent_id = NULL
 WHERE lower(unit_code) = 'kcic-ops';
COMMIT;
```

Jangan membalik dengan menghapus unit, placement, atau assignment.
