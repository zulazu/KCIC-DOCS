# KCIC Docs — Implementation Plan

| Metadata | Nilai |
|---|---|
| Product | KCIC Docs |
| Document | Development Roadmap & Execution Plan |
| Version | 1.0 |
| Status | Baseline plan untuk AI-assisted/vibe coding |
| Tanggal | 22 November 2026 |
| Referensi | `PRD.md`, `ARCHITECTURE.md`, `SCHEMA.md`, `DESIGN_SYSTEM.md`, `AGENTS.md` |

---

## 1. Tujuan Dokumen

Dokumen ini memecah kebutuhan KCIC Docs menjadi fase implementasi yang dapat dikerjakan, diverifikasi, dan ditinjau. Urutan fase menempatkan foundation, authorization, dan repository sebagai dependency sebelum workflow tingkat lanjut.

KCIC Docs merupakan pilot project yang harus dibangun dengan standar engineering serius. Fase boleh diselesaikan bertahap, tetapi setiap fase harus menghasilkan behavior yang benar-benar berjalan dan memiliki test evidence. Jangan menyamakan halaman yang sudah dibuat dengan fitur yang sudah selesai.

---

## 2. Prinsip Eksekusi

1. **Vertical slice:** setiap fitur diselesaikan dari UI/API hingga database, authorization, audit, dan test yang relevan.
2. **Secure baseline first:** local authentication dan backend authorization tidak boleh ditunda sampai akhir.
3. **Data integrity first:** schema/migration dan invariant ditetapkan sebelum frontend bergantung pada data tersebut.
4. **Modular monolith:** implementasi domain dalam satu backend dengan batas module yang jelas.
5. **Design-system first:** AppShell dan left sidebar dibangun sebelum seluruh halaman fitur agar layout tidak berulang dan berbeda.
6. **No speculative infrastructure:** tidak ada SSO, antivirus, centralized monitoring/logging, Redis, RabbitMQ, Kubernetes, atau microservices sebagai dependency pilot.
7. **Evidence-driven completion:** setiap fase memiliki exit gate yang benar-benar dijalankan.
8. **Document as code:** perubahan status, schema, API, permission, dan decision harus memperbarui dokumen terkait.
9. **No scope creep:** hanya requirement PRD atau keputusan user yang disetujui yang boleh masuk backlog.

---

## 3. Scope and Priority

### 3.1 Priority levels

- **P0 — Must:** diperlukan agar pilot dapat digunakan dengan aman dan alur inti berfungsi.
- **P1 — Should:** diperlukan untuk tata kelola operasional yang baik atau release pilot lanjutan.
- **P2 — Could:** nilai tambah setelah proses inti stabil.

### 3.2 Release mapping

| Release | Fokus | Fase implementasi |
|---|---|---|
| R0 — Foundation & Access | Project foundation, AppShell, local auth, organization, RBAC baseline | Phase 0–2 |
| R1 — Repository | Master data, workspace, folder, document, upload/link, versioning, search, ACL, audit | Phase 3–5 |
| R2 — Workflow & Sharing | Request, approval, fulfillment, distribution, notification | Phase 6–7 |
| R3 — Template & Governance | Template lifecycle, admin governance, reporting dasar | Phase 8 |
| R4 — Pilot Hardening | Security review, regression, backup/restore, UAT, handover | Phase 9 |

Urutan ini memperluas release grouping pada PRD agar pekerjaan mudah diprioritaskan dan diverifikasi.

---

## 4. Phase 0 — Project Discovery and Repository Baseline

**Goal:** memahami repository dan environment aktual, lalu menyiapkan keputusan implementasi yang tidak ambigu.

### Work items

- [ ] Audit repository, Git status, branch, existing application, dan dokumentasi.
- [ ] Identifikasi frontend/backend structure, runtime, package manager, build tool, test runner, database migration tool, dan existing coding conventions.
- [ ] Identifikasi environment development/test/staging dan cara menjalankan PostgreSQL/object storage.
- [ ] Review dependency dan library yang sudah ada; hindari membuat stack duplikat.
- [ ] Validasi versi PostgreSQL dan kompatibilitas UUID/JSONB/index.
- [ ] Tetapkan API contract conventions: response envelope, error contract, pagination, sorting, filtering, date/time.
- [ ] Tetapkan migration naming/versioning dan seed strategy.
- [ ] Susun backlog story per module berdasarkan PRD.
- [ ] Identifikasi open question yang harus diselesaikan oleh business owner sebelum UAT/operasional.

### Deliverables

- Repository audit note.
- Development setup instructions yang sesuai tool aktual.
- Initial backlog dengan requirement IDs/acceptance criteria.
- API conventions dan migration conventions.
- Risk/dependency list.

### Exit gate

- [ ] Tim/agent dapat menjalankan frontend/backend sesuai instruksi repository.
- [ ] Baseline build/test dapat dieksekusi atau blocker environment terdokumentasi.
- [ ] Tidak ada konflik keputusan yang mengubah stack/scope.
- [ ] Semua story Phase 1 mempunyai acceptance criteria dan owner module.

**Do not:** mulai membuat seluruh halaman UI sebelum audit memastikan struktur aplikasi dan pola implementasi.

---

## 5. Phase 1 — Engineering Foundation and Design System

**Goal:** menyiapkan struktur aplikasi, quality gates, API foundation, migration baseline, dan AppShell yang digunakan seluruh fitur.

### Frontend work items

- [ ] Bootstrap/rapikan React + TypeScript mengikuti repository aktual.
- [ ] Aktifkan TypeScript strict checks dan lint.
- [ ] Implementasikan Tailwind CSS mapping dari token `DESIGN_SYSTEM.md`.
- [ ] Implementasikan reusable UI primitives: Button, Input, Select, Textarea, Badge, Card, Table primitives, Dialog, Drawer, Tooltip, Toast/Alert, Skeleton, EmptyState, ErrorState.
- [ ] Implementasikan AppShell dengan **left sidebar** expanded/collapsed.
- [ ] Implementasikan responsive drawer untuk tablet/mobile.
- [ ] Implementasikan top header: breadcrumb/page context, notification entry placeholder yang belum mengklaim fitur aktif, user menu placeholder sesuai tahap.
- [ ] Implementasikan route structure dan protected-layout boundary (authorization enforcement tetap backend).
- [ ] Implementasikan loading/empty/error/forbidden/not-found pattern dasar.
- [ ] Tambahkan component/unit test untuk primitive dan navigasi.

### Backend work items

- [ ] Bootstrap/rapikan Java + Spring Boot mengikuti repository aktual.
- [ ] Buat module/package boundary per Architecture Document.
- [ ] Set up centralized exception handling dan error response contract.
- [ ] Set up request identifier/correlation field bila belum tersedia.
- [ ] Set up configuration profiles/environment handling tanpa credential di repository.
- [ ] Set up PostgreSQL connectivity.
- [ ] Set up migration tool dan initial schema structure.
- [ ] Set up object storage adapter interface dan local development configuration; jangan membuat bucket public.
- [ ] Set up unit/integration test harness.
- [ ] Tambahkan health endpoint internal dan build/version identifier sederhana.

### Deliverables

- Aplikasi dapat boot dengan configuration yang terdokumentasi.
- AppShell dan left sidebar responsive.
- Design tokens dan shared components baseline.
- API error contract dan migration baseline.
- Automated lint/test/build gate aktif.

### Exit gate

- [ ] Frontend lint + type-check + build PASS.
- [ ] Backend build + baseline tests PASS.
- [ ] Migration dapat dijalankan pada database kosong.
- [ ] Left sidebar berjalan pada desktop dan drawer pada viewport sempit.
- [ ] Accessibility dasar: semantic nav, focus, keyboard, labels, contrast review.
- [ ] Tidak ada real credential pada config/repository.

---

## 6. Phase 2 — Local Authentication, Users, Organization, and RBAC

**Goal:** membuat identity dan authorization baseline sebelum document repository dibuka.

### 6.1 Local authentication

- [ ] Seed permission catalog dan role baseline secara versioned.
- [ ] Implementasikan bootstrap initial administrator melalui prosedur aman; tanpa default password permanen dalam source code.
- [ ] Implementasikan login username/email + password.
- [ ] Implementasikan logout/session invalidation.
- [ ] Implementasikan `/auth/me` atau equivalent endpoint untuk current user + capability summary.
- [ ] Implementasikan secure session cookie dan CSRF protection.
- [ ] Implementasikan rate/attempt policy dan account lock behavior sesuai konfigurasi.
- [ ] Implementasikan change password.
- [ ] Implementasikan admin password reset/forced password change sesuai policy.
- [ ] Implementasikan account status ACTIVE/INACTIVE/LOCKED.
- [ ] Pastikan deaktivasi user mencabut session terkait.

### 6.2 User and organization administration

- [ ] User directory dengan pagination/search/filter.
- [ ] Create/edit user.
- [ ] User detail dan account status control.
- [ ] Organization unit CRUD (tanpa hard delete data yang direferensikan).
- [ ] Organization hierarchy dengan circular-reference prevention.
- [ ] Primary organization assignment dan history.

### 6.3 RBAC and scope

- [ ] Role/permission catalog.
- [ ] Role-permission mapping.
- [ ] User role assignment dengan scope GLOBAL/ORGANIZATION_UNIT.
- [ ] Workspace scope assignment diselesaikan saat workspace module tersedia.
- [ ] Backend permission evaluator untuk capability dan unit scope.
- [ ] Audit event untuk user/role/permission/account changes.
- [ ] Frontend navigation visibility berdasarkan capability snapshot.

### Deliverables

- Local authentication end-to-end.
- User and organization administration.
- RBAC baseline and scope-aware authorization.
- Session invalidation and audit coverage.

### Exit gate

- [ ] Login/logout/change password/reset flow PASS.
- [ ] INACTIVE/LOCKED user ditolak.
- [ ] Session lama tidak berlaku setelah logout/deactivation.
- [ ] User tanpa permission gagal mengakses admin endpoint meski memanggil API langsung.
- [ ] Scope unit teruji termasuk cross-unit deny.
- [ ] CSRF dan secure cookie policy terverifikasi pada environment yang sesuai.
- [ ] Audit event untuk account/role/permission changes tersedia.

---

## 7. Phase 3 — Master Data and Workspace Foundation

**Goal:** menyiapkan vocabulary dokumen dan batas kepemilikan repository.

### Work items

- [ ] CRUD document categories dan optional hierarchy.
- [ ] CRUD document types.
- [ ] CRUD classifications dengan `sensitivity_rank` yang disetujui.
- [ ] CRUD tags.
- [ ] File policy management (extension allowlist, MIME allowlist, max size) berdasarkan business decision.
- [ ] Workspace create/update/archive.
- [ ] Workspace owner unit/user assignment.
- [ ] Folder create/rename/move/archive.
- [ ] Folder hierarchy validation dan prevention circular parent.
- [ ] Workspace-scoped role assignment.
- [ ] Master-data audit event.
- [ ] Workspace/folder authorization tests.

### Deliverables

- Master data administration.
- Workspace/folder tree.
- Scope boundary untuk contributor dan unit manager.

### Exit gate

- [ ] Master data tidak dapat dihapus jika masih direferensikan.
- [ ] Workspace hanya terlihat sesuai permission/scope.
- [ ] Folder tidak dapat dipindahkan cross-workspace tanpa validasi akses sumber dan target.
- [ ] Folder circular hierarchy ditolak.
- [ ] User tidak dapat membuat/mengubah master data tanpa permission.
- [ ] Migration, tests, dan audit PASS.

---

## 8. Phase 4 — Document Repository and File Storage

**Goal:** menyelesaikan alur utama katalog dokumen file dan Office/external link.

### 8.1 Document metadata

- [ ] Create document record dengan required metadata.
- [ ] Owner type user/unit validation.
- [ ] Document owner change with owner-history persistence and audit event.
- [ ] Workspace/folder relation validation.
- [ ] Owner history is created on document creation and retained on transfer.
- [ ] Category/type/classification validation.
- [ ] Document code conditional uniqueness policy.
- [ ] Document list dengan server-side pagination, search, filter, dan sorting whitelist.
- [ ] Personal Favorites (add/remove/list), tanpa memberikan permission baru.
- [ ] Document detail dengan metadata, owner, classification, status, current version, dan actions sesuai permission.

### 8.2 File upload and storage

- [ ] Implementasikan MinIO/S3 storage adapter.
- [ ] Private bucket/container dan least-privileged credentials.
- [ ] Backend upload validation: allowlist extension, size, MIME, signature bila tersedia.
- [ ] Generate internal object key; simpan original filename sebagai metadata.
- [ ] Upload progress pada UI.
- [ ] Checksum SHA-256 dan file metadata persistence.
- [ ] Compensation saat storage upload berhasil tetapi DB persistence gagal.
- [ ] Orphan object handling yang dapat ditelusuri.
- [ ] Preview/download flow dengan authorization ulang.
- [ ] Safe content disposition dan short-lived signed URL atau backend streaming.

### 8.3 External link

- [ ] Register external/Office URL dan metadata.
- [ ] HTTPS/scheme validation dan safe URL validation.
- [ ] UI membedakan `Open external link` dari `Download from KCIC Docs`.
- [ ] Tampilkan informasi bahwa akses provider eksternal tetap mengikuti permission provider.

### 8.4 Versioning and lifecycle

- [ ] Create document version.
- [ ] Satu current version per document.
- [ ] Version history list/detail.
- [ ] Atomic version switch current/superseded.
- [ ] Archive/restore document.
- [ ] Checksum/integrity verification path.
- [ ] Optimistic locking untuk metadata update.

### Deliverables

- End-to-end document record, upload/link, search, detail, preview/download, version history.
- Storage adapter dan failure compensation.
- Basic repository governance.

### Exit gate

- [ ] Upload valid file berhasil dan object private.
- [ ] File invalid/oversize/MIME mismatch ditolak backend.
- [ ] Kegagalan storage/DB tidak menghasilkan state sukses palsu.
- [ ] Download/preview selalu memeriksa authorization ulang.
- [ ] Versi lama tetap immutable dan dapat ditelusuri.
- [ ] Partial unique constraint menjaga satu current version.
- [ ] Search/list memakai pagination dan tidak query tak terbatas.
- [ ] Favorites hanya menampilkan dokumen yang masih dapat diakses dan tidak membypass ACL.
- [ ] Office link tidak mengklaim permission synchronization.
- [ ] Audit create/upload/version/download/archive tersedia.

---

## 9. Phase 5 — Resource ACL and Access Governance

**Goal:** melengkapi akses resource dari workspace sampai dokumen.

### Work items

- [ ] Implementasikan ACL grant untuk workspace/folder/document.
- [ ] Implementasikan principal USER/ROLE/ORGANIZATION_UNIT sesuai scope baseline.
- [ ] Implementasikan permission effect ALLOW/DENY.
- [ ] Implementasikan inheritance workspace → folder → document.
- [ ] Implementasikan explicit deny precedence.
- [ ] Implementasikan expiry dan revoke.
- [ ] Implementasikan permission summary panel untuk user berwenang.
- [ ] Tampilkan source grant (manual/request/distribution/inherited) bila dapat dilihat.
- [ ] Re-evaluate access ketika document/folder dipindah.
- [ ] Uji role capability + scope + ACL + account state + classification.

### Deliverables

- Access management UI/API.
- Policy evaluator dan test matrix.
- Audit grant/revoke.

### Exit gate

- [ ] Default deny berjalan.
- [ ] Explicit deny mengalahkan inherited allow.
- [ ] Inherited allow hanya berlaku pada resource ancestry yang benar.
- [ ] Cross-unit dan IDOR attempts gagal.
- [ ] Expired/revoked grant gagal.
- [ ] Revoke satu grant tidak memengaruhi grant independent lainnya.
- [ ] System Admin tidak otomatis dapat membaca seluruh isi dokumen.
- [ ] Setiap operasi view/preview/download/update/version/share memeriksa backend permission.

---

## 10. Phase 6 — Document Request and Approval Workflow

**Goal:** mengganti request informal melalui chat dengan lifecycle yang dapat dilacak.

### Work items

- [ ] Create request type ACCESS, DOCUMENT_DELIVERY, TEMPLATE.
- [ ] Required fields: reason, target, requester, owner/unit routing.
- [ ] Request list: My Requests dan Assigned Requests.
- [ ] Request detail dan timeline.
- [ ] Optional request attachment bila file policy dan kebutuhan pilot mengizinkan.
- [ ] Approver assignment dan inbox.
- [ ] Approve/reject dengan reason wajib saat reject.
- [ ] Fulfillment: grant access, link existing document, upload file, register external URL.
- [ ] Request cancel/close policy.
- [ ] Backend transition guard dan optimistic locking.
- [ ] Request history dan audit event.
- [ ] In-app notification trigger untuk event inti.

### Deliverables

- Request end-to-end dari submit hingga fulfilled/closed.
- Approval inbox dan assigned queue.
- Request timeline dan notification.

### Exit gate

- [ ] ACCESS request wajib menunjuk target document.
- [ ] Request submit/approve/reject/fulfill/cancel mengikuti state transition yang sah.
- [ ] Reject wajib reason.
- [ ] ACCESS request baru fulfilled setelah ACL grant berhasil dibuat.
- [ ] Request fulfilled tidak dapat kembali ke state sebelumnya.
- [ ] Unauthorized user tidak dapat melihat/memproses request pihak lain.
- [ ] Request history, notification, dan audit konsisten.
- [ ] Double approve/fulfill ditolak.

---

## 11. Phase 7 — Distribution and Recipient Access

**Goal:** mengirim satu/lebih dokumen ke user/unit internal dan mengelola akses yang berasal dari distribution.

### Work items

- [ ] Create distribution dari dokumen yang pengirim boleh bagikan.
- [ ] Select recipients: individual user atau organization unit.
- [ ] Snapshot akun ACTIVE dari target unit saat send.
- [ ] Pilih permission yang dapat didelegasikan pengirim.
- [ ] Optional expiry.
- [ ] Create distribution item dengan snapshot document version.
- [ ] Create recipient rows, ACL grants, provenance mapping, notifications.
- [ ] Sent dan Received list/detail.
- [ ] Record READ ketika recipient membuka distribution detail.
- [ ] Revoke distribution/individual recipient.
- [ ] Expiry enforcement pada backend tanpa menunggu cleanup.
- [ ] Distribution event history dan audit.

### Deliverables

- Send/receive distribution end-to-end.
- Recipient status dan access provenance.
- Revoke/expiry behavior.

### Exit gate

- [ ] Penerima hanya user internal ACTIVE.
- [ ] Unit recipient di-snapshot pada waktu send.
- [ ] Sender tidak dapat memberi permission melebihi hak delegasi.
- [ ] Gagal sebagian tidak dilaporkan sebagai send success.
- [ ] Revoke distribution hanya mengakhiri grant yang berasal dari distribution tersebut.
- [ ] Akses setelah revoke/expiry ditolak.
- [ ] READ dijelaskan sebagai membuka detail distribution, bukan bukti membaca konten.
- [ ] Distribution history/notifications/audit konsisten.

---

## 12. Phase 8 — Template Catalog and Governance

**Goal:** menyediakan katalog template resmi dengan version history.

### Work items

- [ ] Create template metadata yang mengacu pada document existing.
- [ ] Template code/name/owner/category/status.
- [ ] Template catalog filter/search.
- [ ] Draft/publish/archive lifecycle.
- [ ] Publish current document version.
- [ ] Revisi membuat document version baru, bukan binary duplikat.
- [ ] Template usage events (view/download/copy bila event tersedia).
- [ ] Admin governance view.
- [ ] Dashboard/reporting dasar P1 jika data sudah tersedia.

### Deliverables

- Template catalog.
- Publish/archive/version history flow.
- Basic template usage trace.

### Exit gate

- [ ] Hanya user dengan `template.publish` dapat publish.
- [ ] Published template menunjuk version dari document yang sama.
- [ ] Draft/archived tidak tampil di katalog umum.
- [ ] Versi lama tetap ditelusuri sesuai permission.
- [ ] Template tidak menyimpan binary duplikat.
- [ ] Publish/archive/usage memiliki audit event.

---

## 13. Phase 9 — Pilot Hardening, UAT, and Handover

**Goal:** membuktikan aplikasi layak digunakan pada scope pilot yang disetujui.

### 13.1 Functional regression

- [ ] Jalankan seluruh unit/integration/E2E suites.
- [ ] Jalankan regression seluruh flow AUTH, USER, MASTER, REPO, ACL, REQ, DIST, TEMPLATE, NOTIF, AUDIT.
- [ ] Pastikan dashboard tidak menampilkan resource di luar permission pengguna.
- [ ] Pastikan semua halaman mempunyai loading/empty/error/success/forbidden/conflict state sesuai konteks.

### 13.2 Security review

- [ ] IDOR dan resource enumeration.
- [ ] Privilege escalation dan cross-unit access.
- [ ] CSRF/session fixation/session expiry/deactivation.
- [ ] Password/reset policy.
- [ ] File extension/MIME/signature/size validation.
- [ ] Safe filename/path handling.
- [ ] Signed URL TTL/log hygiene atau backend streaming security.
- [ ] External URL validation.
- [ ] Secret/configuration review.
- [ ] Audit metadata privacy review.
- [ ] Residual risk tanpa antivirus dicatat dan disetujui.

### 13.3 Operational readiness

- [ ] Deployment environment dan TLS tersedia.
- [ ] Private object storage dikonfigurasi.
- [ ] PostgreSQL/object storage backup berjalan.
- [ ] Restore drill berhasil pada environment terisolasi.
- [ ] Migration runbook siap.
- [ ] Initial administrator bootstrap aman.
- [ ] Build/version identifier tersedia.
- [ ] Basic application log/error handling aman.
- [ ] Support owner dan escalation path ditentukan.
- [ ] Known limitations dan deferred integrations didokumentasikan.

### 13.4 UAT and handover

- [ ] UAT scenario disetujui business owner/document owner.
- [ ] UAT dilakukan dengan role berbeda dan sample data yang disetujui.
- [ ] Defect critical/high diselesaikan atau diterima formal dengan mitigasi.
- [ ] User guide/admin guide/runbook tersedia sesuai kebutuhan.
- [ ] Business owner menyetujui pilot readiness.

### Exit gate

Pilot tidak dinyatakan siap hanya karena build lulus. Semua security, data integrity, backup/restore, UAT, dan residual-risk gate harus memiliki evidence atau explicit acceptance.

---

## 14. Cross-Phase Backlog Dependencies

| Capability | Dependency |
|---|---|
| Permission-aware navigation | AppShell + `/auth/me` capability snapshot |
| User role assignment | Local identity + role/permission catalog |
| Workspace scoped role | Organization + workspace foundation |
| Document creation | Master data + workspace/folder + identity |
| Upload/versioning | Document model + storage adapter + file policy + authorization |
| Preview/download | Document version + storage adapter + authorization |
| Resource ACL inheritance | Workspace/folder/document model + permission evaluator |
| ACCESS request | Document + ACL grant/revoke |
| DOCUMENT_DELIVERY request | Owner routing + document create/upload/link |
| Distribution | Document/version + permission delegation + recipient model + notification |
| Template publication | Document version + permission + template metadata |
| Audit search | Audit capture + `audit.view` permission |
| Dashboard/reporting | Stable domain events and bounded queries |

---

## 15. Test Strategy and Quality Gates

### 15.1 Unit tests

- Request state transition.
- Document version current/superseded rules.
- Permission evaluator.
- Inheritance and explicit deny.
- Grant expiry/revoke/source.
- Distribution recipient snapshot.
- Template publication/version rules.
- File metadata and URL validation.

### 15.2 Integration tests

- PostgreSQL repositories and constraints.
- Transaction behavior and optimistic locking.
- Spring Security local session and CSRF.
- Object storage adapter.
- Upload compensation on DB failure.
- Signed URL/streaming authorization.
- Notification persistence.
- Audit transaction behavior.

### 15.3 Security tests

- IDOR, privilege escalation, cross-unit access.
- Inactive account/session invalidation.
- Access-after-revoke/expiry.
- Explicit deny vs inherited allow.
- Unsafe file upload and path traversal.
- CSRF and cookie/session behavior.
- Unsafe external URL schemes.

### 15.4 E2E tests

1. Login/logout/change password.
2. Admin create user, role, unit assignment.
3. Contributor creates workspace/document and uploads file.
4. Register external Office URL.
5. Employee searches document and gets allowed/denied response.
6. Document owner creates new version.
7. Employee submits access request; approver grants/rejects.
8. Owner fulfills document delivery request.
9. Contributor sends distribution; recipient views; sender revokes.
10. Admin publishes template; employee accesses current version.
11. Auditor filters business audit events.

### 15.5 Quality gate per PR/task

- Frontend lint/type-check/build.
- Backend build.
- Relevant unit and integration tests.
- Authorization negative test for new protected actions.
- Migration validation when schema changes.
- UI state/accessibility validation for new pages.
- Documentation update for API/schema/workflow/permission change.

Jangan menggunakan code coverage sebagai satu-satunya ukuran. Fokus pada behavior dan negative cases.

---

## 16. Definition of Ready (DoR)

Story dapat dimulai bila:

- [ ] Requirement ID/tujuan jelas.
- [ ] User/persona dan actor jelas.
- [ ] Acceptance criteria terukur.
- [ ] Permission yang dibutuhkan terdefinisi.
- [ ] Owner module/data jelas.
- [ ] Status transition dan error cases dipahami bila relevan.
- [ ] API/schema/dependency tersedia atau masuk scope story.
- [ ] UI state/loading/empty/error/success/forbidden jelas.
- [ ] Tidak ada open decision kritis yang memblokir behavior.

---

## 17. Definition of Done (DoD)

Story selesai bila:

- [ ] Behavior sesuai PRD dan acceptance criteria.
- [ ] Implementasi berada dalam module boundary yang benar.
- [ ] Backend validation dan authorization tersedia.
- [ ] Constraint/migration tersedia bila data berubah.
- [ ] Audit event tersedia untuk aksi penting.
- [ ] Frontend loading/empty/error/success/forbidden/conflict state tersedia sesuai konteks.
- [ ] Test relevan ditambahkan/diperbarui dan lulus.
- [ ] Lint/type-check/build terkait lulus atau blocker dicatat.
- [ ] Tidak ada secret/data sensitif yang bocor.
- [ ] Dokumentasi diperbarui.
- [ ] Tidak ada defect critical/high yang tidak ditangani/diterima.

---

## 18. Recommended First Backlog (Start Here)

Urutan backlog awal yang disarankan untuk AI coding agent:

1. **KCIC-FOUND-001 — Repository audit and runnable baseline**
2. **KCIC-FOUND-002 — Frontend shell, tokens, responsive left sidebar**
3. **KCIC-FOUND-003 — Backend foundation, error contract, migrations, health/version**
4. **KCIC-AUTH-001 — Local user model and secure login/session**
5. **KCIC-AUTH-002 — `/auth/me`, logout, CSRF, account status**
6. **KCIC-AUTH-003 — Role/permission catalog and evaluator**
7. **KCIC-ORG-001 — Organization units and user assignments**
8. **KCIC-ADMIN-001 — User/role/organization administration**
9. **KCIC-MASTER-001 — Document category/type/classification/tag/file policy**
10. **KCIC-REPO-001 — Workspace and folder foundations**
11. **KCIC-REPO-002 — Document metadata and search**
12. **KCIC-REPO-003 — Private file upload and storage adapter**
13. **KCIC-REPO-004 — Document versioning, preview/download, archive**
14. **KCIC-ACL-001 — Resource permission and inheritance**
15. **KCIC-REQ-001 — Request submit/review/fulfill**
16. **KCIC-DIST-001 — Distribution send/receive/revoke**
17. **KCIC-TEMP-001 — Template publish/catalog**
18. **KCIC-HARDEN-001 — Security, restore drill, regression, UAT**

Tambahan backlog E-series (detail di §21): KCIC-ORG-002 (typed hierarchy), KCIC-EMP-001/002 (employee master, placement), KCIC-EMP-USER-001 (1:1 account linkage), KCIC-AUTHZ-001 (descendant opt-in), KCIC-ADMIN-002 (employee-first administration), KCIC-MIGR-001 (HR import + V18 cutover), KCIC-ORG-003 (legacy retirement), KCIC-DOC-001 (E0 documentation), KCIC-SEC-001/KCIC-UAT-002 (E8 hardening/UAT).

Setiap backlog item harus mempunyai prompt/task brief yang menyebut requirement ID PRD, module owner, file scope, acceptance criteria, test cases, dan larangan scope creep.

---

## 19. Progress Tracking Format

Gunakan tabel berikut ketika melaporkan progress fase:

| Story | Status | Implementation evidence | Tests | Blockers / notes |
|---|---|---|---|---|
| KCIC-... | `NOT_STARTED` / `IN_PROGRESS` / `BLOCKED` / `DONE` | PR/file/API/migration yang benar-benar ada | Command + result | Risiko atau keputusan tersisa |

Jangan memakai `DONE` ketika hanya scaffold atau UI statis yang tersedia. Status `DONE` memerlukan Definition of Done.

---

## 20. Final Pilot Release Gate

KCIC Docs dapat dinyatakan siap untuk pilot bila:

- [ ] Seluruh P0 user journeys berjalan end-to-end.
- [ ] Local account dan account lifecycle aman.
- [ ] Backend authorization dan ACL telah diuji positive/negative.
- [ ] Upload, link, versioning, download, dan storage consistency terverifikasi.
- [ ] Request, approval, fulfillment, distribution, dan revoke/expiry konsisten.
- [ ] Template lifecycle dan notification berjalan.
- [ ] Business audit mencatat event penting.
- [ ] Database migration dan restore procedure tervalidasi.
- [ ] Security review tidak menyisakan critical/high issue tanpa acceptance formal.
- [ ] Residual risk tanpa antivirus dinyatakan eksplisit.
- [ ] UAT diterima business owner.
- [ ] Documentation, support owner, dan handover siap.

**Prinsip akhir:** bangun fondasi yang kuat, selesaikan alur bisnis secara vertikal, dan minta bukti untuk setiap klaim bahwa fitur telah selesai.

---

## 21. E-Series Roadmap — Organization & Identity Enhancement

Dokumen rujukan: `docs/organization-identity/PHASE_E0_AUDIT.md`, `ARCHITECTURE_GAP_ANALYSIS.md`, `MIGRATION_RISK_REGISTER.md`, `docs/MIGRATION_PLAN.md`. Urutan fase wajib; E0 selesai sebagai audit, E1 belum dimulai. Setiap fase memakai status `NOT_STARTED / IN_PROGRESS / BLOCKED / DONE` dan hanya `DONE` bila Definition of Done terpenuhi dengan bukti eksekusi.

### E0 — Audit and planning (DONE — dokumentasi)

- **Scope:** audit implementasi vs target; gap register; migration risk; update dokumen kontrak.
- **Story IDs:** KCIC-DOC-001.
- **Dependencies:** tidak ada.
- **Acceptance:** tiga dokumen E0 ada dan saling konsisten; dokumen kontrak diperbarui; tidak ada perubahan aplikasi/DB.
- **Database/backend/frontend:** tidak ada.
- **Tests:** verifikasi dokumentasi dan query read-only; tidak ada test aplikasi baru.
- **DoD:** audit PASS; blocker tercatat. **Status: DONE (audit only).**

### E1 — Typed organization hierarchy

- **Scope:** `organization_units.unit_type`; validasi shape COMPANY→DIRECTORATE→DEPARTMENT→DIVISION; admin API/UI untuk tipe; unit lama tetap `NULL`.
- **Story IDs:** KCIC-ORG-002.
- **Dependencies:** E0.
- **Acceptance:** satu COMPANY root; parent-child sesuai tipe; DIVISION tanpa child; tipe tidak dapat diubah setelah dipakai; unit lama tidak dimigrasi otomatis; list/filter berdasarkan tipe berfungsi.
- **Database:** V14 additive nullable + CHECK/index; belum NOT NULL.
- **Backend:** OrganizationService validasi shape; DTO `unitType`; controller filter; audit `ORGANIZATION_UNIT_CREATED/UPDATED`.
- **Frontend:** OrganizationPage menampilkan tipe, form create dengan parent terfilter, error shape.
- **Tests:** unit shape validation; integration create invalid parent/type; regression existing unit flows.
- **DoD:** build/lint/test hijau; migration kosong+upgrade; audit event; dokumentasi.

### E2 — Employee master and placements

- **Scope:** `identity.employees`, `organization.employee_placements`; create/update employee; place/transfer; derived D/D from Biro/Division; history.
- **Story IDs:** KCIC-EMP-001, KCIC-EMP-002.
- **Dependencies:** E1.
- **Acceptance:** NIP/email unique; one active placement enforced by DB; transfer closes prior row transactionally; placement rejects non-DIVISION/inactive; history immutable read; no synthetic data.
- **Database:** V15 tables/indexes/partial unique.
- **Backend:** employee module services + `/admin/employees` endpoints; `employee.view/manage` permission seed; audit events; optimistic locking.
- **Frontend:** Employee list/detail/create/transfer pages; loading/empty/error/conflict; derived D/D read-only.
- **Tests:** uniqueness, concurrent transfer 409, DB constraint, history, derived scope, authorization.
- **DoD:** all gates + evidence; no account linkage yet.

### E3 — Employee–User linkage (1:1)

- **Scope:** `users.employee_id` nullable unique; create account from employee; link/unlink legacy; unlinked worklist; display/email source switch.
- **Story IDs:** KCIC-EMP-USER-001.
- **Dependencies:** E2.
- **Acceptance:** at most one account per employee and vice versa; link rejects double-link; employee termination vs account deactivation explicit; no duplicated employee fields written to User after switch; existing users remain functional unlinked until backfilled.
- **Database:** V16 nullable unique FK; no NOT NULL yet.
- **Backend:** transactional account creation (employee + user + role/placement as applicable); link endpoint; audit; 409 semantics.
- **Frontend:** account panel on employee detail; link dialog; unlinked accounts view; lifecycle actions separated.
- **Tests:** 1:1 DB/API, duplicate, lifecycle separation, password flow, authorization.
- **DoD:** gates + migration evidence; zero data fabrication.

### E4 — Descendant-aware authorization (opt-in)

- **Scope:** `include_descendants` on resource grants and unit role assignments; ancestor chain matching; DENY precedence; UI flag + coverage; legacy default false.
- **Story IDs:** KCIC-AUTHZ-001.
- **Dependencies:** E1 (typed hierarchy), E3 (placement-derived units).
- **Acceptance:** flag only valid on unit principal/scope; old grants exact; descendant ALLOW works on DIVISION under flagged DIRECTORATE/DEPARTMENT/COMPANY; explicit DENY wins; revoke independent; capability snapshot unchanged in shape.
- **Database:** V17 columns + CHECK; no backfill true.
- **Backend:** evaluator expansion (depth ≤4), DTO/validation, audit flag change, performance guard.
- **Frontend:** grant dialog checkbox with warning/coverage; access panel shows flag; no evaluator in frontend.
- **Tests:** matrix allow/deny/inheritance/expiry/revoke/cross-unit; legacy non-widening; IDOR.
- **DoD:** authorization suite green; docs updated; no widening regression.

### E5 — Administration UX and bootstrap alignment

- **Scope:** employee-first navigation; typed org management; placement history; account status; migration worklist; bootstrap admin employee link policy.
- **Story IDs:** KCIC-ADMIN-002.
- **Dependencies:** E1–E4.
- **Acceptance:** admin can complete create-unit → create-employee → place → create-account → grant end-to-end; empty/error/forbidden states; keyboard/a11y; no blue brand usage; sidebar consistent.
- **Database:** none beyond prior.
- **Backend:** supporting read models only if required; no policy duplication.
- **Frontend:** Administration pages/sidebar entries; guards by capability snapshot; conflict handling.
- **Tests:** component state, form validation, permission visibility, Playwright admin journey.
- **DoD:** browser verification with screenshots; gates.

### E6 — Migration cutover and enforcement

- **Scope:** HR data import (verified only), preflight, V18 NOT NULL cutover, legacy write freeze, rollback rehearsal.
- **Story IDs:** KCIC-MIGR-001.
- **Dependencies:** E2–E5; verified HR dataset.
- **Acceptance:** preflight zero human unlinked/invalid; V18 applied; legacy `/units` frozen for linked users; rollback plan rehearsed; no fabricated data.
- **Database:** V18 NOT NULL; optional V19 retirement deferred.
- **Backend:** feature flags for legacy path; startup/ops checks.
- **Frontend:** messaging for frozen legacy actions.
- **Tests:** migration dry-run, constraint failure cases, post-migration authz regression, restore drill.
- **DoD:** signed preflight report; production-like rehearsal evidence.

### E7 — Legacy retirement and cross-module alignment

- **Scope:** retire legacy membership write path; align request routing, distribution recipient snapshot, notifications, audit displays with Employee/placement; documentation updates.
- **Story IDs:** KCIC-ORG-003 (retirement), KCIC-AUDIT-002 (employee events visibility).
- **Dependencies:** E6.
- **Acceptance:** no divergent membership; distribution snapshot semantics unchanged; routing exact UNIT_MANAGER preserved; audit shows employee/placement context; legacy tables read-only or dropped only with approval.
- **Database:** optional V19 (drop/read-only) behind explicit approval.
- **Backend/frontend:** remove dead paths, update labels and filters.
- **Tests:** request/distribution regressions, audit visibility, legacy endpoint denial.
- **DoD:** no orphan references; gates.

### E8 — Security hardening, UAT, release gate

- **Scope:** full security regression for new identity/scope model; UAT scenarios; documentation/handover; final gate.
- **Story IDs:** KCIC-SEC-001, KCIC-UAT-002.
- **Dependencies:** E1–E7.
- **Acceptance:** all §12 security tests in `docs/SECURITY.md` pass; no critical/high open; UAT sign-off; residual risks accepted; zero unlinked human accounts.
- **Database/backend/frontend:** fixes only.
- **Tests:** full suite + E2E + migration + restore.
- **DoD:** final release gate in §20 plus E-series gates; evidence recorded in `docs/PROGRESS.md`.

### Dependency summary

```text
E0 → E1 → E2 → E3 → E4 → E5 → E6 → E7 → E8
              └──────────────┘
E4 requires typed units (E1) and placement-derived units (E3)
E6 requires verified HR data (external) and E2–E5
E7/E8 require E6 cutover
```

Blocking external inputs (not code): verified NIP/email/placement dataset, unit-type mapping for existing units, bootstrap/system-account policy. Until these arrive, E1–E3 can proceed only with empty/non-production data; E6 cannot start.
