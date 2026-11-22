# E6 — HR Data Verification

Tujuan: memastikan dataset HR yang akan menghubungkan akun ke Employee dan placement Biro/Division **terverifikasi** sebelum import dan V18. Tidak ada NIP, email, unit, atau placement sintetis yang boleh masuk ke environment bersama.

## 1. Data yang diminta (satu baris per karyawan)

| Kolom | Aturan |
|---|---|
| `nip` | NIP resmi, unik (case-insensitive), tanpa spasi/spasi tersembunyi. |
| `full_name` | Nama sesuai sistem HR, bukan nama tampilan akun. |
| `email` | Email perusahaan yang unik; menjadi email Employee. |
| `employment_status` | `ACTIVE`, `INACTIVE`, atau `TERMINATED` persis. |
| `division_unit_code` | Kode unit `DIVISION` (Biro/Division) yang **sudah ada dan ACTIVE** di KCIC Connect. |

Header CSV harus **persis** ini dan dalam urutan ini (BOM/CRLF ditoleransi):

```text
nip,full_name,email,employment_status,division_unit_code
```

Template: `tools/preflight/e6_hr_template.csv`. File contoh masih berisi placeholder dan **harus gagal** validasi. File yang hanya berisi header juga **harus gagal** (E6-HR-00).

## 2. Sumber dan rekonsiliasi

1. Ekstrak dari sistem HR resmi oleh HR data owner; simpan nama file, tanggal ekstrak, dan query/report sumber.
2. Rekonsiliasi jumlah baris dengan laporan HR (headcount aktif/non-aktif); selisih harus dijelaskan.
3. Rekonsiliasi akun: setiap akun di KCIC Connect harus punya satu NIP; daftar akun unlinked harus kosong. Tidak ada exception akun untuk V18 (DEC-068). Jalankan worklist read-only dan simpan outputnya untuk HR:
   ```bash
   docker exec -i kcic-postgres psql -X -U kcic -d <db> \
     < tools/preflight/e6_unlinked_worklist.sql \
     | tee docs/organization-identity/E6_UNLINKED_WORKLIST_<env>_<date>.txt
   ```
   Worklist memuat `user_id`, username, display name, email akun, legacy unit, Employee yang sudah ada, kandidat kecocokan otomatis, dan kolom kosong untuk diisi HR.
4. Empat mata: HR data owner dan engineering memeriksa sampel minimal 10 baris + semua baris yang berubah status.
5. Jangan memakai `organization.user_organization_assignments` sebagai pengganti data placement HR (DEC-064).

## 3. Keputusan linking per akun

Username dan email akun **bukan** identity key. Email hanya dipakai sebagai kandidat; NIP resmi tetap wajib. Hasil dev 2026-11-22 (`E6_UNLINKED_WORKLIST_DEV_2026-11-22.txt`):

| Akun | Email akun | Legacy unit | Employee kandidat | Kesimpulan |
|---|---|---|---|---|
| `admin` | `admin@kcic.local` | — | none | bootstrap admin; wajib ditautkan ke Employee terverifikasi sebelum go-live (DEC-066/068) |
| `auditor1` | kosong | — | none | perlu NIP resmi dari HR |
| `employee1` | `employee1@kcic.local` | `KCIC-HO` PRIMARY (legacy, untyped) | none | perlu NIP resmi dari HR |
| `employee2` | kosong | — | none | perlu NIP resmi dari HR |
| `employee3` | kosong | `KCIC-HO` SECONDARY (legacy, untyped) | none | perlu NIP resmi dari HR |

Fakta pendukung:

- `identity.employees` hanya berisi satu baris (`UAT-NIP-001`, sudah tertaut ke `uat.biro`); tidak ada Employee lain untuk dicocokkan.
- Query kecocokan otomatis (email akun = email Employee atau username = NIP) mengembalikan **0 baris**.
- Tiga akun tidak memiliki email; email yang ada memakai domain lokal `@kcic.local`, bukan email perusahaan.
- `employee-link` tidak mencocokkan email/NIP; endpoint hanya memvalidasi kedua sisi belum tertaut. Karena itu linking selalu butuh Employee yang sudah dibuat dari data HR.

Keputusan yang diizinkan:

| Kondisi dari HR | Tindakan |
|---|---|
| Akun terbukti milik karyawan | Minta NIP, nama resmi, email perusahaan, status, dan Biro/Division; ikuti §4. |
| Akun hanya demo/UAT, bukan karyawan | **Jangan** buat Employee. Akun tetap hanya di dev/UAT dan tidak boleh ada di database production-like; V18 tidak dapat diterapkan selama akun itu ada. Deaktivasi tidak cukup (DEC-067). |
| Bootstrap admin | Perlakukan seperti karyawan: tautkan ke Employee terverifikasi; tidak ada exception (DEC-068). |

## 4. Prosedur konkret setelah HR mengonfirmasi

Prasyarat: unit Biro/Division tujuan sudah ada, `ACTIVE`, dan bertipe `DIVISION`; target adalah database production-like (bukan dev yang masih memiliki fixture `UAT-CO`).

1. Isi satu baris CSV per akun yang dikonfirmasi (bukan menyalin username/email akun), lalu validasi:
   ```bash
   tools/preflight/run-hr-validation.sh /path/to/hr_extract.csv
   ```
   Lanjut hanya jika `E6-HR-GATE` PASS.
2. Buat Employee (UI **Employees → Tambah**, atau API). Employee dan placement awal dibuat dalam satu transaksi:
   ```http
   POST /api/v1/admin/employees
   Content-Type: application/json
   X-XSRF-TOKEN: <token>

   {
     "nip": "<NIP resmi>",
     "fullName": "<nama resmi HR>",
     "email": "<email perusahaan>",
     "employmentStatus": "ACTIVE",
     "divisionUnitId": "<uuid DIVISION aktif>"
   }
   ```
   Permission: `employee.manage`. Respons `201` memuat `employee.id`.
3. Tautkan akun yang sudah ada ke Employee tersebut (jangan membuat akun kedua):
   ```http
   POST /api/v1/admin/users/{userId}/employee-link
   Content-Type: application/json
   X-XSRF-TOKEN: <token>

   { "employeeId": "<employee id>" }
   ```
   Permission: `user.manage` + `employee.manage`. Relasi 1:1; `409 CONFLICT` bila salah satu sisi sudah tertaut. Email akun tidak perlu sama dengan email Employee dan tidak diubah oleh linking. Akun `ACTIVE` ditolak (`400`, field `employeeId`) bila Employee bukan `ACTIVE`; nonaktifkan akun terlebih dahulu, lalu tautkan. Pembuatan akun baru hanya diizinkan untuk Employee `ACTIVE`.
4. Verifikasi per akun:
   ```sql
   SELECT u.username, e.nip
   FROM identity.users u
   LEFT JOIN identity.employees e ON e.id = u.employee_id
   WHERE u.username IN ('auditor1','employee1','employee2','employee3','admin');
   ```
5. Ulangi untuk setiap akun yang dikonfirmasi, lalu jalankan `tools/preflight/run-e6-preflight.sh`. Jangan memakai UPDATE SQL massal; jalur API mencatat audit linking.

## 5. Validasi otomatis

```bash
tools/preflight/run-hr-validation.sh /path/to/hr_extract.csv
```

Pemeriksaan (read-only terhadap tabel domain; CSV hanya masuk TEMP table):

| Kode | Blocker | Isi |
|---|---|---|
| E6-HR-00 | ya | file tidak berisi baris data (header saja) |
| E6-HR-08 | ya | `nip`/`division_unit_code` mengandung spasi di awal/akhir |
| E6-HR-01 | ya | field kosong / masih placeholder `<...>` |
| E6-HR-02 | ya | `employment_status` tidak valid |
| E6-HR-03 | ya | format email tidak valid |
| E6-HR-04 | ya | NIP/email duplikat di dalam file |
| E6-HR-05 | ya | kode divisi tidak ada / tidak ACTIVE / bukan DIVISION |
| E6-HR-06 | tidak | NIP sudah ada dengan nama/email berbeda (review sebelum upsert) |
| E6-HR-07 | ya | email sudah dipakai Employee lain |
| E6-HR-GATE | — | PASS hanya jika semua BLOCKER = 0 |

Exit code `2` berarti file/header tidak valid (ditolak sebelum menyentuh database); exit code `1` berarti gate FAIL. Hanya exit `0` yang boleh dilanjutkan. Simpan output: `docs/organization-identity/E6_HR_VALIDATION_<env>_<date>.txt`.

## 6. Setelah validasi

1. Import hanya setelah E6-HR-GATE PASS dan sign-off; import berjalan dalam satu transaksi, tidak menghapus data, dan tidak mengubah grant/legacy assignment.
2. Setelah import, jalankan preflight E6 dan `tools/ops/run-employment-check.sh`.
3. Cocokkan ulang jumlah akun tertaut, employee, dan placement aktif dengan laporan HR.
4. Jika ada perbedaan, berhenti dan perbaiki data sumber; jangan menambal dengan NIP/placement karangan.

## 7. Sign-off

| Item | Nilai |
|---|---|
| Sumber ekstrak (sistem/report) | |
| Tanggal & jam ekstrak | |
| Jumlah baris HR / hasil rekonsiliasi | |
| Worklist unlinked ditinjau & diputuskan | |
| E6-HR-GATE | PASS |
| Disetujui HR data owner (nama/tanggal) | |
| Disetujui engineering (nama/tanggal) | |
| Evidence file | |
