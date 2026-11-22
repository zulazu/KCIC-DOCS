# AGENTS.md — KCIC Connect Engineering Instructions

> Dokumen ini adalah kontrak kerja untuk AI coding agent, developer, reviewer, dan automation yang mengubah repository KCIC Connect. Baca seluruh dokumen ini sebelum melakukan perubahan kode.

| Metadata | Nilai |
|---|---|
| Produk | KCIC Connect |
| Dokumen | Agent Engineering Instructions |
| Versi | 1.0 |
| Status | Baseline implementasi |
| Tanggal | 22 November 2026 |
| Bahasa | Indonesia; nama kode, API, dan identifier menggunakan bahasa Inggris |

---

## 1. Misi Produk

Bangun KCIC Connect sebagai aplikasi web internal untuk menemukan, mengelola, meminta, dan mendistribusikan dokumen perusahaan secara terkontrol. Produk menggantikan ketergantungan pada percakapan informal sebagai tempat utama mencari dokumen dan meminta pemenuhan dokumen.

KCIC Connect adalah **pilot project dengan standar engineering produk yang serius**. Pilot membatasi fitur dan integrasi, bukan kualitas implementasi. Jangan membangun solusi sekali pakai, prototype disposable, atau mock flow yang berpura-pura menjadi fitur produksi.

### Hasil utama yang harus diwujudkan

1. Satu katalog dokumen dengan metadata, owner, klasifikasi, status, dan riwayat versi.
2. Penyimpanan file private serta pencatatan tautan Office/eksternal.
3. Hak akses berbasis role, scope, dan resource ACL yang ditegakkan backend.
4. Request dokumen/akses, proses review, fulfillment, dan histori.
5. Distribution kepada akun internal dengan kemampuan revoke dan expiry.
6. Katalog template resmi yang memakai mekanisme versioning repository.
7. Notifikasi in-app dan business audit trail.
8. Frontend dengan **left sidebar navigation** sebagai pola navigasi utama.

---

## 2. Wajib Dibaca Sebelum Coding

Baca dokumen sesuai urutan berikut. Jangan mulai implementasi domain sebelum memahami dokumen yang relevan.

1. `AGENTS.md` — aturan kerja agent dan batasan engineering.
2. `PRD.md` — kebutuhan produk, persona, scope, requirement, dan acceptance criteria.
3. `ARCHITECTURE.md` — batas sistem, module boundary, API, security, dan keputusan teknologi.
4. `SCHEMA.md` — model data, relasi, constraint, index, dan migration rules.
5. `DESIGN_SYSTEM.md` — token visual, layout, navigasi, komponen, serta accessibility.
6. `IMPLEMENTATION_PLAN.md` — urutan fase, dependency, deliverable, dan gate.

### Urutan otoritas keputusan

Jika terdapat ketidaksesuaian:

1. Keputusan eksplisit terbaru dari pemilik produk/user.
2. `PRD.md` untuk perilaku dan scope produk.
3. `ARCHITECTURE.md` untuk batas dan perilaku teknis.
4. `SCHEMA.md` untuk struktur data.
5. `DESIGN_SYSTEM.md` untuk visual dan interaksi.
6. `IMPLEMENTATION_PLAN.md` untuk urutan pengerjaan.

`AGENTS.md` mengatur cara agent bekerja dan tidak boleh digunakan untuk mengubah scope produk. Jika dua dokumen saling bertentangan dan tidak dapat diselesaikan dari urutan di atas, **jangan menebak**. Tulis conflict note, jelaskan dampak, dan minta keputusan yang paling kecil tetapi diperlukan.

---

## 3. Keputusan yang Sudah Dikunci

Keputusan berikut tidak boleh diubah secara sepihak oleh agent.

| Area | Keputusan |
|---|---|
| Product type | Internal web application |
| Delivery approach | Pilot dengan production-minded engineering standard |
| Frontend | React + TypeScript |
| UI styling | Tailwind CSS + reusable internal component system |
| Backend | Java + Spring Boot, REST API |
| Database | PostgreSQL sebagai source of truth metadata dan relasi |
| File storage | MinIO atau object storage kompatibel S3; bucket private |
| Architecture | Modular monolith; bukan microservices |
| Authentication | Akun lokal; dibuat dan dikelola administrator |
| Session | Server-side session dengan secure cookie dan CSRF protection |
| Authorization | RBAC + scope + resource ACL; backend adalah otoritas akses |
| Navigation | Left sidebar pada desktop; drawer pada viewport sempit |
| Primary color | `#8B1A1A` |
| Primary supporting colors | Putih dan silver |
| Secondary colors | Hitam dan soft gold/kuning lembut |
| Office integration | Simpan tautan dan metadata; tidak ada Microsoft Graph/permission sync pada pilot |

### Fitur/integrasi yang ditunda

Jangan menambahkan hal berikut sebagai prasyarat atau dependency pilot tanpa persetujuan eksplisit:

- SSO, OIDC, OAuth enterprise login, LDAP, Active Directory, HRIS, atau identity federation.
- Microsoft Graph, SharePoint API, OneDrive API, atau sinkronisasi permission provider Office.
- Antivirus/malware scanning.
- Centralized logging platform, APM, monitoring stack, atau observability platform tingkat lanjut.
- Redis, RabbitMQ, Kafka, message broker, atau distributed worker platform.
- Kubernetes, microservices, service mesh, atau distributed workflow engine.
- Email, WhatsApp, SMS, atau push notification.
- Public/guest sharing, public download URL permanen, atau anonymous access.
- OCR, semantic search, full-text indexing isi dokumen, dan real-time collaborative editing.

Penundaan di atas **tidak** membenarkan penghilangan kontrol dasar: validasi input/file, safe application logs, audit trail bisnis, error handling, backup/restore procedure, authorization, dan automated tests tetap wajib.

---

## 4. Prinsip Cara Bekerja Agent

### 4.1 Audit lebih dahulu, ubah setelah memahami

Sebelum menyentuh kode:

1. Periksa struktur repository, branch, working tree, dan perubahan yang belum di-commit.
2. Identifikasi framework, versi runtime, package manager, build tool, test runner, migration tool, serta cara menjalankan aplikasi yang benar-benar digunakan repository.
3. Baca README, konfigurasi, migration terakhir, dan modul yang terkait dengan task.
4. Periksa apakah fitur atau abstraksi serupa sudah tersedia agar tidak membuat duplikasi.
5. Buat ringkasan singkat: kondisi saat ini, file yang akan disentuh, dependency, risiko, dan gate verifikasi.

Jangan menganggap struktur folder atau command dari contoh dokumen sudah pasti sama dengan repository aktual. Adaptasikan ke konvensi yang sudah ada tanpa melanggar module boundary.

### 4.2 Implementasi kecil dan terverifikasi

- Kerjakan satu vertical slice atau satu kelompok perubahan yang koheren dalam satu waktu.
- Implementasikan behavior end-to-end seperlunya: UI/API, use case, authorization, persistence, audit, dan test.
- Hindari membuat banyak file placeholder yang tidak mempunyai behavior nyata.
- Jangan mengklaim fitur selesai hanya karena halaman atau endpoint berhasil dibuat.
- Setelah setiap kelompok perubahan, jalankan test relevan, lint/type-check, dan build sesuai konfigurasi repository.
- Jika test gagal, identifikasi apakah regression, test tidak valid, environment issue, atau defect lama. Jangan menghapus assertion hanya untuk membuat test hijau.

### 4.3 Lindungi perubahan pengguna

- Jangan menghapus atau menimpa perubahan kerja yang sudah ada.
- Jangan menjalankan `reset --hard`, `clean -fd`, bulk delete, mass rename, atau destructive migration tanpa instruksi eksplisit dan pemeriksaan dampak.
- Jangan menimpa file konfigurasi atau dokumentasi yang tidak berkaitan dengan task.
- Sebelum perubahan besar, periksa status Git dan buat perubahan sekecil mungkin.
- Jangan melakukan commit, push, release, atau deployment kecuali diminta.

### 4.4 Jangan mengarang bukti

- Jangan menyatakan test/build/lint berhasil tanpa benar-benar menjalankannya.
- Jangan mengarang endpoint, environment variable, database table, package, command, atau dependency.
- Jika environment tidak tersedia, sebutkan gate yang tidak dapat dijalankan dan alasan spesifiknya.
- Bedakan `implemented`, `tested`, `verified`, `blocked`, dan `deferred`.
- Jangan mengganti real implementation dengan mock untuk menghindari pekerjaan backend. Mock hanya untuk test atau story yang memang berstatus UI prototype.

---

## 5. Coding Standards — Umum

1. Gunakan nama yang jelas dan konsisten. Identifier kode menggunakan bahasa Inggris.
2. Satu class/function/component mempunyai satu tanggung jawab utama.
3. Utamakan komposisi dan dependency injection daripada global state atau hidden coupling.
4. Hindari `God class`, `God component`, controller gemuk, repository generik yang terlalu abstrak, dan file `utils` yang menjadi tempat semua logic.
5. Hindari duplikasi business rule. Setiap rule memiliki satu owner module yang jelas.
6. Business rule harus eksplisit, deterministic, dan dapat diuji.
7. Hindari magic string/number untuk status, permission, dan policy. Gunakan enum/constant/configuration yang sesuai.
8. Jangan menggunakan `any` di TypeScript kecuali ada alasan kuat, ruang lingkup sempit, dan komentar penjelas.
9. Tidak boleh ada empty catch, silent failure, atau error yang sengaja ditelan.
10. Komentar menjelaskan alasan, constraint, atau keputusan non-obvious; bukan membaca ulang kode.
11. Jangan menambah dependency hanya untuk kebutuhan yang dapat diselesaikan sederhana oleh stack yang sudah tersedia.
12. Jangan menambah abstraction sebelum ada variasi atau coupling nyata yang perlu diselesaikan.
13. Seluruh konfigurasi environment dibaca melalui configuration layer; jangan hardcode secret, endpoint environment, bucket credential, atau password.
14. Jangan menyimpan credential, access token, session cookie, signed URL, atau file data sensitif ke Git.
15. Perubahan kontrak API, database, permission, status workflow, atau event audit harus memperbarui dokumentasi dan test terkait.

---

## 6. Frontend Engineering Rules

### 6.1 Struktur

Gunakan feature-based structure. Halaman, komponen domain, API calls, schema form, hook, dan tipe khusus satu fitur ditempatkan bersama fitur tersebut. `shared` hanya berisi primitive atau utilitas yang benar-benar reusable lintas fitur.

Contoh struktur konseptual (sesuaikan dengan repository aktual):

```text
frontend/src/
├── app/
│   ├── routes/
│   ├── providers/
│   ├── layouts/
│   └── guards/
├── features/
│   ├── auth/
│   ├── dashboard/
│   ├── documents/
│   ├── workspaces/
│   ├── requests/
│   ├── distributions/
│   ├── templates/
│   ├── notifications/
│   └── administration/
└── shared/
    ├── components/
    ├── hooks/
    ├── api/
    ├── types/
    ├── utils/
    └── styles/
```

### 6.2 UI and navigation

- Implementasikan left sidebar sebagai navigasi utama desktop.
- Pada tablet/mobile, sidebar berubah menjadi drawer; jangan memaksakan sidebar sempit yang menghabiskan ruang konten.
- Sidebar mempunyai state expanded/collapsed yang tetap mudah dipahami dan tidak menghilangkan accessible label.
- Kelompok navigasi mengikuti sitemap PRD: Workspace/Repository, Requests, Distribution, Templates, dan Administration sesuai permission.
- Active route harus terlihat jelas dengan primary maroon dan background tint; jangan hanya mengandalkan perubahan warna teks.
- Menu yang tidak diizinkan boleh disembunyikan dari UI, tetapi setiap API tetap wajib memeriksa authorization di backend.
- Semua halaman mempunyai loading, empty, error, forbidden, not-found, success, dan conflict state sesuai konteks.
- Form mempunyai label, helper text/error text, validasi, loading state, dan perlindungan double submit.
- Tabel/list harus mendukung pagination server-side bila jumlah data berpotensi besar.
- Hindari modal untuk seluruh alur. Gunakan page/detail view untuk pekerjaan kompleks dan dialog untuk konfirmasi singkat.
- Jangan mengandalkan warna saja untuk status; sertakan label teks/icon atau keterangan.

### 6.3 Component boundaries

- Page component mengatur komposisi halaman dan route-level data requirements, bukan seluruh business logic.
- Form component mengelola field state/validation dan mengirim command ke application/API layer.
- API client terpusat menangani base URL, credentials, response normalization, error mapping, dan CSRF header sesuai konfigurasi backend.
- Komponen domain tidak boleh ditempatkan di shared hanya karena dipakai lebih dari satu halaman dalam satu fitur.
- Jangan menyimpan permission evaluator di frontend. Frontend hanya memakai capability snapshot untuk pengalaman pengguna.

### 6.4 Frontend quality

- TypeScript strict mode harus dipertahankan.
- Hindari state duplikat yang bisa diturunkan dari server state.
- Jangan melakukan refetch tanpa alasan atau membuat query tidak terbatas.
- Mutasi harus memberikan feedback, invalidation/update cache yang benar, dan menangani kegagalan.
- Accessibility keyboard, focus, semantic HTML, contrast, dan reduced-motion menjadi bagian dari acceptance criteria.
- Visual token harus berasal dari `DESIGN_SYSTEM.md`; jangan memperkenalkan warna brand baru secara acak.

---

## 7. Backend Engineering Rules

### 7.1 Modular monolith

Backend menggunakan Java + Spring Boot dalam satu deployable application dengan domain module yang tegas. Hindari satu folder global besar `controller/service/repository` yang mencampur seluruh domain.

Batas modul konseptual:

- `identity` — akun lokal, password, session/account lifecycle.
- `authorization` — role, permission, scope assignment, resource ACL, evaluator.
- `organization` — organization unit dan user assignment.
- `masterdata` — category, type, classification, tags, file policy.
- `repository` — workspace, folder, document, version, storage adapter, external link.
- `workflow` — document request, approval, fulfillment, history.
- `distribution` — distribution, recipient, provenance of access grant.
- `template` — template lifecycle dan publication.
- `notification` — in-app notifications.
- `audit` — business audit event.

### 7.2 Layering dalam module

Gunakan pemisahan konseptual:

- `api` — REST controller, request/response DTO, API validation.
- `application` — use case, transaction boundary, orchestration.
- `domain` — entity/value object, business rule, domain service, state transition.
- `infrastructure` — persistence adapter, storage adapter, framework integration.

Tidak semua module harus memiliki semua subfolder jika tidak dibutuhkan, tetapi dependency direction harus terjaga. Domain tidak boleh bergantung pada web framework, object storage SDK, atau module internal lain.

### 7.3 Controller dan service

- Controller tipis: authentication context, request parsing, bean validation, panggil use case, response mapping.
- Business policy berada di application/domain service.
- Repository tidak boleh menjadi tempat business workflow.
- Gunakan DTO; jangan expose entity JPA secara langsung ke response API.
- Jangan menerima field sensitif seperti `createdBy`, `grantedBy`, `approvedBy`, `status` final, atau ownership dari client tanpa policy/use case yang memvalidasinya.
- Setiap operasi resource memeriksa permission pada resource dan scope yang benar.

### 7.4 Transaction dan concurrency

- Tentukan transaction boundary pada application use case.
- Gunakan optimistic locking/version field untuk update metadata yang rentan konflik.
- Workflow transition harus memvalidasi status lama dalam transaction.
- Cegah double approve, double fulfill, dan double distribution melalui locking/constraint yang sesuai.
- Jangan berasumsi transaksi PostgreSQL mencakup object storage. Terapkan compensation/cleanup yang jelas pada upload failure.

### 7.5 Error contract

Gunakan response error yang konsisten, aman, dan tidak membocorkan stack trace, SQL, path internal, bucket credential, atau keberadaan resource yang terproteksi. Sertakan code, user-safe message, field errors bila relevan, dan request/correlation identifier bila tersedia.

---

## 8. Security Rules — Tidak Boleh Dilanggar

1. **Backend is the authorization authority.** Tidak ada operasi read/write yang mengandalkan hidden menu atau frontend guard.
2. Default deny; permission eksplisit diperlukan.
3. Periksa akun aktif dan session valid pada request terlindungi.
4. Periksa permission, scope, resource ACL, resource status, classification policy, dan expiry sesuai tindakan.
5. Cegah IDOR/resource enumeration; akses berdasarkan UUID tidak otomatis diizinkan.
6. Role assignment tanpa scope tidak boleh dianggap global kecuali role tersebut memang global.
7. Inherited allow tidak mengalahkan explicit deny.
8. Revoke satu sumber grant tidak boleh mencabut grant independen dari sumber lain.
9. Local password wajib menggunakan adaptive password hashing yang sesuai Spring Security; jangan plaintext/reversible encryption.
10. Gunakan session cookie `HttpOnly`, `Secure` pada HTTPS, `SameSite` yang tepat, session invalidation, dan CSRF protection.
11. Tidak ada public self-registration pada pilot.
12. Object storage private. Credential storage hanya berada di backend.
13. Periksa file extension, MIME/content type, signature bila tersedia, ukuran, dan file policy di backend.
14. Tolak executable/script/format berbahaya yang tidak masuk allowlist.
15. Jangan menggunakan original filename sebagai storage object key atau path.
16. Signed URL, bila digunakan, harus berumur pendek, dibuat setelah authorization, dan tidak pernah dicatat ke log/audit.
17. External URL dibatasi ke HTTPS kecuali ada pengecualian bisnis yang disetujui secara eksplisit.
18. Audit metadata tidak boleh berisi password, password hash, session ID/cookie, token, signed URL, storage credentials, atau isi binary.
19. Akun INACTIVE/LOCKED tidak dapat mengakses aplikasi; session terkait harus diinvalidate sesuai policy.
20. Uji privilege escalation, cross-unit access, IDOR, access-after-revoke, expiry, dan account deactivation.

### Batasan risiko yang wajib disebutkan

Antivirus tidak termasuk pilot. Validasi file dasar tidak sama dengan malware scanning. Jangan memberi label file sebagai "aman dari malware". Cantumkan residual risk dan batas operasional pada dokumentasi deployment/UAT.

---

## 9. Database and Migration Rules

- PostgreSQL adalah source of truth untuk seluruh metadata, relasi, permission, workflow, notification, dan audit.
- Gunakan migration versioned (Flyway atau tool yang sudah dipilih repository); jangan mengubah schema manual di environment bersama.
- Migration yang sudah dirilis dianggap immutable. Perbaikan dibuat melalui migration baru.
- Primary key resource menggunakan UUID atau identifier stabil yang ditetapkan schema.
- Semua foreign key penting, unique constraint, check constraint, dan index harus didefinisikan eksplisit.
- Simpan timestamp sebagai `TIMESTAMPTZ` UTC.
- Hindari hard delete untuk data yang punya histori atau masih direferensikan. Gunakan archive/status/soft delete sesuai domain.
- Jangan menyimpan binary file dalam PostgreSQL.
- Jangan menaruh status bisnis yang penting hanya di JSONB; gunakan kolom bertipe untuk field yang difilter, dijoin, atau menjadi invariant. JSONB dipakai untuk metadata tambahan yang aman dan tidak menggantikan relasi.
- Pastikan satu document hanya memiliki satu current version.
- Pastikan satu template tidak menggandakan binary/versioning milik document.
- Uji migration terhadap database kosong dan jalur upgrade yang relevan.
- Uji constraint untuk invariant kritis; jangan hanya mengandalkan validasi frontend.

---

## 10. File Storage Rules

- Gunakan storage adapter/interface agar domain repository tidak bergantung langsung pada MinIO SDK.
- Simpan object dengan key random/stabil yang dibuat backend, bukan nama file asli.
- Catat bucket/container, object key, original filename, content type, size, checksum, dan waktu upload pada metadata DB.
- Binary version immutable; revisi menciptakan object/version baru.
- Lakukan authorization sebelum preview/download dan sebelum menerbitkan signed URL.
- Upload tidak boleh memberi status sukses sebelum object tersimpan dan metadata berhasil dicatat.
- Bila persist metadata gagal setelah object upload, lakukan compensating delete. Catat/identifikasi orphan object bila compensation gagal.
- Jangan meletakkan object storage bucket public.
- External Office URL hanya dicatat. Jangan mengklaim KCIC Connect mengatur permission pada provider eksternal.

---

## 11. API Rules

- Gunakan REST API berversi dengan baseline prefix `/api/v1`.
- Resource naming konsisten, plural untuk collection.
- Gunakan HTTP semantics dengan tepat; jangan memakai `200 OK` untuk semua hasil.
- Pagination/filter/sort ditangani secara server-side.
- Validasi input pada backend; frontend validation hanya untuk UX.
- Response memakai DTO eksplisit dan format error yang konsisten.
- Mutasi penting harus idempotent atau memiliki perlindungan terhadap duplicate submission bila relevan.
- Jangan mengirim entity database, storage key internal, password hash, atau secret ke client.
- API contract baru/berubah harus mempunyai integration test dan dokumentasi.

---

## 12. Testing and Quality Gates

### Test layers

1. **Unit tests:** state transition, policy evaluator, validation, mapping, domain rules.
2. **Integration tests:** PostgreSQL repository, transaction, session, object storage adapter, notification/audit persistence.
3. **Authorization tests:** allow/deny, scope, inheritance, explicit deny, expiry, revoke, cross-unit, inactive account, IDOR.
4. **E2E tests:** login, user administration, create/search/download document, versioning, request, approval, fulfillment, distribution, template, audit.
5. **Frontend tests:** critical component state, form validation, navigation permission states, error/loading/empty states.

### Minimum quality gate sebelum task dinyatakan selesai

- Build frontend berhasil.
- Lint/type-check frontend berhasil.
- Build backend berhasil.
- Unit test terkait berhasil.
- Integration/security test terkait berhasil atau blocker-nya didokumentasikan.
- Migration valid dan reversible/rollback plan dipahami sesuai tool.
- Tidak ada secret baru atau log data sensitif.
- UI memenuhi state dan responsive behavior yang relevan.
- Dokumen terkait diperbarui bila ada perubahan API, schema, permission, workflow, atau UX decision.

### Jangan memanipulasi gate

Jangan menonaktifkan test, lint rule, type-check, constraint, atau security filter untuk memperoleh status hijau tanpa justifikasi teknis yang disetujui. Jika gate tidak dapat dijalankan karena environment, beri status `BLOCKED`, bukan `PASS`.

---

## 13. Delivery Workflow untuk AI Coding Agent

Untuk setiap task, hasilkan ringkasan berikut:

### A. Before implementation

- Tujuan task dan requirement/acceptance criteria terkait.
- File/module yang sudah ada dan akan diubah.
- Dependency dan risiko teknis.
- Test yang akan ditambahkan/dijalankan.

### B. During implementation

- Implementasikan perubahan dalam batas domain yang benar.
- Pertahankan backward compatibility jika tidak ada instruksi breaking change.
- Tambahkan test berbarengan dengan business behavior.
- Hindari perubahan out-of-scope.

### C. After implementation

Laporkan:

- Files changed.
- Behavior yang benar-benar selesai.
- Test/build/lint yang benar-benar dijalankan dan hasilnya.
- Migration/API/documentation changes.
- Known limitation atau residual risk.
- Item yang masih `BLOCKED` atau `DEFERRED`.

Jangan menulis "done" untuk pekerjaan yang baru berupa scaffolding, UI statis, atau endpoint stub.

---

## 14. Aturan Scope dan Perubahan Arsitektur

Agent tidak boleh menambahkan framework, service, database, cloud provider, atau pola arsitektur baru hanya karena populer atau dianggap scalable.

Sebelum mengusulkan perubahan arsitektur:

1. Jelaskan masalah konkret pada baseline.
2. Jelaskan dampak pada operasional, biaya, security, maintainability, dan developer workflow.
3. Tunjukkan mengapa solusi dalam stack saat ini tidak memadai.
4. Tawarkan pilihan paling sederhana yang memenuhi requirement.
5. Tunggu persetujuan untuk perubahan yang mengubah keputusan terkunci.

Jangan mengubah modular monolith menjadi microservices, local authentication menjadi SSO, PostgreSQL menjadi database lain, atau MinIO menjadi provider lain secara sepihak.

---

## 15. Definition of Done Agent

Task hanya dapat dinyatakan selesai apabila:

- [ ] Implementasi sesuai PRD dan acceptance criteria.
- [ ] Kode berada dalam module boundary yang benar.
- [ ] Backend authorization dan validation tersedia untuk operasi terlindungi.
- [ ] Database constraint/migration tersedia bila dibutuhkan.
- [ ] Audit event tersedia untuk aksi bisnis penting.
- [ ] Loading, empty, error, success, forbidden, dan conflict state tersedia sesuai konteks.
- [ ] Automated test relevan ditambahkan/diperbarui.
- [ ] Build/lint/type-check/test terkait telah dijalankan atau blocker dijelaskan.
- [ ] Tidak ada credential atau data sensitif yang bocor.
- [ ] Dokumentasi kontrak dan keputusan diperbarui.
- [ ] Tidak ada perubahan out-of-scope yang disisipkan.

**Prinsip akhir:** implementasikan kebutuhan yang benar, dengan desain paling sederhana yang tetap aman, konsisten, dapat diuji, dan dapat diperluas. Jangan mengorbankan integritas sistem demi kecepatan vibe coding.
