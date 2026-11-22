# KCIC Connect — Panduan Administrator

Untuk SYSTEM_ADMIN, DOCUMENT_ADMIN, dan Unit Manager yang mengelola akun, akses, repository, serta tata kelola dokumen pada pilot KCIC Connect.

## 1. Peran dan tanggung jawab

| Role | Fokus utama |
|---|---|
| SYSTEM_ADMIN | Akun, role/permission, unit organisasi, master data, file policy, audit |
| DOCUMENT_ADMIN | Pengelolaan dokumen, workspace/folder, ACL, request/distribusi, template |
| UNIT_MANAGER | Approval request dan akses untuk unit yang dikelola |
| AUDITOR | Membaca audit trail; tidak mengubah data |
| EMPLOYEE / CONTRIBUTOR | Katalog, request, unggah versi (Contributor) sesuai capability |

Prinsip: **backend selalu memeriksa ulang**. Menyembunyikan menu bukan kontrol keamanan.

## 2. Membuat akun dan menugaskan role

1. **User Management** → **Tambah pengguna**: isi username, nama tampilan, email opsional, password sementara (≥12 karakter).
2. Buka detail akun:
   - **Profil** – nama/email.
   - **Status akun** – nonaktifkan, reset password, buka kunci. Nonaktif mengakhiri session aktif.
   - **Keanggotaan unit** – tambahkan unit dengan tipe PRIMARY/SECONDARY; unit menentukan scope akses.
   - **Penugasan role** – pilih role dan scope (Global atau Unit organisasi) serta masa berlaku opsional.
3. Akun tanpa role tidak memperoleh capability apa pun. Akun baru wajib mengganti password saat login pertama.

Role dengan scope unit hanya berlaku pada resource milik unit tersebut; akses lintas unit tetap memerlukan ACL.

> **Administrasi employee-first (E1–E5):** Employee (NIP, nama, email, status kerja, placement Biro/Division) dibuat lebih dulu di **Employee**, lalu tepat satu akun User ditautkan. Untuk akun yang sudah tertaut, assignment unit legacy tidak dapat ditambah (409); kelola perpindahan lewat placement Employee. Akun bootstrap/layanan yang belum tertaut tetap dapat memakai alur akun lama selama migrasi.

## 3. Role dan permission

**Authorization** menampilkan katalog role dan permission. Role sistem tidak dapat diubah permission-nya secara langsung; buat role turunan bila perlu kombinasi berbeda. Periksa kolom scope dan capability sebelum menugaskan ke pengguna.

## 4. Unit organisasi

**Organization** mengelola hierarki unit dan anggota. Unit dipakai untuk:

- scope penugasan role,
- owner dokumen/workspace,
- routing request dan approval,
- penerima distribusi (unit diekspansi ke anggota aktif saat pengiriman).

Perubahan keanggotaan tidak mengubah penerima distribusi yang sudah dikirim.

### 4.1 Typed hierarchy dan placement

- Tipe unit: `COMPANY` (root tunggal) → `DIRECTORATE` → `DEPARTMENT` → `DIVISION` (Biro/Division).
- Employee hanya ditempatkan pada **DIVISION**; Directorate/Department diturunkan otomatis dan tidak diisi manual.
- Satu Employee hanya memiliki satu placement aktif; transfer menutup placement lama dan menyimpan riwayat.
- Satu Employee hanya boleh memiliki satu akun User.
- Unit lama tanpa tipe tidak otomatis diubah; pemetaan harus berasal dari data resmi.
- Anggota unit, `/auth/me`, dan distribusi ke unit memakai placement aktif; penerima distribusi yang sudah terkirim tidak berubah saat employee dipindahkan.

### 4.2 Status kepegawaian mengunci akses

- Mengubah status Employee dari `ACTIVE` ke `INACTIVE`/`TERMINATED` mengakhiri seluruh session akun tertaut dan mencatat audit `EMPLOYEE_ACCESS_SUSPENDED`.
- Login berikutnya ditolak (`ACCOUNT_DISABLED`, audit `LOGIN_DENIED` reason `EMPLOYMENT_INACTIVE`); role/ACL baru ke akun tersebut ditolak sampai status kembali `ACTIVE`.
- Distribusi ke unit melewati employee non-aktif; memilih akun non-aktif sebagai penerima eksplisit ditolak.
- Status employee dan status akun tetap dua kontrol terpisah: tetap nonaktifkan akun pada **User Management** saat karyawan keluar. Jalankan `tools/ops/run-employment-check.sh` secara berkala; E8-OPS-01 harus 0. Script keluar dengan kode `1` bila `E8-OPS-GATE` = `BLOCKED`, sehingga penjadwalan otomatis berhenti pada pelanggaran nyata.

### 4.3 Scope turunan (include descendants)

- Opsi "berlaku untuk sub-unit" **default mati**. Grant/role lama tetap exact dan tidak otomatis melebar.
- Aktifkan hanya bila memang dibutuhkan; setiap aktivasi dicatat audit dan harus ditinjau.
- DENY eksplisit pada unit mana pun tetap mengalahkan ALLOW turunan.
- Jangan memakai opsi ini untuk "memperbaiki" akses yang gagal; periksa placement dan grant terlebih dahulu.

> Catatan: cutover `users.employee_id NOT NULL` (V18) **belum** diterapkan sampai data HR terverifikasi lolos preflight E6. Selama itu, akun tanpa Employee tetap diizinkan dan tercatat di **Migrasi Akun**. Tidak ada exception akun untuk V18 (DEC-068): akun bootstrap sekalipun harus ditautkan ke Employee terverifikasi sebelum cutover.

## 5. Master data dan file policy

**Master Data** mengelola kategori, jenis dokumen, klasifikasi, tag, dan file policy:

- Data yang dinonaktifkan tidak muncul pada form baru tetapi tetap valid pada data lama.
- File policy menentukan ekstensi dan MIME yang diizinkan serta ukuran maksimum. Nilai awal `PILOT_DEFAULT` berstatus sementara dan **harus disetujui pemilik bisnis** sebelum go-live.
- Perubahan policy berlaku pada unggahan berikutnya, bukan file lama.

## 6. Workspace dan folder

1. **Workspaces** → buat workspace (nama, unit pemilik, deskripsi).
2. Di detail workspace, buat folder dan sub-folder. Aturan: parent harus aktif, nama unik antar-saudara, tanpa siklus, folder dengan anak aktif tidak dapat diarsipkan.
3. Status workspace: `ACTIVE↔INACTIVE`, keduanya dapat diarsipkan; workspace arsip bersifat read-only untuk folder.
4. **Kontrol akses** tersedia pada workspace/folder untuk memberi atau mencabut izin.

## 7. Dokumen

- **Tambah dokumen** (metadata) lalu **Unggah versi**: file divalidasi backend (ekstensi, MIME, signature, ukuran). Nama file asli disimpan sebagai metadata; nama objek penyimpanan dibuat acak.
- Versi baru menjadi `CURRENT`; versi sebelumnya `SUPERSEDED`. Versi lama tidak dihapus dan dapat dipromosikan kembali bila perlu.
- Tautan Office/eksternal hanya `https://`; sistem mencatat tautan dan tidak mengubah izin di provider.
- Perubahan owner (user→unit atau sebaliknya) wajib alasan dan tercatat pada riwayat kepemilikan.
- Arsip membuat konten read-only; restore mengembalikan dokumen ke `ACTIVE`.

### ACL dokumen

Pada detail dokumen → **Kontrol akses**:

- Prinsipal: user, unit, atau role; efek `ALLOW`/`DENY`; permission `document.view/preview/download`.
- **DENY eksplisit mengalahkan** semua izin warisan, termasuk milik administrator global.
- Grant dapat diberi masa berlaku; revoke menyimpan riwayat.
- Mencabut satu grant tidak mencabut grant independen dari request/distribusi/manual.

## 8. Request dan approval

- Approver menerima notifikasi dan melihat task di **Requests → Tugas saya**.
- Alur: `SUBMITTED → UNDER_REVIEW → APPROVED/REJECTED → IN_PROGRESS → FULFILLED → CLOSED` (atau `CANCELLED`).
- Penolakan wajib alasan. Transisi ganda ditolak server.
- Fulfillment:
  - **Akses diberikan** – membuat grant `REQUEST` (view/preview/download) untuk requester.
  - **Dokumen ditautkan** – menautkan dokumen tervalidasi dan menyimpan snapshot versi.
  - **Tautan eksternal** – hanya HTTPS, dicatat tanpa klaim perubahan izin provider.
  - **Unggah dokumen** – memakai pipeline versi dokumen (validasi file & storage privat).

## 9. Distribusi

**Distribusi baru**: judul, pesan, masa berlaku (masa depan), 1–20 dokumen dengan level akses, penerima user/unit.

- Pengirim hanya dapat mendelegasikan level yang ia sendiri miliki dan wajib punya `document.share`.
- Distribusi langsung terkirim (`SENT`); tidak ada draft.
- Sender/`distribution.revoke` dapat mencabut seluruh distribusi atau per penerima; revoke hanya menyentuh grant milik distribusi tersebut.
- Expiry dinilai saat data dibaca; tidak ada job terjadwal.

## 10. Template

- **Template baru** menautkan satu dokumen yang dapat Anda lihat; kode template unik (disimpan uppercase).
- **Publish** menunjuk versi `CURRENT` dokumen. Publikasi ulang ke versi CURRENT yang lebih baru diperbolehkan; versi yang sama = no-op; template/dokumen arsip = 409.
- Katalog umum hanya menampilkan `PUBLISHED` yang dokumennya dapat dilihat pengguna. Draft/arsip hanya terlihat oleh pemegang `template.create/publish/archive`.
- **Arsipkan** menyembunyikan dari katalog umum dan mempertahankan riwayat; belum ada unarchive pada pilot.
- Penggunaan template (VIEW/COPY/DOWNLOAD) dihitung pada versi terpublikasi dan tidak pernah memberi akses.

## 11. Audit trail

**Audit Trail** (capability `audit.view`, role AUDITOR/SYSTEM_ADMIN):

- Filter berdasarkan aktor (username/nama), aksi, tipe resource, outcome, dan rentang tanggal.
- Tabel menampilkan waktu, aktor, aksi + outcome, resource, dan metadata aman (id, status, nomor versi).
- Audit tidak dapat diubah/dihapus dari aplikasi. Gunakan untuk investigasi akses, revoke, distribusi, dan perubahan administrasi.
- Periksa berkala `DENIED`/`FAILURE` untuk aktivitas mencurigakan.

## 12. Rutinitas operasional

| Frekuensi | Tindakan |
|---|---|
| Harian | Cek `/actuator/health`, log error backend, lonceng notifikasi, dan event `DENIED` mencurigakan |
| Mingguan | Tinjau akun tidak aktif, grant kedaluwarsa, dan request terbuka yang menua |
| Bulanan | Tinjau role/scope, file policy, template usang, dan kebutuhan dokumen baru |
| Sebelum rilis | Jalankan quality gates, backup DB + bucket, lakukan restore drill (lihat `DEPLOYMENT.md`) |
| Saat insiden | Nonaktifkan akun bermasalah (session berakhir), cabut grant terkait, periksa audit, rotasi kredensial bila perlu |

## 13. Batasan pilot yang harus dipahami

- Tidak ada antivirus; unggahan hanya divalidasi struktur. Jangan menyatakan file "bebas malware".
- Tidak ada SSO/MFA; autentikasi lokal dengan lockout.
- Notifikasi hanya in-app.
- Revoke/expiry berlaku pada API call berikutnya; file yang sudah terunduh tidak dapat ditarik.
- Pencarian hanya metadata, bukan isi dokumen.
- Tautan Office tidak mengubah izin di provider eksternal.
- Employee master, NIP, typed hierarchy, descendant ACL, dan pemisahan assignment legacy sudah aktif (E1–E7). Cutover `employee_id NOT NULL` (V18) belum diterapkan sampai data HR terverifikasi.
- Tidak ada data NIP/placement sintetis pada environment bersama; import HR harus terverifikasi dan melalui preflight.
