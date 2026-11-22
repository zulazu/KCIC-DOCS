# E6 — Cutover Runbook (V18)

Status: **belum dieksekusi** terhadap data nyata. Semua langkah di bawah dapat dijalankan sekarang kecuali langkah yang membutuhkan data HR resmi dan sign-off. Bukti rehearsal: `docs/organization-identity/E6_REHEARSAL_2026-11-22_r3.txt`.

## 0. Prinsip

- V18 (`users.employee_id NOT NULL`, `organization_units.unit_type NOT NULL`) adalah cutover, bukan deploy biasa; jangan digabung dengan import yang belum terverifikasi (MIGRATION_PLAN §2).
- Tidak ada NIP/unit/placement karangan (DEC-064). Tidak ada reset database destruktif.
- Gate harus PASS pada environment target, bukan hanya dev. Jika satu BLOCKER tersisa, berhenti.

## 1. Peran

| Peran | Tanggung jawab |
|---|---|
| HR data owner | ekstrak HR, rekonsiliasi headcount, sign-off E6-HR-GATE |
| Org owner | sign-off mapping unit (`E6_UNIT_MAPPING.md`) |
| DBA / platform | backup, restore rehearsal, apply V18 di jendela maintenance |
| Engineering | preflight, rehearsal, smoke test pasca-cutover |

## 2. Urutan langkah

### Langkah 1 — Backup pasangan DB + storage

Ikuti `docs/DEPLOYMENT.md` §6. Tanpa backup yang direstorasi-uji, jangan lanjut.

### Langkah 2 — Mapping unit

1. Inventory: `docker exec -i kcic-postgres psql -X -U kcic -d <db> < tools/preflight/e6_unit_inventory.sql`
2. Rekomendasi & sign-off: `docs/organization-identity/E6_UNIT_MAPPING.md` (KCIC-HO → COMPANY, KCIC-OPS → DIRECTORATE).
3. Terapkan pada **production-like DB tanpa fixture UAT**: `tools/preflight/e6_apply_unit_mapping.sql`.
4. Preflight E6-05/06/07 harus PASS. Mapping tidak mengubah grant, keanggotaan, atau placement.

### Langkah 3 — Verifikasi data HR

1. Minta ekstrak sesuai `docs/organization-identity/E6_HR_VERIFICATION.md`; satu baris per karyawan.
2. Validasi: `tools/preflight/run-hr-validation.sh /path/to/hr_extract.csv` → **E6-HR-GATE PASS**. Header harus persis `nip,full_name,email,employment_status,division_unit_code`; E6-HR-00 menolak file tanpa baris data dan E6-HR-08 menolak NIP/kode divisi dengan spasi di tepi.
3. Rekonsiliasi jumlah baris dengan laporan HR dan daftar akun unlinked: `tools/preflight/e6_unlinked_worklist.sql` (read-only). Setiap akun harus diputuskan: karyawan (butuh NIP resmi) atau bukan (tidak dibuatkan Employee).
4. Sign-off HR + engineering di dokumen yang sama, termasuk keputusan worklist.

### Langkah 4 — Import dan linking akun

1. Import employee/placement dalam satu transaksi yang disetujui (tidak menghapus; tidak mengubah grant). Import tanpa sign-off dilarang.
2. Link **setiap** akun ke Employee (Employee detail → akun, atau `POST /admin/users/{id}/employee-link`). Bootstrap admin juga wajib ditautkan; tidak ada exception akun untuk V18 (DEC-066, DEC-068). Akun `ACTIVE` tidak dapat ditautkan ke Employee non-`ACTIVE`; nonaktifkan akun terlebih dahulu. Pembuatan akun hanya untuk Employee `ACTIVE`.
3. Target: `users WHERE employee_id IS NULL` = **0** untuk akun manusia.
4. Bila admin masih akan dibuat lewat bootstrap pada target kosong, set `KCIC_BOOTSTRAP_ADMIN_EMPLOYEE_NIP` ke NIP Employee `ACTIVE` yang sudah diimpor **sebelum** start backend. NIP tidak dikenal atau non-`ACTIVE` menggagalkan startup (tidak ada akun setengah jadi); tanpa setting ini bootstrap pre-V18 tetap berjalan tetapi dicatat sebagai warning yang harus diselesaikan sebelum V18.

### Langkah 5 — Preflight pada target

```bash
set -o pipefail
KCIC_DB_CONTAINER=<container> KCIC_DB_USER=<user> KCIC_DB_NAME=<db> tools/preflight/run-e6-preflight.sh \
  | tee docs/organization-identity/E6_PREFLIGHT_<env>_<date>.txt
```

Runner keluar `1` bila baris data `E6-GATE` = `BLOCKED`; jangan hanya mengandalkan exit `tee`.

Lulus hanya jika `E6-GATE` = PASS (E6-01…E6-09). E6-08 memblokir akun ACTIVE yang tertaut ke employee non-ACTIVE (nonaktifkan akun terkait, jangan mengubah status employee tanpa dasar HR); E6-09 memblokir NIP/email kosong, spasi di tepi, atau malformed. Review E6-INFO `include_descendants`; nilai bukan nol harus disetujui grant-by-grant. Review juga E6-INFO `active legacy unit assignments on linked accounts`: baris `DIFFERS_FROM_PLACEMENT` dapat memperlebar unit scope dan harus diputuskan per baris (dipertahankan dengan alasan atau diakhiri lewat UI); dilarang bulk delete.

### Langkah 6 — Rehearsal V18 pada database sekali pakai

```bash
tools/preflight/run-e6-rehearsal.sh | tee docs/organization-identity/E6_REHEARSAL_<date>.txt
```

Script membuat database throwaway, menerapkan V1–V17, memuat fixture berlabel `REH-*`, menjalankan preflight (harus PASS), menerapkan `v18_candidate.sql`, memverifikasi kedua kolom `NOT NULL`, lalu menghapus database. Ini membuktikan mekanisme, bukan data produksi.

### Langkah 7 — Terapkan V18

1. Maintenance window; hentikan penulisan.
2. Pindahkan `tools/preflight/v18_candidate.sql` menjadi `backend/src/main/resources/db/migration/V18__enforce_employee_identity.sql` **hanya pada rilis cutover**.
3. Deploy; Flyway menerapkan V18. Jika ada satu user unlinked/unit untyped, migrasi gagal dan aplikasi tidak start — ini disengaja.
4. Jalankan smoke test: login admin, `/auth/me` menampilkan unit placement, buka dokumen, kirim distribusi uji, cek audit.

### Langkah 8 — Pasca-cutover

```bash
set -o pipefail
tools/ops/run-employment-check.sh | tee docs/organization-identity/E8_OPERATIONAL_CHECK_<env>_<date>.txt
```

E8-OPS-01 harus 0. Jadwalkan pemeriksaan ini berkala. Verifikasi audit `EMPLOYEE_ACCESS_SUSPENDED`/`LOGIN_DENIED` bila ada perubahan status.

## 3. Rollback

| Situasi | Tindakan |
|---|---|
| Sebelum V18 | Hentikan; restore backup bila import salah. V14–V17 additive dan boleh tertinggal. |
| Setelah V18, aplikasi gagal | Deploy balik versi sebelumnya; jika perlu `ALTER COLUMN ... DROP NOT NULL` pada kedua kolom (data tetap). |
| Grant/akses memburuk | Jangan reset. Identifikasi grant, bandingkan audit, perbaiki dengan grant/revoke terotorisasi. |

## 4. Kondisi berhenti (STOP)

- Ada NIP/email/unit yang tidak dapat diverifikasi ke sumber resmi.
- Diminta mengecualikan akun dari gate; V18 tidak mendukung exception akun (DEC-068).
- Mapping unit belum disign-off atau menimbulkan dua COMPANY aktif.
- E6 preflight masih FAIL pada target.
- Rehearsal gagal.
- Backup tidak tersedia atau restore drill belum pernah lulus.
