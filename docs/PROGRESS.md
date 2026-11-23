# KCIC Docs — Implementation Progress Register

Updated: 2026-11-22
Execution mode: implementation starts at Phase 0 and proceeds through Phase 9. Status vocabulary: `NOT_STARTED`, `IN_PROGRESS`, `BLOCKED`, `DONE` (Definition of Done met; scaffold alone is never `DONE`).

## Phase summary

| Phase | Status | Gate evidence |
|---|---|---|
| 0 — Discovery & baseline | DONE | `docs/PHASE-0-AUDIT.md`; baseline build/test verified |
| 1 — Foundation & design system | DONE | Frontend shell + tokens; backend error contract, Flyway V1–V11, `/actuator/health`, `/api/v1/version` |
| 2 — Auth, users, org, RBAC | DONE | Auth/session/CSRF, account lifecycle, user/role/organization admin; backend tests + browser verification |
| 3 — Master data, workspaces, folders | DONE | Master data + workspace/folder APIs and UI; backend tests + browser verification |
| 4 — Repository, storage, versioning | DONE | Document APIs/UI, filesystem+S3 storage adapter, versioning, preview/download, favorites, owner history; backend tests + browser verification |
| 5 — Resource ACL | DONE | Workspace/folder/document ACL enforcement with deny precedence; access management UI; `AclIntegrationTest` 10 tests; browser-verified allow/deny/revoke |
| 6 — Request workflow | DONE | Request/approval/fulfillment APIs + notifications; `RequestWorkflowIntegrationTest` 4 tests (33 backend total); UI list/create/detail/decide/fulfill; browser-verified end-to-end (create → approve → fulfill → close, ACL grant honored) |
| 7 — Distribution | DONE | Send/track/revoke APIs + UI with provenance-isolated ACL grants; `DistributionIntegrationTest` 4 tests; browser-verified send → read → recipient revoke → distribution revoke with grant-source isolation |
| 8 — Templates | DONE | Template catalog/lifecycle APIs + UI on top of repository versioning; `TemplateIntegrationTest` 2 tests; browser-verified create → publish → usage → edit → archive, historical-version warning download, employee catalog visibility, mobile drawer |
| 9 — Hardening & handover | DONE | Code splitting + lazy routes; audit trail search API/UI (`AuditIntegrationTest`); 40 backend tests; cross-role UAT incl. AUDITOR; restore drill; `SECURITY.md`/`DEPLOYMENT.md`/`UAT.md`/`USER_GUIDE.md`/`ADMIN_GUIDE.md` |

## E-series phase summary (Organization & Identity Enhancement)

| Phase | Status | Gate evidence |
|---|---|---|
| E0 — Audit and planning | DONE (documentation only) | `docs/organization-identity/PHASE_E0_AUDIT.md`, `ARCHITECTURE_GAP_ANALYSIS.md`, `MIGRATION_RISK_REGISTER.md`; PRD/ARCHITECTURE/SCHEMA/API/SECURITY/DECISIONS/IMPLEMENTATION_PLAN/MIGRATION_PLAN updated; no code or DB change |
| E1 — Typed organization hierarchy | DONE | V14 `unit_type` + shape validation (COMPANY root, DIRECTORATE→COMPANY, DEPARTMENT→DIRECTORATE, DIVISION→DEPARTMENT); legacy units stay untyped; `OrganizationHierarchyIntegrationTest` 4 tests; admin UI type selector/labels ("Biro/Division") browser-verified. Official unit-type mapping still blocked (B-2) |
| E2 — Employee master and placements | DONE | V15 `identity.employees` + `organization.employee_placements`; NIP/email case-insensitive uniqueness; DIVISION-only active placement; partial unique one-active-placement; transactional transfer with stale-request conflict; `EmployeeIntegrationTest` 8 tests; admin employee list/detail/create/transfer UI browser-verified (no HR data created). Actual HR master data still blocked (B-1) |
| E3 — Employee–User linkage (1:1) | DONE | V16 `identity.users.employee_id` + partial unique index; account creation and legacy linking endpoints; linked-account card and dialogs; `EmployeeAccountIntegrationTest` 8 tests; dev data limitation documented |
| E4 — Descendant-aware authorization (opt-in) | DONE | V17 `include_descendants` flags + unit-only CHECK; placement-aware unit membership; recursive ancestor matching in role/ACL evaluation with DENY precedence; `DescendantScopeIntegrationTest` 5 tests; ACL grant and role assignment UI opt-in with coverage badges |
| E5 — Administration UX and bootstrap alignment | DONE | Employee list `linked` filter + `hasAccount` badge; read-only migration worklist page (`/admin/migration`) with stats, empty/error/forbidden states; navigation + route guarded by `employee.view`; golden path browser-verified (typed hierarchy → employee/placement → account → document grant); frontend gates pass |
| E6 — Migration cutover and enforcement | BLOCKED (tooling complete) | Preflight E6-01…E6-09 (incl. employee lifecycle and master-data quality), HR CSV validator, guarded unit-mapping applier + proposal, V18 candidate rehearsed PASS on throwaway DB; dev gate BLOCKED (5 unlinked accounts, 2 untyped units; UAT-CO fixture blocks mapping). Requires verified HR data, org-owner mapping sign-off; no account exceptions (DEC-068) |
| E7 — Legacy retirement and cross-module alignment | DONE | Legacy unit-assignment create frozen for Employee-linked accounts (409); end-assignment retained for narrowing; unit members, user units, `/auth/me`, and distribution resolve active placements; distribution snapshot regression test; full backend suite 71/71 |
| E8 — Security hardening and UAT | DONE (automated controls); UAT BLOCKED | Employment status gates login, session, grants, and distribution; operational check script; IDOR and independent-revoke coverage; backend 84/84 green; human E-series UAT still requires verified HR data (E6) |

## Story backlog

| Story | Phase | Requirement refs | Module owner | Status | Evidence |
|---|---|---|---|---|---|
| KCIC-FOUND-001 Repository audit and runnable baseline | 0 | IMPLEMENTATION_PLAN §4 | infra | DONE | docs/PHASE-0-AUDIT.md |
| KCIC-FOUND-002 Frontend shell, tokens, responsive left sidebar | 1 | DESIGN_SYSTEM §4–6 | frontend/app | DONE | `frontend/src/app` — maroon sidebar, drawer <768px, skip link, tokens in `globals.css` |
| KCIC-FOUND-003 Backend foundation, error contract, migrations, health/version | 1 | ARCHITECTURE §11/§13.4 | backend/shared | DONE | Flyway V1–V11 applied; JSON error writer with `code`/`requestId`; `/api/v1/version` |
| KCIC-AUTH-001 Local user model and secure login/session | 2 | AUTH-01/02, ARCHITECTURE §7 | identity | DONE | BCrypt(12), lockout, server-side sessions, forced first-password change |
| KCIC-AUTH-002 `/auth/me`, logout, CSRF, account status | 2 | AUTH-02/05, API_CONVENTIONS §4 | identity | DONE | Cookie CSRF + `X-XSRF-TOKEN`; `AccountStateFilter`; 401/403 JSON; 403 verified from employee session |
| KCIC-AUTH-003 Role/permission catalog and evaluator | 2 | USER-03/05, ACL-01 | authorization | DONE | 6 system roles / 38 permissions (V11); evaluator + capability snapshot |
| KCIC-ORG-001 Organization units and user assignments | 2 | USER-04 | organization | DONE | Unit CRUD, hierarchy, memberships with PRIMARY/SECONDARY |
| KCIC-ADMIN-001 User/role/organization administration | 2 | USER-01/02, USER-03/05 | identity+authorization+organization | DONE | UI + API for users, roles, permissions, units, assignments; browser-verified |
| KCIC-MASTER-001 Categories/types/classifications/tags/file policy | 3 | MASTER-01..04 | masterdata | DONE | CRUD + deactivate for categories/types/classifications/tags; file policy edit; admin UI at `/admin/master-data` |
| KCIC-REPO-001 Workspace and folder foundations | 3 | REPO-01/02 | repository | DONE | Workspace + folder APIs, status lifecycle, hierarchy guards; UI `/workspaces`, `/workspaces/:id`; 5/5 `RepositoryIntegrationTest` |
| KCIC-REPO-002 Document metadata and search | 4 | REPO-03/06/08, REPO-11 | repository | DONE | `GET/POST/PUT /api/v1/documents`, filters/paging/favorites, owner history; `DocumentsPage`/`DocumentDetailPage` |
| KCIC-REPO-003 Private file upload and storage adapter | 4 | REPO-04, REPO-05 | repository/storage | DONE | `StorageAdapter` (MinIO/S3 + filesystem dev), SHA-256, randomized object keys, magic-byte/file-policy validation before persist |
| KCIC-REPO-004 Versioning, preview/download, archive | 4 | REPO-07/09/10 | repository/version | DONE | Version upload/promote, https-only external links, inline/attachment streaming, archive/restore with audit; browser-verified |
| KCIC-ACL-001 Resource permission and inheritance | 5 | ACL-02/03 | authorization | DONE | ACL CRUD + history per workspace/folder/document, deny-precedence evaluator, FolderGrantResolver ancestry; access panel UI; 10 ACL tests; browser-verified |
| KCIC-REQ-001 Request submit/review/fulfill | 6 | REQ-01..06, NOTIF-02 | workflow | DONE | `/api/v1/requests` create/list/detail/review/approve/reject/fulfill/cancel/close + multipart `/fulfill/upload`; routing, transition table, ACL grant on fulfillment; requests UI + notification bell/page; 4 workflow tests; browser-verified full lifecycle |
| KCIC-DIST-001 Distribution send/receive/revoke | 7 | DIST-01..05 | distribution | DONE | `/api/v1/distributions` create/list/detail/revoke/recipient-revoke; snapshot recipients, provenance-linked grants, lazy expiry; `DistributionIntegrationTest` 4 tests; UI pages + browser-verified |
| KCIC-TEMP-001 Template publish/catalog | 8 | TEMPLATE-01..04 | template | DONE | `/api/v1/templates` list/detail/create/update/publish/archive/usage; published-version reference via composite FK (no binary duplication), draft/archive governance; `TemplateIntegrationTest` 2 tests; catalog/detail UI incl. historical-version warning download + browser-verified |
| KCIC-HARDEN-001 Security, restore drill, regression, UAT | 9 | IMPLEMENTATION_PLAN §13 | all | DONE | Audit trail search API/UI + `AuditIntegrationTest`; 40/40 backend tests, frontend gates green; cross-role UAT (employee/auditor/admin) incl. forbidden paths; code-split routes; DB+storage restore drill; `docs/SECURITY.md`, `DEPLOYMENT.md`, `UAT.md`, `USER_GUIDE.md`, `ADMIN_GUIDE.md` |
| KCIC-DOC-001 E0 audit and E-series planning | E0 | USER-06/07/08, ACL-04 | docs | DONE (docs only) | Three E0 deliverables + contract docs updated; no code changes |
| KCIC-ORG-002 Typed organization hierarchy | E1 | USER-06 | organization | DONE | V14 `unit_type`; typed shape validation and type-change guards; legacy units remain untyped; 4 hierarchy tests; admin UI + browser verification |
| KCIC-EMP-001 Employee master data | E2 | USER-07 | identity | DONE | V15 employees table, `employee.view/manage`, admin API + UI; 8 integration tests |
| KCIC-EMP-002 Employee placements and history | E2 | USER-07 | organization | DONE | V15 placements, DIVISION validation, one-active DB constraint, transactional transfer, history UI |
| KCIC-EMP-USER-001 One-to-one Employee–User linkage | E3 | USER-07/08 | identity | DONE | V16 `employee_id` + partial unique index, create-account/link endpoints, linked-account UI, 8 integration tests |
| KCIC-AUTHZ-001 Descendant-aware ACL (opt-in) | E4 | ACL-04 | authorization | DONE | `include_descendants` on grants/assignments (default false, unit-only CHECK); placement-derived membership; ancestor matching only when flag true; DENY wins; 5 integration tests; UI opt-in |
| KCIC-ADMIN-002 Employee-first administration UX | E5 | USER-08 | frontend/admin | DONE | Account coverage filter and badge on the employee list; `/admin/migration` worklist (stats, unlinked employees, unlinked accounts) with loading/empty/error/forbidden states; sidebar entry under Administration; golden path verified end-to-end in the browser |
| KCIC-MIGR-001 HR import and V18 cutover | E6 | USER-07, IMPLEMENTATION_PLAN §21 | infra/DB | BLOCKED | Preflight tooling delivered and run; gate fails on external inputs (verified HR data, unit mapping, service-account policy); V18 not written/applied |
| KCIC-ORG-003 Legacy membership retirement | E7 | USER-04/06 | organization | DONE | Legacy create frozen for linked accounts with 409; end-assignment preserved for narrowing; placement-aligned unit reads/distribution; 4 new integration tests + distribution snapshot regression; legacy table retained (V19 approval-only) |
| KCIC-SEC-001/KCIC-UAT-002 E-series security and UAT | E8 | ACL-04, USER-07/08 | security/QA | DONE (automated) / UAT BLOCKED | Security tests §12 implemented and green (84/84); operational check committed; human UAT pending verified HR data and E6 cutover |

## Evidence log

Append command outputs and verification notes here as phases complete. Keep entries factual: command, timestamp, result.

### 2026-11-22 — Phase 0 / Phase 1

- `docs/PHASE-0-AUDIT.md` records repository, toolchain, and environment facts.
- Backend: Spring Boot 3.5.16, Java 21 release on JDK 23, Maven 3.9.9 (`tools/`), PostgreSQL 16 container `kcic-postgres` on host port 5433, Flyway V1–V11 with `ddl-auto: validate`.
- Frontend scaffold: Vite 8 + React 19 + TypeScript strict + Tailwind 4 `@theme` tokens + React Router 7 + TanStack Query.
- Design: maroon `#8B1A1A` sidebar with gold `#D6B76E` active edge, 264/76 px desktop widths, native `<dialog>` drawer under 768 px (verified at 390×844).

### 2026-11-22 — Phase 2 (identity, organization, RBAC)

Backend behavior implemented and integration-tested:

- Local login, logout, `/auth/me`, forced password change, CSRF bootstrap, lockout, account status gates, session invalidation on deactivation/password reset.
- User administration: search with optional status + keyword, create/update, status change, reset password, unlock.
- Organization units: hierarchy, cycle guard, membership add/end; role catalog, permission mapping, scoped role assignment/revocation.
- Bug found by browser verification and fixed: `GET /api/v1/admin/users` returned 500 (`function lower(bytea) does not exist`) when the nullable keyword bound as `bytea`. Fix: normalized non-null keyword plus `:keyword = ''` JPQL predicate, split status/all queries; regression test `adminUserListSupportsOptionalStatusAndKeywordFilters`.

Verification commands (2026-11-22):

| Command | Result |
|---|---|
| `npm run typecheck` (frontend) | PASS — 0 errors |
| `npm run lint` (frontend) | PASS — 0 errors, 5 non-blocking warnings (react-refresh exports, RHF `watch`) |
| `npm run test` (frontend) | PASS — 3 files, 9 tests |
| `npm run build` (frontend) | PASS — 898 ms; 550 kB JS chunk warning (no code-splitting yet) |
| `mvn -f backend/pom.xml test` | PASS — `AuthorizationIntegrationTest` 4/4, `AuthenticationIntegrationTest` 5/5 (0 failures/errors) |

Browser acceptance (Playwright, admin + employee1):

- Login page, admin dashboard, user list/detail, create-user validation, reset-password dialog, role permission editor (system role locked), organization unit dialog, mobile drawer at 390 px.
- Employee session: sidebar shows only Dashboard; `/admin/users` renders Forbidden state; direct `fetch('/api/v1/admin/users')` returns `403 ACTION_FORBIDDEN` with `requestId`.
- Responsive fixes applied: hide timeline columns under `md`/`lg`, collapse breadcrumb root under `sm`, keep actions `whitespace-nowrap`.
- Favicon added (`frontend/public/favicon.svg`), removing the only recurring 404 from the console.

### 2026-11-22 — Phase 3 (master data, workspaces, folders)

Backend behavior implemented and integration-tested (`masterdata` + `repository` modules):

- Master data: categories, document types, classifications, tags with list/create/update/deactivate; file policy read/update with extension, MIME, and size rules enforced server-side. Admin-only (`master_data.manage`); lookup lists exposed to authenticated users.
- Workspaces: list/create/update/status change; ACTIVE/INACTIVE/ARCHIVED lifecycle with transition guard (invalid transition → 409 `CONFLICT`, same status → no-op); create allowed via global `workspace.manage` or unit-scoped `allowsUnitCapability` on the chosen owner unit; archive hides workspace from default listing and makes folders read-only.
- Folders: create/rename/move/archive under ACTIVE parents only; case-insensitive sibling-name uniqueness; cycle guard (parent cannot be self/descendant); archive blocked while active children exist; `parentVersion` optimistic locking surfaces 409 on stale edits.

Bug found by browser verification and fixed:

- File-policy allowlist textarea split `pdf, p df` into `pdf, p, df` (the old splitter treated spaces as separators), silently saving an altered policy. Fix in `FilePolicyPanel.splitList`: split on commas/newlines only so invalid tokens reach backend validation; field error keys are normalized (`allowedExtensions[1]` → `allowedExtensions`) so messages attach to the field. Policy restored to `pdf, doc, docx, xls, xlsx, ppt, pptx, txt, csv`.

Verification commands (2026-11-22):

| Command | Result |
|---|---|
| `npm run typecheck` (frontend) | PASS — 0 errors |
| `npm run lint` (frontend) | PASS — 0 errors, 5 warnings (unchanged react-refresh/RHF warnings) |
| `npm run test` (frontend) | PASS — 3 files, 10 tests |
| `npm run build` (frontend) | PASS — 342 ms; 550 kB JS chunk warning (code-splitting still deferred) |
| `mvn -f backend/pom.xml test` | PASS — 14 tests, 0 failures/errors (`AuthorizationIntegrationTest` 4, `AuthenticationIntegrationTest` 5, `RepositoryIntegrationTest` 5) |

Browser acceptance (Playwright, admin + employee1):

- Master data page (`/admin/master-data`): category create + deactivate, file policy edit, validation errors shown inline.
- Workspaces: create workspace, open detail, create sub-folder, duplicate sibling name rejected with field error, parent options exclude self/descendants, folder archive conflict (active children) and success, archived folders hidden until "Tampilkan arsip".
- Workspace status: archive hides from default list and shows under archived filter; reactivation restores; archived workspace folder actions disabled.
- Dialog defects fixed during acceptance: stale server error banner leaking between dialogs (open/close helpers clear `actionError` and form errors), archived workspace status select falling back to "Aktif" (native `<dialog>` keeps children mounted, so form content now remounts per open and ARCHIVED option always renders).
- Employee session: sidebar limited to permitted entries; `/admin/master-data` and `/workspaces` mutations return 403 `ACTION_FORBIDDEN`; workspace list shows scope-appropriate empty state; layout verified at 390×844.

### 2026-11-22 — Phase 4 (document repository, storage, versioning)

Backend behavior implemented and integration-tested (`repository` module):

- Documents: create with metadata/tags/optional folder, update metadata, activate/archive/restore with status guards, catalog search (workspace, keyword, category/type/classification/tag/owner/status, sort, paging), favorites list/toggle, per-document activity projection over business audit events.
- Storage: `StorageAdapter` port with S3-compatible (MinIO) and filesystem providers; dev run uses filesystem (`KCIC_STORAGE_PROVIDER=filesystem`, root `.local/storage-dev`); object keys random, original filename stored only as metadata; SHA-256 checksum persisted; compensating delete on metadata failure.
- Uploads: multipart version upload validated against active file policy (extension, declared MIME, size) plus magic-byte sniffing before persistence; object stored first, then metadata; upload returns 201.
- Versioning: new version becomes CURRENT and prior CURRENT becomes SUPERSEDED; promote existing version applies the same transition; version binaries immutable; external Office links recorded as HTTPS-only EXTERNAL_LINK versions with normalized host.
- Content access: `GET .../versions/{id}/content?download=` streams after authorization with `Content-Disposition` inline/attachment, `Cache-Control: private, no-store`, `X-Content-Type-Options: nosniff`; preview and download emit `DOCUMENT_PREVIEWED`/`DOCUMENT_DOWNLOADED` audit events.
- Ownership: owner may be a user or an organization unit; owner changes require a reason (≥5 chars), `document.update_metadata` capability, close the open history row and open a new one, and emit `DOCUMENT_OWNER_CHANGED`; unit-only owners have no owner user id.
- Archived documents reject new version uploads and external links on the backend; metadata edits remain allowed for `canEdit` actors.

Bugs found by browser verification and fixed:

- `PUT /api/v1/documents/{id}` returned 500 when changing ownership to an organization unit: `DocumentResponseMapper` performed `Map.of()` lookups with a null `ownerUserId`, and immutable map lookups reject null keys. Fix: null-safe `nameFor(...)` helper used for owner user/unit and history name resolution; regression assertions added to `ownerChangeRequiresReasonAndKeepsHistory` (unit-only owner detail + catalog).
- Archived documents still showed "Unggah versi"/"Tautan eksternal" actions although the backend rejects them; both are now hidden when `status === 'ARCHIVED'` (header and empty-state actions).
- Default catalog empty state implied no documents exist; text now explains archived documents are hidden and how to include them.
- Mobile detail page overflowed horizontally at 390 px (594 px action row, 477 px content column): `PageHeader` action container now wraps instead of `shrink-0`, and the detail grid content column is `min-w-0`; document SHA-256 lines wrap inside the card.

Verification commands (2026-11-22):

| Command | Result |
|---|---|
| `npm run typecheck` (frontend) | PASS — 0 errors |
| `npm run lint` (frontend) | PASS — 0 errors, 6 warnings (react-refresh exports, RHF `watch`; no new warnings) |
| `npm run test` (frontend) | PASS — 3 files, 11 tests |
| `npm run build` (frontend) | PASS — 327 ms; 629.88 kB JS chunk warning (code-splitting deferred to Phase 9) |
| `mvn -f backend/pom.xml test` | PASS — 19 tests, 0 failures/errors (`Authorization` 4, `Authentication` 5, `DocumentIntegrationTest` 5, `Repository` 5) |

Browser acceptance (Playwright, admin):

- Create document → upload PDF v1.0 → favorite → inline preview (valid PDF rendered) → upload v1.1 (v1.0 `SUPERSEDED`, promote action visible) → activate → catalog `ACTIVE` with keyword filter → favorites tab.
- Download v1.1 delivered the original filename and emitted `Document Downloaded`.
- Edit metadata: owner switched from user to unit; save without reason blocked by inline validation; with reason saved, owner history closed the old entry and opened the unit entry, activity shows `Document Owner Changed` + `Document Metadata Updated`.
- Archive → banner + only restore action; default catalog hides archived; include-archived shows it; restore returns to `ACTIVE`.
- External link with `http://` rejected client-side with HTTPS-only message; activity trail covers created/uploaded/previewed/downloaded/activated/archived/restored/owner-changed.
- Responsive: 390×844 drawer navigation, catalog filters/rows/pagination, and detail versions/owner history fit without horizontal scroll.

Known limitations recorded for later phases:

- Catalog visibility is evaluated in the application layer after loading candidate rows, then paginated in memory (DEC-029); ACL SQL pushdown is deferred until Phase 5 establishes resource grants.
- MinIO remains the configured production-like provider; the dev environment runs the filesystem adapter. MinIO integration against a live server has not been executed in this environment.

### 2026-11-22 — Phase 5 (resource ACL)

Backend behavior implemented and integration-tested:

- `authorization.resource_permissions` grants (USER/ROLE/ORGANIZATION_UNIT principals, ALLOW/DENY, optional expiry, revoke with history) evaluated by `AuthorizationService.aclDecision`: explicit DENY wins over every allow; revoking one source never removes independent grants.
- ACL management API under `/api/v1/workspaces/{id}/access`, `/api/v1/workspaces/{id}/folders/{folderId}/access`, `/api/v1/documents/{id}/access`: summary (active + history), create (201), revoke (204). Manage capability: workspace → `workspace.manage`; folder → `folder.update` or `workspace.manage`; document → `document.manage_access`.
- Grant validation: only allowlisted permissions applicable to the target type (400 with `fieldErrors`), future-only expiry, duplicate active grant → 409; `document.manage_access` is not grantable through the API.
- Document visibility (`DocumentService` catalog, favorites, detail, preview, download, version operations) resolves document grants + folder ancestry grants + workspace grants; folder grants inherit along the real parent chain (`FolderGrantResolver`, batched per list page).
- Workspace visibility (`WorkspaceService.list/get`) and folder visibility (`FolderService.list`) now apply the same deny-precedence model: an explicit DENY hides the resource even for global assignments, an explicit ALLOW exposes a workspace/folder outside the actor's unit scope, otherwise role + unit scope decides. This closed a gap where a granted out-of-unit employee still received 403 on the workspace page.
- `NoResourceFoundException` maps to `404 RESOURCE_NOT_FOUND` instead of a logged 500.

Frontend:

- `AccessPanel` (workspace detail, folder dialog, document "Kontrol akses") lists active grants, collapsible history, principal/permission/effect/source/expiry metadata, and revoke with confirmation; `GrantDialog` searches the access directory, filters permission options by target type, and maps backend field errors.
- Capability snapshots drive UI only (`canManageAccess`); backend re-authorizes every request.

Bugs found by browser verification and fixed:

- Out-of-unit employee was forbidden on `/workspaces/{id}` despite an active `workspace.view` ALLOW because workspace/folder services ignored resource ACL; fixed with deny-first per-resource decisions (above).
- Workspace detail page overflowed horizontally at 390 px because the folder meta line used `whitespace-nowrap` truncation; switched to a two-line clamp; `scrollWidth === clientWidth` at 390×844.

Verification commands (2026-11-22):

| Command | Result |
|---|---|
| `npm run typecheck` (frontend) | PASS — 0 errors |
| `npm run lint` (frontend) | PASS — 0 errors, 7 warnings (react-refresh exports, RHF `watch`; no new warnings) |
| `npm run test` (frontend) | PASS — 3 files, 11 tests |
| `npm run build` (frontend) | PASS — 642.22 kB JS chunk warning (code-splitting deferred to Phase 9) |
| `mvn -f backend/pom.xml test` | PASS — 29 tests, 0 failures/errors (`AclIntegrationTest` 10, `Authorization` 4, `Authentication` 5, `Document` 5, `Repository` 5) |
| `mvn -f backend/pom.xml -DskipTests compile` | PASS |

Browser acceptance (Playwright):

- Admin: workspace ACL panel shows grant, folder "Akses" dialog records `folder.view`, document "Kontrol akses" shows concurrent DENY + ALLOW rows and audit entries (`Access Granted`, `Access Denied Grant Created`).
- Employee1 (Head Office) with `workspace.view` ALLOW on an Operations workspace: workspace now appears in `/workspaces` and opens; granted folder "Kebijakan" is listed; the workspace-root SOP document returns the forbidden state while the document-level DENY is active, proving DENY precedence over inherited workspace/folder grants.
- Admin revokes the document DENY (history keeps it as revoked); Employee1 can then open the SOP detail and sees it in the catalog; the "Kontrol akses" panel is absent for Employee1 (no `document.manage_access`).
- 390×844: workspace detail and document detail have no horizontal overflow.

Known limitations recorded for later phases:

- Folder metadata mutations (create/update/archive) remain role/scope capability checks; ACL governs visibility only in Phase 5. A folder-scoped manage grant is not offered by the UI/allowlist.
- Workspace/folder visibility filtering runs per candidate row (batched ACL lookup) in the application layer; SQL-level ACL pushdown remains deferred (DEC-029).
- Deny precedence intentionally hides a resource from global administrators as well, matching document behavior; administrators can still manage its ACL through the API/direct link.

### 2026-11-22 — Phase 6 (request workflow and notifications)

Backend behavior implemented and integration-tested (`workflow` + `notification` modules, Flyway V12):

- Request creation (ACCESS, DOCUMENT_DELIVERY, TEMPLATE) with per-type validation (ACCESS requires a target document), auto-generated `REQ-YYYY-NNNNN` codes, requester/unit capture, and immediate submission with a `SUBMITTED` history entry.
- Routing (`RequestRoutingService`): target document owner user → manager of the owning/target unit → global `DOCUMENT_ADMIN` → unit-scoped `DOCUMENT_ADMIN` → `SYSTEM_ADMIN`; no eligible approver returns `409 CONFLICT` instead of creating an orphan request.
- Transition table enforced server-side (`DRAFT→SUBMITTED/CANCELLED`, `SUBMITTED→UNDER_REVIEW/APPROVED/REJECTED/CANCELLED`, `UNDER_REVIEW→APPROVED/REJECTED/CANCELLED`, `APPROVED→IN_PROGRESS/FULFILLED/CANCELLED`, `IN_PROGRESS→FULFILLED/CANCELLED`, `FULFILLED/REJECTED→CLOSED`); invalid or repeated transitions return 409.
- Review/approve/reject restricted to the assigned approver; rejection requires a reason. Cancel allowed for requester/assignee (or request-wide viewers) while the request is open; close allowed for requester/assignee after `FULFILLED` or `REJECTED`.
- Fulfillment only in `APPROVED`/`IN_PROGRESS`: `ACCESS_GRANTED` creates USER ACL allows for `document.view/preview/download` with `GrantSource.REQUEST` (skips permissions already allowed), `DOCUMENT_LINKED` validates each document and snapshots the current version (max 20), `EXTERNAL_LINK_REGISTERED` accepts HTTPS URLs only and records them without claiming provider-side permission changes. First fulfillment walks `APPROVED → IN_PROGRESS → FULFILLED` in one transaction and completes the pending approval row.
- File fulfillment uses `POST /api/v1/requests/{id}/fulfill/upload` (multipart documentId/file/note/versionLabel) through the existing document-version pipeline (file policy, magic bytes, private storage); the JSON `POST /{id}/fulfill` rejects `DOCUMENT_UPLOADED` with 400 so uploads cannot bypass validation.
- Notifications are per-recipient rows created in the same transaction as each event (submitted → approver, approved/rejected/fulfilled/cancelled → requester), with list, unread count, mark-read, and mark-all-read APIs restricted to the recipient. Business audit records `REQUEST_SUBMITTED`, `REQUEST_APPROVED`, `REQUEST_REJECTED`, `REQUEST_FULFILLED`, `REQUEST_CANCELLED`, `REQUEST_CLOSED`.

Frontend:

- `/requests` list with "Request saya"/"Tugas saya" tabs, keyword/status/type filters, server-side paging, status badges, overdue indicator; create dialog with document picker (searches the catalog, preserves selections), unit and needed-by date, inline field errors.
- `/requests/:id` detail with summary, reason/description, history timeline, approvals and fulfillments; actions are driven by backend capability flags plus status (`Mulai review`, `Setujui`/`Tolak` with optional/required notes, `Penuhi request`, `Tutup`, `Batalkan`); fulfill dialog covers ACCESS_GRANTED / DOCUMENT_LINKED / EXTERNAL_LINK_REGISTERED / DOCUMENT_UPLOADED with upload progress.
- Notification bell (unread count, 60 s refresh) and `/notifications` page (unread filter, mark read, mark all read, open target); sidebar "Requests" entry gated by OR of `request.view_own|view_assigned|approve|fulfill`, and `/notifications` labeled in the breadcrumb.

Bugs found during verification and fixed:

- Sidebar "Requests" entry was visible to users with only `document.view` (caught by the navigation unit test); the item now requires at least one request capability.
- Document picker chips vanished when the search keyword changed (chosen rows were derived from the current result set); labels are now kept per selected id.
- ACCESS requests showed the document picker as optional; it now renders as required and blocks submission without a document.
- Fulfillment upload target was typed `string | undefined`; guarded explicitly before calling the upload mutation (TS2345).
- Dialog reset effects tripped `react-hooks/set-state-in-effect`; state now resets via per-open mounting/prop-driven rendering instead of effects.

Verification commands (2026-11-22):

| Command | Result |
|---|---|
| `npm run typecheck` (frontend) | PASS — 0 errors |
| `npm run lint` (frontend) | PASS — 0 errors, 7 warnings (react-refresh exports, RHF `watch`; no new warning categories) |
| `npm run test` (frontend) | PASS — 3 files, 11 tests |
| `npm run build` (frontend) | PASS — 678.12 kB JS chunk warning (code-splitting deferred to Phase 9) |
| `mvn -f backend/pom.xml test` | PASS — 33 tests, 0 failures/errors (`Authorization` 4, `Authentication` 5, `Acl` 10, `Document` 5, `Repository` 5, `RequestWorkflow` 4) |

Browser acceptance (Playwright, admin + employee1):

- Employee1 created ACCESS request `REQ-2026-00001` for the Operations SOP document (unit auto-derived); admin received the "terkirim" notification (bell badge 1) and saw the request under "Tugas saya".
- Admin: `Mulai review` → `UNDER_REVIEW`; `Setujui` with a note → `APPROVED`, approval row shows decision and reason, history records both transitions.
- Admin fulfilled with "Akses diberikan": request became `FULFILLED` with `In Progress`/`Fulfilled` history entries and a fulfillment card showing the document link and note; requester received a "dipenuhi" notification.
- Employee1: `/notifications` listed 3 unread rows; single mark-read dropped the badge to 2, "Buka" marked read and navigated to the request (2 → 1), "Tandai semua dibaca" cleared the badge and disabled the button.
- Employee1 opened the SOP document from the fulfillment card and the request link with preview/download available, proving the REQUEST-sourced ACL grant is effective; then closed the request (`FULFILLED → CLOSED`, note recorded, actions disappear).
- 390×844: requests list, detail (full timeline), and dashboard drawer render without horizontal overflow (`documentElement.scrollWidth === 390`); drawer keeps the maroon sidebar with the gold active edge and hides administration groups for employee1.

Known limitations recorded for later phases:

- Request attachments remain deferred (DEC-034, superseding DEC-011); request evidence is carried by description, fulfillment notes, and linked/uploaded documents.
- File fulfillment reuses the document-version upload pipeline, so the fulfiller must hold `document.upload_version` on that document in addition to `request.fulfill`; the upload dialog surfaces the resulting 403/409 instead of masking it.
- No bulk fulfillment or delegate/reassign flow in the pilot; approval is single-stage by the routed user.

### 2026-11-22 — Phase 7 (distribution and recipient access)

Backend behavior implemented and integration-tested (`distribution` module, Flyway V8 + V13 for the `DIST-YYYY-NNNNN` code sequence):

- `POST /api/v1/distributions` (capability `distribution.create`) validates title, message, future-only `expiresAt`, 1..20 documents with access level `VIEW|PREVIEW|DOWNLOAD`, and recipient users/units. A sender can only delegate what it itself holds at that level and only for documents where it holds `document.share`; each grant is created as a `USER` ACL entry with `GrantSource.DISTRIBUTION` plus a `distribution_access_grants` provenance row.
- Unit recipients are expanded to the unit's active members at send time (inactive accounts are skipped), so later unit changes do not silently add recipients.
- Sending creates the distribution as `SENT` (no draft phase), writes a `SENT` event per recipient, sends a `DISTRIBUTION_RECEIVED` in-app notification, and records `DISTRIBUTION_SENT` audit.
- List (`GET /api/v1/distributions?box=sent|received&status&keyword&page&size`) is capability-gated (`distribution.view_own` / `distribution.view_received`), server-paginated and keyword/status filterable. Detail is visible only to the sender, a recipient, or a principal with global `distribution.revoke`; non-participants receive 404.
- Recipient statuses `SENT → READ → REVOKED|EXPIRED`; the first detail open by a recipient marks `READ` and appends a `READ` event. Senders see `readCount`. Recipient revoke and distribution-wide revoke are allowed for the sender or a global `distribution.revoke` holder while the distribution is active; revoke only touches grants linked to that distribution (provenance isolation), so grants from requests, manual grants, or other distributions survive.
- Expiry is lazy: list/detail/revoke run `expireDue` inside the transaction, flipping due distributions/recipients to `EXPIRED` and appending `EXPIRED` events. `canRevoke` is returned only in the sender box while active recipients remain.
- Access directory read authorization widened to `document.manage_access|workspace.manage|folder.update|distribution.create`; it still exposes only active user names/usernames (max 20), active units, and active roles.

Frontend:

- Sidebar group "Distribution" → `/distributions` (visible with `distribution.view_own` or `distribution.view_received`). The page has "Diterima"/"Terkirim" tabs (default received when available), keyword/status filters, server pagination, status/recipient-status badges, read count for senders, and empty/forbidden states.
- "Distribusi baru" opens a dialog with title, message, expiry, a document picker (min 2 chars, per-document access level), and user/unit recipient pickers backed by the access directory; client-side validation mirrors backend field names and maps `recipientOrganizationUnitIds` errors onto the shared recipient field.
- `/distributions/:id` shows summary, message, documents (access-level badges, link only while `canOpen`), chronological history, and moderation controls (revoke distribution, per-recipient revoke) for senders/`distribution.revoke`. Recipients see "Status Anda" plus an "Akses Anda" card whose copy now reflects `REVOKED`/`EXPIRED` instead of claiming active access.
- Notifications route `DISTRIBUTION` targets to the distribution detail.
- After the production build and typecheck, a revoked-recipient copy defect was found in browser verification and fixed (`Akses Anda` card), then re-verified live.

Verification commands (2026-11-22):

| Command | Result |
|---|---|
| `npm run typecheck` (frontend) | PASS — 0 errors |
| `npm run lint` (frontend) | PASS — 0 errors, 7 warnings (pre-existing categories only) |
| `npm run test` (frontend) | PASS — 3 files, 11 tests |
| `npm run build` (frontend) | PASS — 705.89 kB JS chunk warning (code-splitting deferred to Phase 9) |
| `mvn -f backend/pom.xml test` | PASS — 37 tests, 0 failures/errors (`Authorization` 4, `Authentication` 5, `Acl` 10, `Document` 5, `Repository` 5, `RequestWorkflow` 4, `Distribution` 4) |

Browser acceptance (Playwright, admin + employee1 + a fresh employee3):

- Admin created `DIST-2026-00002` (SOP-OPS-001, DOWNLOAD level, recipient Employee Three) and was redirected to the detail page showing `SENT`, 1 recipient, SENT history and per-recipient revoke.
- Employee3 (new local account, Head Office/Employee, forced password change completed) opened the distribution detail, saw it under "Diterima", and opened the SOP document through the distribution grant.
- Admin revoked the recipient grant; detail flipped to `REVOKED` with a "Status akhir" card and the revoke control disappeared.
- Employee3 re-opened the document URL and received the "Akses tidak diizinkan" forbidden state; the recipient detail shows `Status Anda: Dicabut` and the corrected "Akses Anda" copy.
- Employee1 (who also holds independent MANUAL/REQUEST grants on the same document) still opened the document after the distribution revocation, confirming revoke isolation across grant sources at the API and DB level (`resource_permissions` shows DISTRIBUTION rows revoked while MANUAL/REQUEST rows remain active).
- 390×844: `/distributions` switches to the drawer navigation ("Buka navigasi") and the list/detail render without horizontal overflow.

Known limitations recorded:

- No draft/scheduling, CC/bulk teams, or resend in the pilot; a distribution is sent immediately and can only be revoked.
- Expiry is lazy (evaluated on read/mutation), so no background job marks rows exactly at the deadline.
- Distribution revoke does not invalidate an already open document page; the next API call is re-authorized (DEC-012/DEC-024).

### 2026-11-22 — Phase 8 (template catalog and governance)

Backend behavior implemented and integration-tested (`template` module, Flyway V9):

- `GET /api/v1/templates?keyword&categoryId&ownerUserId&organizationUnitId&status&page&size` (capability `template.view`) returns a server-paginated catalog with `totalItems/totalPages`. Visibility is status- and document-scope-aware: ordinary users see only `PUBLISHED` templates whose source document they may view; `template.publish|archive` holders see all statuses; `template.create` holders additionally see `DRAFT/ARCHIVED` templates for documents they can view. Filters cover name/code keyword, category, document owner, owner unit, and status.
- `GET /api/v1/templates/{id}` returns template summary, document metadata, current published version, description fields, version history (document versions with `currentlyPublished`/`SUPERSEDED` markers), effective/review/expiry dates, usage counts, and permission flags (`canEdit/canPublish/canArchive/canViewDocument/canDownloadDocument`). Non-visible templates return 404; version history is withheld (`versionHistoryVisible=false`) without document VIEW.
- `POST /api/v1/templates` (`template.create`) links one template to one existing document, enforcing unique normalized uppercase `template_code`, one template per document, and document visibility (ACTIVE, viewable). No binary is copied.
- `PATCH /api/v1/templates/{id}` (`template.publish|archive`) updates code/name/description with validation and `TEMPLATE_UPDATED` audit.
- `POST /api/v1/templates/{id}/publish` (`template.publish`) defaults to the document's CURRENT version; an explicit version must belong to the document and be CURRENT (non-WITHDRAWN). `DRAFT→PUBLISHED` and re-publish to a newer CURRENT are allowed; same-version re-publish is a no-op; archived template or ARCHIVED document returns 409. Publisher/timestamp/version are stored via a composite FK to `document_versions` and audited as `TEMPLATE_PUBLISHED`.
- `POST /api/v1/templates/{id}/archive` (`template.archive`) moves the template to `ARCHIVED`, keeps the published pointer and history, hides it from the common catalog, and rejects a second archive with 409; audited as `TEMPLATE_ARCHIVED`.
- `POST /api/v1/templates/{id}/usage` records `VIEW|COPY|DOWNLOAD` against the current published version only when the caller holds the underlying `document.view` (VIEW/COPY) or `document.download` (DOWNLOAD), audit `TEMPLATE_USED` with safe metadata. Counts are analytics-only and never grant access.
- Template creation from an archived source document, publishing without a CURRENT version, and cross-document version references are rejected with field/domain errors rather than silent fallbacks.

Frontend:

- Sidebar group "Template" → `/templates` (visible with `template.view`). Catalog page has keyword/category/owner/unit/status filters, server pagination, published/draft/archived status badges, version label, and "Template baru" for `template.create`.
- Detail page shows metadata, source-document link (only with document VIEW), template and document descriptions, version history with `CURRENT/SUPERSEDED` badges and change summaries, "newer document version available" alert for stale publications, usage counts, and governance actions (edit metadata, publish/re-publish, archive) gated by permission flags. Archive and publish use confirmation dialogs that explain audit effects.
- TEMPLATE-04.3 closed: non-current history entries expose "Unduh versi lama", which first shows a blocking warning dialog ("versi X … template menunjuk versi Y … jangan dipakai sebagai acuan resmi") and then downloads that historical document version through the authorized version-content endpoint; cancelled by default.
- Create dialog picks a visible ACTIVE document with search, and edit dialog mounts only when opened with lazy state initialization (no setState-in-effect).
- Notifications route unaffected; template pages re-use shared Card/Table/Badge/Feedback patterns and gain empty/forbidden/not-found/error states.

Verification commands (2026-11-22):

| Command | Result |
|---|---|
| `npm run lint` (frontend) | PASS — 0 errors, 7 warnings (pre-existing categories only) |
| `npm run typecheck` (frontend) | PASS — 0 errors |
| `npm run test` (frontend) | PASS — 3 files, 12 tests |
| `npm run build` (frontend) | PASS — 730.50 kB JS chunk warning (code-splitting deferred) |
| `mvn -f backend/pom.xml test` | PASS — 39 tests, 0 failures/errors (`Authorization` 4, `Authentication` 5, `Acl` 10, `Document` 5, `Repository` 5, `RequestWorkflow` 4, `Distribution` 4, `Template` 2) |

Browser acceptance (Playwright, admin + employee1 + employee3):

- Admin created `TPL_SOP_OPS` on SOP-OPS-001, published it (auto-targeted CURRENT version 2 / label 1.1), opened the source document (VIEW usage), downloaded the published PDF (DOWNLOAD usage), edited metadata, and archived it. The archived filter lists the template, and detail retains version history and “Diarsipkan” status copy.
- Expanded version history shows version 1.0 as `SUPERSEDED` with the “bukan lagi acuan template saat ini” warning; “Unduh versi lama” opens the warning dialog, and confirming downloaded `sop-gangguan.pdf` (version 1) with an explicit toast. Usage counters remained VIEW 1 / DOWNLOAD 1 because analytics is published-version-only.
- Employee3 (no source-document ACL) saw an empty catalog — template metadata does not leak document existence; direct detail access returns not-found.
- Employee1 (document VIEW via ACL) saw the published catalog entry and detail with version history and superseded-version warning before archival.
- Mobile 390×844: Templates group appears in the drawer and catalog/detail render without horizontal overflow; desktop restored to 1440×900.

DB/audit evidence (`kcic-postgres`):

- `template.document_templates` holds one row linking document `43644d1c…`, `template_status=ARCHIVED`, `current_published_version_id` = document version 2, publisher and timestamps set; `document_versions` for the source document remains 2 rows (no binary/version duplication).
- `template.template_usage_events` has one VIEW and one DOWNLOAD row, both against version 2.
- `audit.audit_events` has `TEMPLATE_CREATED`, `TEMPLATE_UPDATED`, `TEMPLATE_PUBLISHED`, `TEMPLATE_ARCHIVED` (one each) and `TEMPLATE_USED` (2) with safe metadata (`documentId`, `versionNumber`, `previousStatus`); no storage keys, URLs, or credentials.

Known limitations recorded:

- Catalog reads load at most 1000 rows then filter visibility and paginate in memory (DEC-050); revisit with ACL pushdown beyond pilot volumes.
- Historical-version access is download-only with a mandatory warning on the template page; inline preview for old versions remains available on the document detail page.
- Historical downloads are intentionally excluded from template usage analytics, which is scoped to the published version (DEC-049/DEC-051).
- The archived template keeps its published pointer; restoring an archived template to the catalog requires a future explicit unarchive decision (not in TEMPLATE-01..04).

### 2026-11-22 — Phase 9 (hardening, audit trail, UAT, handover)

Backend behavior implemented and integration-tested:

- `GET /api/v1/audit-events` (capability `audit.view`, DEC-053): optional `actor` (username/display-name keyword), `action`, `resourceType`, `resourceId`, `outcome`, `from`/`to` (ISO dates, UTC day bounds), `page`/`size` (1..100). JPA Specification query ordered newest-first, `totalItems`/`totalPages` in the response, actor names enriched from `identity.users`. Invalid date range → 400 `fieldErrors[0].field=from`; missing capability → 403 `RESOURCE_FORBIDDEN`.
- `AuditEventRepository` gained `JpaSpecificationExecutor`; `UserRepository.findIdsByActorKeyword` supports actor filtering without exposing directories.
- `AuditIntegrationTest`: employee forbidden, auditor filter combination, future `from` returns an empty page, size=1 pagination, invalid range field error.

Frontend:

- `AuditPage` (`/admin/audit`) with filter form (actor, action, resource type, outcome, from/to), table (time, actor, action + outcome badge, resource, collapsible safe metadata incl. requestId), server pagination, and loading/forbidden/error/empty states. Sidebar "Audit Trail" is shown only with `audit.view`.
- Route code splitting (DEC-054): all pages are `React.lazy` in `app/routes/pages.tsx` (components only), the router imports them and exports only the router, and a shared `Suspense` fallback (`LoadingBlock`) wraps route elements plus the AppShell outlet. Entry chunk dropped from 730.50 kB to 365.23 kB (113.54 kB gzip) and the >500 kB warning is gone; the split removed the 22 react-refresh warnings the first lazy pass introduced (final lint: 0 errors, 6 warnings, all pre-existing).
- Committed Playwright E2E smoke suite (`frontend/e2e`, `playwright.config.ts`, `npm run test:e2e`): unauthenticated deep-link redirect, generic invalid-credential message, employee navigation/permitted-menu assertions, employee forbidden administration pages, admin sidebar + deep links, audit filtering to `LOGIN_SUCCESS` with server pagination, and the not-found route. Credentials are read only from `KCIC_E2E_*` environment variables and the suite fails fast when they are missing; no password is committed. Vitest excludes `e2e/**` so the two runners stay independent.

Verification commands (2026-11-22, final run):

| Command | Result |
|---|---|
| `npm run lint` (frontend) | PASS — 0 errors, 6 warnings (pre-existing react-refresh/React Compiler categories) |
| `npm run typecheck` (frontend) | PASS — 0 errors |
| `npm run test` (frontend) | PASS — 3 files, 12 tests |
| `npm run build` (frontend) | PASS — 365 ms; entry `index` 365.23 kB (113.54 kB gzip), per-route chunks, no size warning |
| `npm run test:e2e` (frontend, Chromium) | PASS — 7/7 tests, 19.0 s (backend + Vite running; credentials from env) |
| `mvn -f backend/pom.xml test` | PASS — 40 tests, 0 failures/errors (`Audit` 1, `Authorization` 4, `Authentication` 5, `Acl` 10, `Document` 5, `Repository` 5, `RequestWorkflow` 4, `Distribution` 4, `Template` 2) |

Browser acceptance (Playwright): admin, employee3, auditor1

- Lazy routes: full page loads/deep links to `/`, `/documents`, `/documents/{id}`, `/admin/audit`, and an unknown path (`Halaman tidak ditemukan`) render correctly with 0 console errors; direct `/admin/audit` as employee3 shows the forbidden state, and the same URL as admin loads the table.
- Audit trail as admin: 157 events, server pagination "1 / 8"; combined filter `employee1` + `LOGIN_SUCCESS` + `SUCCESS` returned "Menampilkan 10 dari 10 event"; activity rows show only safe metadata.
- Cross-role UAT: created fixture `auditor1` through the admin UI, assigned the global AUDITOR role, completed the forced password change, and verified the session exposes 2 capabilities, a sidebar with only Dashboard + Audit Trail, audit access (162 events), and forbidden states (UI) with 403 at the API for `/admin/users` and `/documents`. employee3 remains unable to open `/admin/audit`.
- Sidebar remains the maroon left rail with the gold active edge; no horizontal overflow observed on the checked pages.

Restore drill (verified 2026-11-22): custom-format database dump (161 KB) restored into a scratch database with identical row counts (`users` 4, `documents` 1, `document_versions` 2, `resource_permissions` 12, `requests` 1, `distributions` 2, `templates` 1, `audit_events` 153, `notifications` 6), Flyway history present through V13, filesystem storage SHA-256 identical; scratch database dropped after verification. Backup/restore commands and MSYS path notes are recorded in `docs/DEPLOYMENT.md §6`.

Security review (code-evidence based, recorded in `docs/SECURITY.md`): CSRF cookie/header enforcement, session invalidation on login/password/account changes, BCrypt(12), private streaming storage with random object keys and magic-byte validation, HTTPS-only external links, no presigned/public URLs, safe audit metadata, no hardcoded secrets, and backend-authoritative ACL with deny precedence.

Handover documents added: `docs/SECURITY.md`, `docs/DEPLOYMENT.md`, `docs/UAT.md`, `docs/USER_GUIDE.md`, `docs/ADMIN_GUIDE.md`; `README.md` links now resolve to existing files.

Known limitations recorded for Phase 9:

- Audit trail is global for `audit.view` holders (DEC-053); unit-scoped audit views are not offered in the pilot.
- The committed Playwright suite is a smoke layer that requires a running backend, database, and frontend plus `KCIC_E2E_*` credentials; it asserts navigation, authorization, audit filtering, and the not-found route, while full workflow journeys remain covered by backend integration tests and the recorded UAT scenarios in `docs/UAT.md`.
- Lazy chunks have no explicit retry on transient chunk-load failure beyond a page reload; deployments should replace assets atomically.
- No antivirus, SSO/MFA, centralized logging/APM, or background expiry job (residual risks listed in `docs/SECURITY.md §9`).
- UAT business sign-off (Product Owner/Business Owner/IT) is still pending; `docs/UAT.md §5` is the sign-off sheet.

### 2026-11-22 — E0 (organization & identity audit, planning only)

Scope: audit and documentation for the Organization & Identity Enhancement brief. **No application code, migration, or data was changed.**

Findings (evidence in `docs/organization-identity/PHASE_E0_AUDIT.md`):

- No Employee entity, NIP, employment status, or placement history exists. `organization.organization_units` has no `unit_type`; active units are free-form (`KCIC-HO`, `KCIC-OPS`).
- Membership lives in `organization.user_organization_assignments` with PRIMARY/SECONDARY and a partial unique index that permits only one active PRIMARY per user — multiple active rows remain possible; there is no overlap validation and no DB guarantee of one active placement.
- Authorization matches unit principals exactly (`unitIds.contains(...)`); there is no descendant membership. ACL DENY precedence and provenance isolation are implemented and tested.
- Users own `display_name`/`email`; 3 of 5 dev accounts have no email. Users without a role/placement exist (auditor1, admin) and employee2 has must-change-password with no placement.
- Documented API path `/users/{id}/organization-assignments` does not match implementation `/api/v1/admin/users/{id}/units`; PRD's combined create-user-with-unit differs from the actual separate endpoints.

Deliverables created:

- `docs/organization-identity/PHASE_E0_AUDIT.md` — ground truth, conformity matrix D-01..D-16, discrepancies, blockers B-1..B-5, E0 exit gate PASS.
- `docs/organization-identity/ARCHITECTURE_GAP_ANALYSIS.md` — target model, ACL semantics, gap register G-01..G-20, target API, test matrix.
- `docs/organization-identity/MIGRATION_RISK_REGISTER.md` — V14–V19 staging, risks R-01..R-22, preflight SQL, cutover/rollback.
- `docs/MIGRATION_PLAN.md` — executable cutover plan and post-migration checklist.

Documents updated: `PRD.md` (USER-06/07/08, ACL-04), `ARCHITECTURE.md` (ADR-013..015, §20), `SCHEMA.md` (§21 planned schema), `docs/API_CONVENTIONS.md` (§7–§9), `docs/SECURITY.md` (§11–§12), `DECISIONS.md` (DEC-055..066), `IMPLEMENTATION_PLAN.md` (§21 E0–E8).

Blockers (no code can resolve): verified NIP/email/placement dataset; unit-type mapping; bootstrap/system-account policy; confirmation that legacy grants stay exact (`include_descendants=false`); routing/legacy-membership transition decision.

Gate: E0 documentation-only PASS. E1 not started. No commits, migrations, tests, or database changes were performed.

### 2026-11-22 — E1 (typed organization hierarchy)

Scope: make organization structure typed without widening existing access or inventing official unit mappings.

Implemented:

- `V14__organization_unit_type.sql`: nullable `unit_type` with `COMPANY/DIRECTORATE/DEPARTMENT/DIVISION` check and `(unit_type, unit_status)` index. Existing units stay `NULL` until mapped by the business owner.
- `OrganizationUnit` entity, DTOs, service, and controller expose optional `unitType`; `GET /api/v1/organization-units` and `/api/v1/admin/organization-units` accept `?unitType=`.
- Shape rules: one active COMPANY root; DIRECTORATE requires COMPANY, DEPARTMENT requires DIRECTORATE, DIVISION requires DEPARTMENT; typed units require an active typed parent; untyped parents/units remain valid legacy data.
- Type changes are rejected when the unit has active members or typed children that no longer match the hierarchy. Type cannot be cleared through the API.
- Admin Organization UI: type selector, hierarchy-aware parent options, type badges, and the label **Biro/Division** for the technical `DIVISION` value.

Verification:

- `mvn -f backend/pom.xml -Dtest=OrganizationHierarchyIntegrationTest test` → 4/4 pass (happy path, second COMPANY conflict, wrong/missing parent, untyped parent rejection, type-change conflict, legacy→COMPANY mapping).
- `mvn -f backend/pom.xml test` → 44/44 pass across 10 integration suites.
- `npm run typecheck`, `npm run lint` (0 errors, 7 pre-existing warnings), `npm run test` (12/12), `npm run build` → pass.
- Browser (admin, local dev): Organization page shows legacy units as "Belum bertipe"; create dialog restricts COMPANY to no parent and DIVISION to DEPARTMENT parents with the correct helper text.
- V14 applied successfully to the local dev database; no official unit types were assigned and no employee/HR data was created.

Not done: mapping existing units to official types (blocked by B-2). E-series UAT scenarios remain `NOT_RUN`.

### 2026-11-22 — E2 (employee master and placements)

Scope: employee records and Biro/Division placement history with database-enforced invariants and no fabricated HR data.

Implemented:

- `V15__employee_master.sql`: `identity.employees` (NIP/email case-insensitive unique, employment status check, optimistic version) and `organization.employee_placements` (employee/division/assigned-by FKs, validity check, partial unique index `(employee_id) WHERE is_active`, history indexes). Employee permissions `employee.view`/`employee.manage` seeded and granted to SYSTEM_ADMIN; V16 will re-assert them idempotently.
- Backend `employee` module (api/application/domain/infrastructure): `GET/POST /api/v1/admin/employees`, `GET/PUT /api/v1/admin/employees/{id}`, `POST /api/v1/admin/employees/{id}/placements`.
- Employee + initial placement are created in one transaction. Placement requires an ACTIVE unit typed `DIVISION`; Directorate/Department are derived from placement ancestry and never stored on the employee.
- Transfer locks the employee row, closes the previous placement (`is_active=false`, `valid_until` set) and inserts a new one; an optional `expectedPlacementId` rejects stale concurrent requests with `409`, and the partial unique index is the final guard. TERMINATED employees cannot be placed.
- Audit events `EMPLOYEE_CREATED`, `EMPLOYEE_PLACED`, `EMPLOYEE_TRANSFERRED`, `EMPLOYEE_UPDATED` with safe metadata (division code, status only; no NIP/email).
- Frontend: Employee navigation entry (capability `employee.view`), list with keyword/status filters and pagination, create dialog with Biro/Division selection, detail page with derived org, edit, transfer, and full placement history; loading/empty/error/conflict states.

Verification:

- `mvn -f backend/pom.xml -Dtest=EmployeeIntegrationTest test` → 8/8 pass (create + derived org, duplicate NIP/email, non-DIVISION and inactive division rejection, transfer history, stale transfer conflict, DB second-active-placement violation, read authorization, search + profile update).
- `npm run typecheck`, `npm run lint` (0 errors, 7 pre-existing warnings), `npm run test -- --run` (13/13), `npm run build` → pass.
- Browser (admin, local dev): Employee menu and list render; create dialog disables placement selection and explains that an official active Biro/Division mapping is required. No employee or organization records were fabricated; V15 applied to the local dev database.

Not done: populating official employee/NIP/placement data (blocked by B-1/B-2). E-series UAT scenarios remain `NOT_RUN`.

### 2026-11-22 — E3 (one-to-one Employee–User linkage)

Scope: bind every employee to exactly one local account without inventing employees and without weakening existing account administration.

Implemented:

- `V16__user_employee_link.sql`: `identity.users.employee_id` with FK to `identity.employees` and partial unique index `users_employee_uq (employee_id) WHERE employee_id IS NOT NULL`; `employee.view`/`employee.manage` re-asserted idempotently for SYSTEM_ADMIN.
- `User.employeeId` with `linkEmployee(...)`; `UserRepository.findByEmployeeId`, `existsByEmployeeId`, and `searchUnlinked`; `UserSummaryResponse.employeeId`.
- `EmployeeService` returns `linkedAccount` in employee detail; `EmployeeAccountService` in the employee module owns both mutations so `identity` does not depend on `employee`.
- Endpoints: `POST /api/v1/admin/employees/{id}/account` (create account with username + temporary password, `mustChangePassword=true`, email taken from the employee), `POST /api/v1/admin/users/{userId}/employee-link` (link an unlinked account; both sides checked), `GET /api/v1/admin/users/unlinked` (migration worklist). Both mutations require `user.manage` + `employee.manage`; the unlinked worklist requires `user.manage`.
- Audit events `EMPLOYEE_ACCOUNT_CREATED` / `EMPLOYEE_ACCOUNT_LINKED` with employee id + username only.
- Frontend: employee detail now shows a linked-account card (username link, status, initial-password state, last login) with "Buat akun" (username, temporary password, confirmation) and "Hubungkan akun" (search + radio list of unlinked accounts, loading/empty/error states) dialogs; Users list shows an Employee badge that deep-links to the employee; user detail shows the employee link or "Belum terhubung ke employee".

Verification:

- `mvn -f backend/pom.xml -Dtest=EmployeeAccountIntegrationTest test` → 8/8 pass (create + 1:1 link + forced password change, empty linked account, duplicate account conflict, username conflict, weak password, link/duplicate-link conflicts, unlinked list exclusion, 403 for plain employees).
- Full `mvn -f backend/pom.xml test` → see gate summary in the E8 report; E2 plus E3 suites pass.
- `npm run typecheck`, `npm run lint` (0 errors, 7 pre-existing warnings), `npm run test -- --run` (13/13), `npm run build` → pass.
- Browser (admin, local dev): Employee list and create dialog render; Users list and user detail render the new employee relation row. No employee/HR data exists in the dev database, so the account dialogs were not executed through the browser; their behavior is covered by the integration tests. V16 applied to the local dev database.

Known limitation: linking is intentionally not reversible through the API; unlinking would need an explicit policy decision. Account lifecycle remains independent from employment status (deactivation stays explicit and audited).

### 2026-11-22 — E4 (descendant-aware authorization, opt-in)

Scope: allow a unit-scoped grant or role assignment to cover descendants of the selected unit without widening any existing row.

Implemented:

- `V17__descendant_scope_flag.sql`: `include_descendants BOOLEAN NOT NULL DEFAULT FALSE` on `authorization.resource_permissions` and `authorization.user_role_assignments`, with CHECK constraints allowing `true` only for `ORGANIZATION_UNIT` principal/scope. Existing rows stay exact.
- `ResourcePermission` and `UserRoleAssignment` carry the flag; `AuthorizationService`:
  - `activeUnitIds` now includes the user's active employee placement unit in addition to legacy `user_organization_assignments`.
  - Unit matching expands to ancestors (`OrganizationUnitRepository.findAncestorIds`, recursive CTE) only when at least one relevant candidate has the flag; legacy rows keep exact membership.
  - Descendant matching applies to resource capability, content capability, unit capability, and ACL decisions; explicit `DENY` still returns immediately for any matching effective grant.
  - `grant(...)` and `assignRole(...)` accept the flag through additive overloads (existing two-argument-list callers keep exact semantics), reject `true` for non-unit principals/scopes, and record the flag in audit metadata.
- API: `CreateGrantRequest.includeDescendants` and `AssignRoleRequest.includeDescendants` (optional, default false); `GrantResponse` and `RoleAssignmentView` return the flag. `ResourceAclService` validates and forwards it.
- Frontend: access grant dialog shows an opt-in "Termasuk unit turunan" checkbox for unit principals; role assignment dialog shows the same checkbox for unit scope; active/history grants and assignment rows display a coverage badge or note. Types updated.
- `DescendantScopeIntegrationTest` covers: legacy grant does not widen to a descendant placement; descendant grant allows a division placement under the granted department; explicit deny at the child unit beats the descendant allow; descendant role assignment covers a descendant-owned workspace while the exact assignment does not; flag rejected for user grant and global scope.

Verification:

- `mvn -f backend/pom.xml -Dtest=DescendantScopeIntegrationTest test` → 5/5 pass (V1–V17 applied on a clean Testcontainers database).
- Full backend `mvn test` rerun tracked in the E8 gate; E4 changes are additive to existing signatures.
- `npm run typecheck`, `npm run lint` (0 errors, 7 pre-existing warnings), `npm run test -- --run` (13/13), `npm run build` → pass.

Known limitation: ancestor expansion is evaluated per capability check; for large hierarchies the recursive query cost should be reviewed during E8 hardening. The dev database has no typed org data inserted by this phase, so the new checkbox flow is covered by integration tests rather than live data.

### 2026-11-22 — E5 (administration UX and bootstrap alignment)

Scope: make account coverage visible to administrators before the E6 cutover, without importing or fabricating HR data.

Implemented:

- Backend read model (additive): `GET /api/v1/admin/employees` accepts an optional `linked=true|false` filter (JPQL exists-subquery on `identity.users.employee_id`); employee list and detail responses expose `hasAccount` derived from the same source. The filter queries remain server-side and paged.
- `EmployeeAccountIntegrationTest` gained `employeeListReportsAndFiltersAccountCoverage`: `linked=false` returns the employee with `hasAccount=false`; after linking, `linked=true` returns it with `hasAccount=true` and `linked=false` returns zero (suite 9/9).
- Frontend employee list: new "Akun pengguna" filter (Semua / Sudah terhubung / Belum terhubung) wired into the query key and reset; first cell shows a success badge "Akun terhubung" or warning badge "Belum punya akun".
- New `MigrationPage` at `/admin/migration` (lazy, `RequireCapability employee.view`, sidebar "Migrasi Akun" under Administration): three stat tiles (employees without account, employees with account, unlinked accounts) and two worklists linking to employee/user detail. Each list has independent loading, error, empty, and capability-forbidden states; the page states explicitly that employee data must come from the official HR source and that bootstrap accounts are infrastructure-only.
- `navigation.test.ts` updated for the new entry and active-route label; other suites unchanged.

Verification:

- Frontend: `npm run typecheck`, `npm run lint` (0 errors, 7 pre-existing warnings), `npm run test -- --run` (13/13), `npm run build` → pass.
- Backend: full `mvn test` after E4+E5 → 66/66 pass, BUILD SUCCESS.
- Browser acceptance (dev backend restarted with the E5 build; the pre-E5 process served the old DTO and was stopped):
  - Created the full typed chain in the admin UI: UAT Perusahaan (COMPANY) → UAT Direktorat (DIRECTORATE) → UAT Departemen (DEPARTMENT) → UAT Biro/Division (DIVISION). The employee form offers only `DIVISION` units.
  - Created employee `UAT-NIP-001` placed in UAT Biro/Division; detail shows the derived Directorate/Department and history.
  - Created the linked account `uat.biro` from the employee detail (email inherited, forced password change), then verified the employee list badges ("Akun terhubung"), `linked=false` → empty, `linked=true` → the single employee, and `/admin/migration` stats 0/1/5 with both worklists.
  - Granted `document.view` on SOP-OPS-001 to the linked user through the access dialog; the active grant appears as `User · Sumber Manual`.
  - Mobile drawer at 390 px lists "Migrasi Akun"; sidebar order is User Management → Employee → Migrasi Akun → Authorization.

Known limitation: typed UAT records now exist only in the local dev database and are labeled UAT; no production seed or fabricated HR data was added. The migration page is intentionally read-only — blocking cutover enforcement remains E6.

### 2026-11-22 — E6 (cutover preflight and rehearsal; V18 still blocked on data)

Status: **BLOCKED** on external inputs (verified HR data + unit-mapping sign-off). All safe, verifiable E6 tooling is delivered; the destructive cutover was not executed.

Implemented:

- `tools/preflight/e6_preflight.sql`: read-only checks E6-01…E6-09 with an `E6-GATE` summary (E6-05 = every unit typed, including inactive, because V18 applies a table-wide `NOT NULL`; E6-07 = exactly one active COMPANY root; E6-08 = ACTIVE account linked to non-ACTIVE employee; E6-09 = blank/placeholder/untrimmed/malformed employee master fields) plus the `include_descendants` and legacy-assignment review sections.
- `tools/preflight/e6_unit_inventory.sql` + guarded `e6_apply_unit_mapping.sql`: inventory for the typed-hierarchy decision and a transactional mapping applier with pre/post invariant checks and reversal SQL. Running it on dev aborts by design because the active `UAT-CO` fixture would create a second COMPANY (no data changed).
- `docs/organization-identity/E6_UNIT_MAPPING.md`: recommendation `KCIC-HO → COMPANY`, `KCIC-OPS → DIRECTORATE under KCIC-HO`, with the sign-off table; explicitly notes that no DEPARTMENT/DIVISION exists yet, so placements remain impossible until HR supplies the real tree.
- `docs/organization-identity/E6_HR_VERIFICATION.md`, `tools/preflight/e6_hr_template.csv`, `e6_validate_hr.sql`, `run-hr-validation.sh`: CSV contract and read-only validator (placeholders/blank fields, status, email format, duplicates in file, division existence/type/status, conflicts with existing employees). Hardened after the E6 audit: the runner requires the exact header order (exit 2 on mismatch), loads with an explicit column list, E6-HR-00 rejects a header-only file, and E6-HR-08 rejects untrimmed NIP/division codes. Template, reordered-header, and header-only runs all fail as expected.
- `run-e6-preflight.sh` now exits `1` when the `E6-GATE` data row is `BLOCKED` (not only on SQL errors), and the rehearsal consumes that exit code; a `PASS` gate exits `0`.
- Account lifecycle guards: creation requires an ACTIVE employee; an ACTIVE account cannot be linked to a non-ACTIVE employee (deactivate it first); reactivation/unlock is rejected while the linked employee is non-ACTIVE. Integration coverage in `EmployeeAccountIntegrationTest` (suite 12/12). `EmployeeService.update` now records `changedFields` (including `nip`) in `EMPLOYEE_UPDATED` without logging values.
- Bootstrap admin linkage: `KCIC_BOOTSTRAP_ADMIN_EMPLOYEE_NIP` resolves a case-insensitive NIP to an existing ACTIVE Employee at creation; unknown/non-ACTIVE NIP aborts startup, unlinked bootstrap logs an explicit pre-V18 warning, and the NIP is not logged or audited (DEC-069, `BootstrapAdminInitializerTest` 4/4).
- `e6_preflight.sql`: E6-INFO lists active legacy unit assignments on linked accounts with a `DIFFERS_FROM_PLACEMENT` marker; unit scope is the union of legacy and placement, so differing rows need a per-row decision. No bulk delete. Dev result: 0 rows.
- `tools/preflight/e6_unlinked_worklist.sql`: read-only worklist for HR (account identity, legacy units, existing Employees, automatic email/username candidates, blank mapping columns). Dev result: 5 unlinked accounts, 0 automatic matches, 1 existing Employee already linked to `uat.biro`; confirms username/email cannot substitute for a verified NIP. Procedure and decision matrix in `E6_HR_VERIFICATION.md` §3–4.
- `tools/preflight/v18_candidate.sql`, `e6_rehearsal_fixture.sql`, `run-e6-rehearsal.sh`: V18 candidate (not a Flyway migration) rehearsed on a throwaway database (V1–V17 → rehearsal copies of KCIC-HO/KCIC-OPS + labeled REH department/division and employee fixtures → **real `e6_apply_unit_mapping.sql`** → preflight PASS → V18 → NOT NULL verified → database dropped).
- `docs/organization-identity/E6_CUTOVER_RUNBOOK.md`: end-to-end steps, owners, stop conditions, V18 application, smoke tests, and rollback.

Evidence:

| Run | Result |
|---|---|
| `E6_UNLINKED_WORKLIST_DEV_2026-11-22.txt` | 5 unlinked accounts; **0 automatic email/username matches**; only `UAT-NIP-001` exists and is already linked to `uat.biro` |
| `E6_PREFLIGHT_DEV_2026-11-22_r4.txt` | **BLOCKED** — 5 unlinked accounts + 2 untyped units (both ACTIVE); E6-07/08/09 PASS; E6-INFO legacy-linked = 0; runner exits 1 on BLOCKED |
| `E6_REHEARSAL_2026-11-22_r3.txt` | **PASS** — recommended mapping applied to KCIC-HO/KCIC-OPS copies; E6-GATE 0 violations with E6-08/09 and E6-INFO; V18 candidate applied; `employee_id`/`unit_type` `NOT NULL` verified; strict runner exits 0 only on PASS |
| `E6_HR_VALIDATION_SELFCHECK_2026-11-22.txt` | Template FAIL (exit 1), reordered header rejected (exit 2), header-only file FAIL via E6-HR-00 (exit 1) |
| `E6_PREFLIGHT_DEV_2026-11-22_r3.txt` | Superseded by r4; same 5+2 blockers, gate E6-01…E6-09 |
| `E6_REHEARSAL_2026-11-22_r2.txt` | Superseded by r3; PASS before the E6-INFO/strict-runner additions |
| `E6_PREFLIGHT_DEV_2026-11-22_r2.txt` | Superseded by r3; same 5+2 blockers, gate E6-01…E6-07 |

Still blocking cutover (not fabricated, not bypassed):

- Verified HR dataset and placement mapping from HR (`E6_HR_VERIFICATION.md`).
- Org-owner sign-off for the unit mapping and the missing Directorate/Department/Division tree.
- Bootstrap/system-account policy: linkage is now supported at creation via `KCIC_BOOTSTRAP_ADMIN_EMPLOYEE_NIP` (DEC-069); the remaining input is the verified NIP of an ACTIVE Employee. V18 stays plain `NOT NULL` (DEC-066) and no account exception is approved (DEC-068).
- Dev remains a UAT environment: its `UAT-CO` fixture conflicts with the single-COMPANY rule, so the mapping is exercised only on a production-like/rehearsal database.

Verification after the E6 audit:

- Backend `mvn -f backend/pom.xml test` → 84 tests, 0 failures (includes the three account-lifecycle guards and 4 bootstrap-linkage tests).
- Frontend `typecheck`, `lint` (0 errors, 7 pre-existing warnings), `test -- --run` (13/13), `build` → pass; employee detail disables "Buat akun" for non-ACTIVE employees and explains the deactivate-first rule for linking.
- `run-e6-preflight.sh` on dev → exit 1 with `E6-GATE BLOCKED` (5 unlinked + 2 untyped); rehearsal → exit 0 with `E6-GATE PASS`, V18 NOT NULL verified.

Not done by design: V18 is not a Flyway migration and has not been applied anywhere; no HR import ran; no placeholder NIPs or guessed unit types were inserted.

### 2026-11-22 — E7 (legacy retirement and cross-module alignment)

Status: **DONE** for the application-level alignment; the legacy table and V19 retirement stay out of scope pending explicit approval.

Implemented:

- `OrganizationService.assignUser` rejects accounts with `employee_id` (`409 CONFLICT`, message directs to Employee placement). `endAssignment` remains allowed for linked accounts, because ending a legacy row only narrows access and is required to retire stale grants without destructive SQL.
- Unit reads now follow the active Biro/Division placement: `GET /admin/users/{id}/units` appends the placement unit; `GET /admin/organization-units/{id}/members` appends placement-derived accounts (`assignmentId` = placement id, type `PRIMARY`, deduplicated by user); `GET /auth/me` adds the placement unit as `PRIMARY`.
- `DistributionService` unit recipients resolve the union of legacy active assignments and active placements of linked accounts at send time. Recipients are still snapshotted into `distribution_recipients`, so a later transfer does not change historical recipients or already-granted access.
- No schema change and no data deletion; `organization.user_organization_assignments` is retained. V19 (drop/privilege revocation) remains approval-only.

Verification (2026-11-22):

| Command | Result |
|---|---|
| `mvn -f backend/pom.xml test` | PASS — 71 tests, 0 failures/errors; includes `LegacyMembershipAlignmentIntegrationTest` 4/4 and `DistributionIntegrationTest` 5/5 (new placement-snapshot test) |

Known limits:

- Request routing stays exact per unit (documented decision); ancestor routing is a separate business decision.
- Legacy rows created before linking are not auto-migrated; they remain readable and can be ended, but administrators are steered to Employee placement for new changes.
- E6/V18 cutover remains BLOCKED on verified HR data and unit mapping.

### 2026-11-22 — E8 (security hardening and E-series sign-off)

Status: **DONE** for the application-level security controls; human UAT and the E6 cutover remain BLOCKED on external inputs.

Implemented:

- `AuthenticationService.login` denies linked accounts whose employee is not `ACTIVE` (`403 ACCOUNT_DISABLED`, audit `LOGIN_DENIED` reason `EMPLOYMENT_INACTIVE`); `/auth/me` also returns the placement unit. Missing employment row for a linked account is treated as inactive.
- `EmployeeService.update` invalidates the linked account's sessions when status leaves `ACTIVE` and records `EMPLOYEE_ACCESS_SUSPENDED` with the new status and username.
- `AuthorizationService` rejects new unit-scoped/global role assignments and ACL `USER` grants while employment is not `ACTIVE` (`400 VALIDATION_ERROR` on `userId`/`principalId`), without touching existing grants.
- `DistributionService` omits inactive employees when expanding unit recipients and rejects an explicitly selected inactive recipient (`fieldErrors.recipientUserIds`). Snapshots and access provenance are unchanged.
- `tools/ops/employment_lifecycle_check.sql` + `run-employment-check.sh` (read-only): E8-OPS-01 non-ACTIVE employees with ACTIVE accounts (BLOCKER), E8-OPS-02 leftover active placements (WARN), E8-OPS-03 linked users without employment row (WARN). The runner exits `1` when the `E8-OPS-GATE` data row is `BLOCKED` and `0` for WARN-only findings, so scheduled runs fail on real violations. Dev evidence: `docs/organization-identity/E8_OPERATIONAL_CHECK_DEV_2026-11-22.txt` — all zero, gate PASS, exit 0.
- Tests: `EmploymentLifecycleSecurityIntegrationTest` (login, session end, grant denial, capability-based IDOR probe), `AclIntegrationTest.revokingOneGrantKeepsIndependentGrantIntact`, `DistributionIntegrationTest` inactive-recipient and placement-snapshot cases.

Verification (2026-11-22):

| Command | Result |
|---|---|
| `mvn -f backend/pom.xml test` | PASS — 84 tests, 0 failures/errors |
| `npm run typecheck` / `lint` / `test -- --run` / `build` (frontend) | PASS (unchanged from E7; no frontend code changed in E8) |
| `tools/ops/run-employment-check.sh` (dev DB) | PASS — E8-OPS-GATE 0 violations, exit 0 |

Known limits:

- Human E-series UAT (`docs/UAT.md`) is still `NOT_RUN`: it requires verified HR data and the KCIC-HO/KCIC-OPS unit-type mapping (blockers B-1/B-2), so E6/V18 stays unapplied.
- An employment status changed directly in the database bypasses session invalidation; the operational check detects the resulting inconsistent account (E8-OPS-01).
- Request routing remains exact per unit; distribution to a unit is the placement-aware path.
- V19 (drop of legacy assignments) remains approval-only; no data was deleted.

### 2026-11-22 — UI/UX audit, refinement, and verification

Status: **DONE** for the frontend presentation layer. No backend, API, schema, authorization, or data changes.

Implemented:

- Dashboard rewritten as a capability-gated bento (two independent columns at `xl`, one column below) with per-tile loading/error/empty states and real data only.
- `FilterPanel` and `PaginationBar` shared components: filter collapse below `lg` with `aria-expanded`/`aria-controls` and active-count badge; pagination range (`aria-live`), page-size 10/25/50/100, page reset on filter/size change.
- Separated filter card from table card on Dokumen, Requests, Distribusi, Template, Users, Employees, and Audit; single-search pages keep an inline toolbar (documented variant).
- Document detail: code chip in header meta, short panel descriptions, activity capped at 8 items with expand control.
- Copy: removed restated menu descriptions (Workspaces, Roles, Organization), trimmed Migration callout and description, removed the repeated footer tagline.
- Tokens: added `danger-hover` (#9A1D14) and `danger-pressed` (#801810); `ConfirmDialog` now uses shared `Button`.
- `DESIGN_SYSTEM.md` raised to v1.1 (§5.2 navigation interaction, responsive breakpoints, §6.8 page header, §6.9 filter panel, §6.10 pagination, §7.7 dashboard).

Verification (2026-11-22, `frontend/`):

| Command | Result |
|---|---|
| `npm run typecheck` | PASS |
| `npm run lint` | PASS — 0 errors, 8 warnings (react-refresh exports, react-hook-form library) |
| `npm run test -- --run` | PASS — 13/13 |
| `npm run build` | PASS |
| Backend tests | Not re-run (no backend change); last verified 84/84 in E8 |
| Playwright E2E | BLOCKED — `KCIC_E2E_*` credentials absent |

Visual evidence: `frontend/docs/ux-evidence/` (35 PNG at 1536/1280/1024/768/390). Manual checks: sidebar collapse persists (264→76 px), drawer, filter collapse, audit pagination (`1–50 dari 196`), 390 px dialog, skip-link focus.

Reports: `frontend/docs/UI_UX_AUDIT_REPORT.md`, `UI_UX_DESIGN_SPECIFICATION.md`, `COMPONENT_STANDARD.md`, `UI_UX_IMPLEMENTATION_REPORT.md`, `UI_UX_VERIFICATION_REPORT.md`.

Known limits: no time-series charts (no aggregate endpoint; no fabricated data), no column sort (API), Master Data unpaginated (small bounded set), navigation language follows PRD terminology, axe/screen-reader/zoom checks deferred.

### 2026-11-22 — Accessibility: axe-core pipeline and findings remediation

Status: **DONE** for the frontend accessibility pipeline. No backend, API, schema, authorization, or data changes.

Implemented:

- Added `axe-core` and `@axe-core/playwright` as devDependencies (no runtime bundle impact).
- `src/test/axe.ts`: shared axe helper for vitest (WCAG 2.0–2.2 A/AA tags; `color-contrast` disabled in jsdom because it has no layout — contrast verified in a real browser instead).
- `src/test/accessibility.test.tsx`: 11 axe tests over shared primitives (Button, Badge, PageHeader, Card/DetailRow, FilterPanel, PaginationBar, Dialog/ConfirmDialog, Field, Feedback, Toast) plus a page-level landmark/skip-link/table composition. Fails on any violation.
- `src/test/setup.ts`: minimal jsdom `<dialog>` `showModal`/`close` polyfill.
- `e2e/a11y.spec.ts`: Playwright + `@axe-core/playwright` suite covering login, 15 authenticated routes, logout, and 390 px mobile. Requires `KCIC_E2E_*` credentials (BLOCKED locally).
- Fixed all live-scan findings: sidebar brand-mark background `white/10` → `primary-pressed` (gold 3.88:1 → 6.63:1); `white/60` subtitles → `white/75` on Sidebar/Login/ChangePassword (4.19:1 → 5.81:1); document SHA-256 `ink-disabled` → `ink-secondary` (2.84:1 → 5.66:1); active version row wrapped in `<ul>` (`listitem` rule).

Verification (2026-11-22, `frontend/`):

| Command | Result |
|---|---|
| `npm run typecheck` | PASS |
| `npm run lint` | PASS — 0 errors, 8 warnings (unchanged library warnings) |
| `npm run test -- --run` | PASS — 24/24 (4 files; +11 axe tests) |
| `npm run build` | PASS — `index-*.js` 365.28 kB (gzip 113.40 kB) |
| Live axe browser scan | PASS — 0 violations across 17 routes @1536 and 4 scans @390 after fixes |
| Playwright a11y suite | BLOCKED — `KCIC_E2E_*` credentials absent |

Reports updated: `frontend/docs/UI_UX_VERIFICATION_REPORT.md` v1.1 (§5.3 axe pipeline + findings table), `UI_UX_IMPLEMENTATION_REPORT.md` v1.1 (§2.6). Remaining: run the Playwright a11y suite in CI/staging with valid credentials, real screen-reader testing, and 200% zoom / 320 px reflow before formal UAT.

### 2026-11-22 — UI taste pass: bento dashboard, table primitives, action visibility, copy trims

Status: **DONE** (frontend presentation only). No backend, API, schema, authorization, or data changes.

Implemented:

- New shared `Table/THead/Th/Tr/Td` (`frontend/src/shared/components/Table.tsx`): scroll wrapper + sr-only caption, uppercase 11 px headers on `surface-muted`, row hover tint, `hover` prop for non-interactive rows. Converted 8 tables (Users, Employees, Roles, Workspaces, Master Data, Organization, EmployeeDetail placements, Audit); no raw `<table>` left in `features/`.
- `ButtonLink` added to `Button.tsx`; dashboard CTA and Request/Distribution/Template not-found actions no longer style raw `<Link>` as buttons. Row actions (Kelola/Buka/Permission/Ubah) are secondary compact + chevron; Organization `LinkToUser` no longer full-reloads. Neutral ghost actions (Tandai dibaca, folder actions, promote version, template historical download) raised to secondary; destructive revokes keep ghost + danger tint.
- Dashboard bento stat row: four capability-gated tiles with real query totals (assigned requests, accessible documents, received/sent distributions, unread notifications); unread tile solid maroon when > 0. Duplicate totals footer and in-card unread block removed.
- Copy/placement: DocumentDetail "Riwayat versi" excludes the current version and drops the "N versi tercatat" description; AccessPanel policy copy trimmed (deny precedence and grant-source isolation retained); Users password helper trimmed and avatar initials added; Employees account badge moved to Status column; audit UUIDs truncated with `title`, metadata disclosure is a button with rotating chevron, outcomes labelled in Indonesian.
- axe page-level composition test updated to the new Table primitives + `ButtonLink`.

Verification (2026-11-22, `frontend/`):

| Command | Result |
|---|---|
| `npm run typecheck` | PASS |
| `npm run lint` | PASS — 0 errors, 8 pre-existing warnings |
| `npm run test -- --run` | PASS — 24/24 (4 files) |
| `npm run build` | PASS — `index-*.js` 272.68 kB (gzip 83.51 kB) |
| Live axe scan | PASS — 10 scans (5 routes × 1536/390), 0 violations |
| Visual inspection | PASS — before/after captures in `frontend/docs/ux-evidence/taste-before-*` (13) and `taste-after-*` (8); bento 2-col at 390 px, tables scroll without page overflow |
| Playwright E2E/a11y suite | BLOCKED — `KCIC_E2E_*` credentials absent |

Docs: `frontend/docs/COMPONENT_STANDARD.md` v1.1 (§13 Table dan aksi baris, ButtonLink rule), `UI_UX_VERIFICATION_REPORT.md` v1.2 (§9 addendum), `UI_UX_IMPLEMENTATION_REPORT.md` v1.2 (§8).



### 2026-11-23 — UI rebuild (ui-ux-pro-max), submenu Organization per tipe, role model pilot

Status: **DONE (code + gates)**; penerapan migration V19 dan verifikasi browser live **BLOCKED** menunggu kredensial PostgreSQL lokal (Docker sudah dihapus; backend/frontend belum berjalan).

Implemented:

- **Rebuild UI** atas keputusan pemilik produk: skill `ui-ux-pro-max` dipasang dan dipakai (design dials variance 7 / motion 3 / density 7; gaya bento-box-grid + data-dense-dashboard; tipografi EB Garamond + Lato). Palet generator (navy/blue) ditolak — palet maroon `#8B1A1A` + putih/silver + hitam/soft gold dipertahankan; biru tetap dilarang. Token baru: `--font-display`, `--radius-card:16px`, `--radius-lg:10px`, `--radius-xl:14px`, `--shadow-card(-hover)`, `--shadow-button`, `.tile-hover`; body 15 px/24 px; focus-visible 3 px (gold di aside/dialog). Shell: sidebar submenu (chevron terpisah, `aria-expanded`, auto-open), breadcrumb `navTrail()`, login dan dashboard bento, StatTile Migrasi disamakan. Semua fitur lama tetap ada.
- **Submenu Organization** (`organization.manage`): halaman CRUD per tipe `/admin/organization/directorates|departments|divisions` (tabel sendiri: Unit/Induk/Status/Diperbarui/Aksi, pencarian inline, dialog buat dengan tipe terkunci + induk sesuai hierarki) dan detail unit `/admin/organization/units/:unitId` (rantai hierarki, unit turunan, anggota, informasi + edit). Helper bersama `unitLabels.ts` dan `UnitEditDialog.tsx`; OrganizationPage lama tetap sebagai pohon gabungan + tile jumlah per tipe + tautan Detail. Backend tidak berubah (filter `unitType` dan endpoint detail sudah ada sejak E1/E5).
- **Role model pilot** (DEC-070): `ADMINISTRATOR`, `ADMIN`, `PIC` + label `User (Employee)`; seed idempotent **V19__pilot_role_model.sql** (V18 tetap cadangan cutover E6 sesuai SCHEMA §21.7). `RoleCodes` constants; governance-only filter ACL mencakup SYSTEM_ADMIN+ADMINISTRATOR; routing request UNIT_MANAGER→PIC, DOCUMENT_ADMIN→ADMIN, fallback governance; last-admin guard melindungi kedua role governance; label role frontend terpusat (`roleLabel`).

Verification (2026-11-23):

| Command | Result |
|---|---|
| `frontend: npx tsc --noEmit` | PASS |
| `frontend: npm run lint` | PASS — 0 errors, 8 pre-existing warnings |
| `frontend: npx vitest run` | PASS — 35/35 (5 files; +6 unitLabels, +5 navigation submenu/navTrail) |
| `frontend: npm run build` | PASS — entry 276.74 kB (gzip 84.45 kB) |
| `backend: mvn -q -DskipTests compile` | PASS |
| `backend: mvn test -Dtest=RequestRoutingServiceTest` | PASS — 5/5 |
| Backend integration tests | BLOCKED — Testcontainers membutuhkan Docker yang sudah dihapus |
| Live browser verification + apply V19 | BLOCKED — kredensial PostgreSQL lokal belum diberikan user |

Docs: `SCHEMA.md` §6.1 + §21.7 (V19), `DESIGN_SYSTEM.md` §14 addendum, `docs/DECISIONS.md` DEC-070..072, `design-system/kcic-docs/MASTER.md` owner override.

Blockers tersisa: (1) kredensial PostgreSQL lokal untuk restore `.local/kcic_connect.dump`, apply V19, dan menjalankan backend+frontend; (2) strategi integration test tanpa Docker (native PG atau tetap BLOCKED terdokumentasi); (3) E6 menunggu data HR terverifikasi; (4) seluruh perubahan sejak commit `765ff5b` belum di-commit (menunggu otorisasi baru).

### 2026-11-23 — Environment lokal aktif, V19 applied, rebuild halaman login

Status: **DONE (code + gates + verifikasi browser live)**.

Environment: PostgreSQL 18.6 native (127.0.0.1:5432) menggantikan Docker; DB `kcic_connect` direstore dari `.local/kcic_connect.dump` (`--no-owner --no-privileges`), role aplikasi `kcic` dengan password tergenerate (hanya di `.env`, gitignored). Storage filesystem `.local/storage-dev`. Backend :8080 dan frontend :5173 berjalan; Flyway menerapkan **V19** otomatis — role ADMIN/ADMINISTRATOR/PIC ACTIVE dan EMPLOYEE berlabel `User (Employee)` terverifikasi via psql.

Rebuild login (arahan `@ui-ux-pro-max rebuild tampilan login`): gaya editorial serif + minimalisme (hasil search skill: editorial-grid-magazine, accessible authentication; saran focus-ring biru dari skill ditolak sesuai kunci palet). Panel kiri maroon dengan tekstur dot halus, gold rule, headline EB Garamond 42 px dengan span gold, tiga mini-tile bento (katalog/akses/distribusi); kanan kartu form dengan gold bar + heading serif; mobile mendapat hero band maroon ringkas (lg:hidden). Fitur baru: toggle lihat password (ikon eye/eye-off baru, `aria-pressed` + label dinamis) memakai slot `trailing` baru pada `InputField`. Seluruh logika lama dipertahankan: zod, `autocomplete=username/current-password`, paste bebas, autoFocus, redirect `mustChangePassword`/`from`, Alert error ApiError, loading submit.

Files: `frontend/src/features/auth/LoginPage.tsx` (rewrite), `frontend/src/shared/components/Field.tsx` (slot trailing), `frontend/src/shared/components/Icon.tsx` (+eye-off).

Verification (2026-11-23):

| Gate | Result |
|---|---|
| `npx tsc --noEmit` | PASS |
| `npm run lint` | PASS — 0 errors, 8 pre-existing warnings |
| `npx vitest run` | PASS — 35/35 |
| `npm run build` | PASS |
| Browser desktop 1440×900 | PASS — render sesuai desain (`.local/ui-evidence/login-rebuild-desktop.png`) |
| Browser mobile 390×844 | PASS — hero band + form, tanpa horizontal scroll (`login-rebuild-mobile.png`) |
| Toggle password | PASS — type text/password, aria-pressed/label benar |
| Login benar | PASS — redirect ke dashboard |
| Login salah | PASS — Alert "Username atau password tidak sesuai." (console error = 401 yang diharapkan) |
| Console halaman login | PASS — 0 error pada load normal |

Blocker lama yang selesai: kredensial PostgreSQL lokal (diberikan user), apply V19, verifikasi browser live. Blockers tersisa: (1) integration test backend tanpa Docker (Testcontainers) — strategi perlu diputuskan; (2) E6 menunggu data HR terverifikasi; (3) perubahan sejak `765ff5b` belum di-commit (menunggu otorisasi baru).

### 2026-11-23 — Toggle sidebar pindah ke atas (ikon hamburger)

Permintaan pemilik produk: tombol perluas/ciutkan sidebar tidak di bawah, tetapi di atas dengan ikon SVG tiga garis. `Sidebar.tsx`: komponen `SidebarToggle` (ikon `menu`, aria-label/aria-expanded/title tetap) dirender di header brand — kanan baris brand saat expanded, di atas logo KC saat collapsed; tombol bawah dihapus. MobileDrawer tidak berubah (tetap tombol tutup). Gates: tsc PASS, lint 0 errors, vitest 35/35. Browser: kedua state diverifikasi (`.local/ui-evidence/sidebar-toggle-expanded*.png`).

### 2026-11-23 — Fix responsive zoom-out (layout full-width)

Keluhan pemilik produk: saat zoom out, konten menyisakan margin kosong besar karena `main`/`footer` dibatasi `max-w-[1440px] mx-auto`. `AppShell.tsx`: cap lebar dihapus; padding progresif `px-4 lg:px-8 2xl:px-12`. `DashboardPage.tsx`: rail bento melebar di 2xl (`2xl:grid-cols-[minmax(0,1fr)_420px]`). Verifikasi browser 2400×1080: konten mengisi lebar tanpa margin kosong (`.local/ui-evidence/dashboard-wide-2400.png`). Gates: tsc/lint/vitest 35/35/build PASS.

### 2026-11-23 — Halaman Profil sendiri (data diri + ubah password)

Permintaan pemilik produk: "ubah password" tidak lagi di sidebar bawah; dibuat halaman `/profile` berisi data diri + ganti password.

Backend: `EmployeeService.selfDetail(userId)` — id employee diturunkan dari principal (bukan input), tanpa capability global; 404 bila akun belum tertaut employee. Endpoint baru `GET /api/v1/me/employee` (`EmployeeSelfController`). `detail()` admin tetap `EMPLOYEE_VIEW`.

Frontend: `features/profile/ProfilePage.tsx` (Data akun: nama/username/email/role/unit/login terakhir; Data kepegawaian: NIP, status, penempatan aktif + riwayat, EmptyState bila belum tertaut; kartu Ubah password). Form diekstrak ke `features/auth/ChangePasswordForm.tsx` dipakai bersama `ChangePasswordPage` (forced change) dan profil; error backend kini menampilkan fieldErrors spesifik ("Password saat ini tidak sesuai."). `fetchMyEmployee` di `auth/api.ts`. Route `profile` + lazy page; breadcrumb "Profil saya" (`navigation.ts` + test). Sidebar `UserFooter` dan chip topbar menjadi link ke `/profile`; link "Ubah password" sidebar dihapus.

Verification (2026-11-23): tsc PASS; lint 0 errors (8 warning lama); vitest 35/35 (+kasus label /profile); build PASS; `mvn -DskipTests compile` PASS; backend restart (proses Java lama menahan :8080, di-kill lalu start ulang). Browser live: profil render via topbar & sidebar, EmptyState employee unlinked (admin), submit password-salah → Alert "Password saat ini tidak sesuai." (password admin TIDAK diubah), screenshot `.local/ui-evidence/profile-desktop.png`. Console error = 404/400 yang diharapkan dari probe tersebut.

### 2026-11-23 — Pengaturan scope akses per unit di halaman Authorization (role detail)

Permintaan pemilik produk: role PIC harus bisa dibatasi per Direktorat/Departemen/Biro. Enforcement backend sudah ada (E4: `matchesUnitScope` = unit tepat ATAU turunan bila `includeDescendants`); yang belum ada adalah UI manajemen assignment dari sisi role. Ditambahkan kartu "Assignment & scope akses" di halaman detail role.

Backend: endpoint baru `GET /api/v1/admin/roles/{roleId}/assignments` (`RoleAdminController` → `RoleAdminService.roleAssignments`, guard `ROLE_MANAGE` global) mengembalikan `RoleHolderView[]` — fakta user (username/displayName/status), scope (GLOBAL/ORGANIZATION_UNIT/WORKSPACE + kode/nama/tipe unit atau nama workspace), `includeDescendants`, masa berlaku, status ACTIVE/REVOKED. Lookup batch user/unit; nama workspace via `WorkspaceLookup.workspaceName` (JDBC, menjaga authorization tidak bergantung entity repository). Assign/revoke memakai endpoint existing (`POST /admin/users/{id}/role-assignments`, `DELETE /admin/role-assignments/{id}`); proteksi last-system-admin tetap berlaku.

Frontend: `features/administration/roles/RoleAssignmentsCard.tsx` — tabel pemegang role (link ke detail user, ScopeCell, badge "Termasuk turunan", berlaku sampai, StatusBadge, tombol Cabut dengan ConfirmDialog yang menjelaskan grant independen tidak terpengaruh), footer hitung aktif/riwayat; dialog assign (zod + react-hook-form): pilih pengguna aktif, scope Unit organisasi/Workspace/Global, unit dikelompokkan per tipe via optgroup (COMPANY→DIRECTORATE→DEPARTMENT→DIVISION + grup "Belum bertipe" untuk unit legacy), checkbox turunan dengan helper contoh kasus PIC per Direktorat, batas waktu opsional, warning untuk scope GLOBAL. `listRoleHolders` di `administration/api.ts`, tipe `RoleHolderView` di `shared/api/types.ts`.

Catatan aturan bisnis (tidak di-bypass): pembuatan Direktorat dummy di bawah Head Office legacy ditolak 400 ("Unit induk belum memiliki tipe organisasi.") dan retype Head Office ditolak 409 ("Tipe unit tidak dapat diubah setelah memiliki anggota aktif."). Verifikasi live memakai unit Operations yang ada; pohon organisasi bertipe akan dibuat lewat seed dummy (#62) dengan root COMPANY baru.

Verification (2026-11-23):

| Gate | Result |
|---|---|
| `mvn -DskipTests compile` | PASS |
| `RoleAdminServiceRoleAssignmentsTest` (baru) | PASS — 5/5 (guard capability, role unknown, mapping unit+turunan, workspace+riwayat revoked, list kosong) |
| `npx tsc --noEmit` / lint / vitest / build | PASS — 0 errors (9 warning lama), 35/35, build OK |
| API live `GET /admin/roles/{PIC}/assignments` | PASS — 200 |
| Browser: assign PIC → Employee One, scope Operations + turunan | PASS — baris "Termasuk turunan · Tanpa batas · Aktif", footer "1 assignment aktif" (`.local/ui-evidence/role-scope-assignment.png`) |
| Browser: Cabut → ConfirmDialog | PASS — baris menjadi "Dicabut", footer "0 assignment aktif · 1 riwayat dicabut" (state dikembalikan) |

### 2026-11-23 — Seed data dummy lengkap untuk testing (semua modul)

Permintaan pemilik produk: isi seluruh aplikasi dengan data dummy untuk testing, termasuk employee dan organisasi. Dibuat `tools/seed-dummy.mjs` (Node native fetch, tanpa dependency baru) yang mengisi via REST API resmi sebagai admin — bukan insert SQL — sehingga seluruh validasi, authorization, audit, dan notifikasi tetap berjalan.

Cara pakai: `SEED_ADMIN_USERNAME=... SEED_ADMIN_PASSWORD=... node tools/seed-dummy.mjs` dari root repo (backend harus berjalan). Kredensial admin tidak di-hardcode; password akun dummy digenerate sekali dan disimpan di `.local/dummy-credentials.json` (gitignored). Skrip idempotent (look-up-before-create per kode/NIP/username/judul) dan non-destruktif; run kedua terverifikasi tidak membuat record baru.

Data yang dibuat (semua berkode/berlabel DUMMY):

- Organisasi bertipe: root COMPANY baru `DUMMY-CO` (unit legacy Head Office/Operations tidak disentuh; retype legacy diblokir aturan bisnis), 2 Direktorat (Operasi; Keuangan & SDM), 2 Departemen, 3 Biro/Division.
- 6 employee (NIP DUMMY-0001..0006, email @example.com) + penempatan otomatis; 4 akun tertaut one-to-one: `dummy.budi`, `dummy.sari`, `dummy.agus`, `dummy.rina` (password final sudah di-set, tanpa forced change saat login).
- Assignment unit PRIMARY + role: semua akun mendapat EMPLOYEE scoped ke biro-nya (pola sama dengan employee1); budi = PIC scoped Direktorat Operasi + turunan (kasus pemilik produk), sari = UNIT_MANAGER scoped Departemen Operasi + turunan, agus = CONTRIBUTOR scoped Workspace Operasi, rina = DOCUMENT_ADMIN global.
- 3 tag dummy; kategori/jenis/klasifikasi memakai master data resmi.
- 2 workspace + 3 folder; 6 dokumen: SOP (2 versi PDF, ACTIVE), laporan CONFIDENTIAL (PDF), IK (TXT), formulir (PDF), memo (versi external-link HTTPS), kontrak RESTRICTED sengaja DRAFT.
- ACL contoh: ALLOW unit Biro Sarana (download, termasuk turunan) + explicit DENY user rina pada laporan confidential.
- 2 template: Formulir Cuti PUBLISHED (+usage VIEW/DOWNLOAD), SOP DRAFT.
- 4 request mencakup semua state: ACCESS fulfilled (ACCESS_GRANTED), DOCUMENT_DELIVERY fulfilled (DOCUMENT_LINKED), ACCESS rejected, ACCESS submitted menunggu.
- 2 distribusi: edaran aktif (2 dokumen, penerima user + unit, expiry +30 hari) dan satu dicabut (REVOKED).

Perbaikan selama eksekusi (kontrak API, bukan bypass): change-password wajib `confirmPassword`; PageResponse memakai `items`/`totalItems`; list template default hanya PUBLISHED sehingga lookup idempotency memeriksa PUBLISHED+DRAFT; akun employee baru tidak otomatis mendapat role EMPLOYEE sehingga seed meng-assign mengikuti pola existing.

Verification (2026-11-23): run pertama SELESAI dengan ringkasan 8 unit / 6 employee / 4 user / 2 workspace / 6 dokumen / 2 template / 4 request / 2 distribusi; run kedua idempotent (0 pembuatan baru, hitungan sama). Browser: halaman role PIC menampilkan Budi Dummy Santoso scoped "Direktorat Operasi (Dummy) · Termasuk turunan" (`.local/ui-evidence/pic-scope-direktorat-dummy.png`); katalog dokumen menampilkan dokumen dummy dengan versi/folder/owner (`dummy-catalog.png`); login `dummy.budi` sukses tanpa forced change, inbox distribusi menampilkan edaran dummy (`dummy-budi-inbox.png`), profil menampilkan NIP + rantai penempatan Direktorat→Departemen→Biro. Sesi browser dikembalikan ke admin.

### 2026-11-23 — Fix pop-up tidak center di semua menu

Keluhan pemilik produk: semua dialog/pop-up menempel di kiri-atas, tidak center (contoh: "Kirim distribusi"). Akar masalah: preflight Tailwind 4 me-reset margin seluruh elemen ke 0, menghapus `margin: auto` bawaan UA yang memusatkan `<dialog>` modal. Fix satu titik di primitif bersama `shared/components/Dialog.tsx`: tambah `m-auto` pada kelas dialog — berlaku untuk semua pop-up (Dialog sm/md/lg dan ConfirmDialog) di seluruh fitur. Drawer navigasi mobile (`Sidebar.tsx`) sudah eksplisit `m-0` sehingga tidak terdampak.

Verification (2026-11-23): browser live 1440×900 — dialog "Kirim distribusi" (lg) center sempurna (offset X/Y = 0, muat di viewport, `.local/ui-evidence/dialog-centered-distribusi.png`); 390×844 — center sempurna (`dialog-centered-mobile.png`); ConfirmDialog "Cabut assignment role" (sm) center (offset −8px horizontal = setengah lebar scrollbar halaman, perilaku centering native terhadap area terlihat); drawer mobile tetap rata kiri 0,0 280×844; data tidak berubah (Batal). Gates: tsc PASS, lint 0 errors (9 warning lama), vitest 35/35, build PASS.

### 2026-11-23 — Buka proteksi role sistem (edit permission + status)

Permintaan pemilik produk: "buka perlindungan role sistem". Perubahan (DEC-073):

- Backend `RoleAdminService`: lock `setPermissions` (403 "Permission role sistem utama dikelola oleh aplikasi…") dan lock `updateRole` (403 "Role sistem utama tidak dapat dinonaktifkan.") dihapus. Permission dan status seluruh role sistem — termasuk SYSTEM_ADMIN dan ADMINISTRATOR — kini dapat diubah melalui API.
- Frontend `RoleDetailPage`: editor permission tidak lagi terkunci untuk role sistem (checkbox, pilih-semua-modul, Kembalikan, Simpan aktif); alert peringatan diganti alert info "Role sistem dapat dikelola" dengan pengingat menjaga minimal satu administrator aktif. Badge "Role sistem" tetap sebagai penanda informasi.
- Frontend `RolesPage`: select status pada dialog "Ubah role" tidak lagi disabled untuk SYSTEM_ADMIN.
- Guard yang dipertahankan (anti-lockout, bukan proteksi edit): administrator terakhir tetap tidak dapat mencabut assignment governance milik sendiri (403). Desain governance konten tidak berubah: assignment global SYSTEM_ADMIN/ADMINISTRATOR tetap tidak memberi capability konten (DEC-070).
- Test baru `RoleAdminServiceSystemRoleTest` (4 test): edit permission role sistem sukses, deaktivasi role sistem sukses, self-revoke admin terakhir diblokir, self-revoke allowed bila masih ada assignment governance lain.

Verification (2026-11-23): unit test backend `RoleAdminServiceSystemRoleTest` + `RoleAdminServiceRoleAssignmentsTest` PASS; tsc PASS; lint 0 errors (9 warning lama); vitest 35/35; build frontend PASS. Live API sebagai admin: PUT permission SYSTEM_ADMIN tanpa `template.view` → 200 (sebelumnya 403), kode hilang dari response; restore set semula → 200, 40 permission identik dengan kondisi awal. Browser live: halaman detail SYSTEM_ADMIN menampilkan 51 checkbox aktif (0 disabled) + alert info; siklus UI penuh uncheck `template.view` → Simpan → header "39 dari 40 permission" → recheck → Simpan → "40 dari 40 permission" (state kembali persis); dialog "Ubah role" SYSTEM_ADMIN menampilkan select status aktif (ACTIVE), ditutup tanpa perubahan (`.local/ui-evidence/role-system-unlocked.png`).

Residual risk: administrator kini dapat mencabut permission manajemen role dari role governance atau menonaktifkannya; kesalahan konfigurasi dapat menghilangkan akses administrasi. Guard self-revoke admin terakhir tetap mencegah lockout via pencabutan assignment, tetapi tidak mencegah salah edit permission/status — alert info di UI memperingatkan hal ini.

### 2026-11-23 — Audit keamanan menyeluruh (code-security) + fix temuan

Audit penuh terhadap backend dan frontend mengikuti kategori OWASP (SQLi, command injection, XSS, XXE, path traversal, deserialisasi, secrets, crypto, transport, SSRF, CSRF, race condition).

Area yang diperiksa dan dinyatakan bersih (bukti baca kode/grep, bukan asumsi):

- SQL: seluruh query JDBC parameterized (`?`); tidak ada string concatenation input pengguna; JPA/Hibernate untuk sisanya.
- Command injection / deserialisasi / XXE: tidak ada `Runtime`/`ProcessBuilder`/`ObjectInputStream`/parser XML pada kode aplikasi.
- SSRF: backend tidak pernah melakukan fetch URL eksternal; tautan Office hanya dicatat (parseHttpsUrl: wajib https, host valid, tanpa userInfo).
- XSS: tidak ada `dangerouslySetInnerHTML`/`innerHTML`/`eval`; allowlist file menolak html/svg sehingga tidak ada stored XSS via preview inline; endpoint konten memakai `X-Content-Type-Options: nosniff` + `Cache-Control: private, no-store`.
- Path traversal: FilesystemStorageAdapter normalize + startsWith(root); object key dibuat backend (UUID), bukan nama file asli; sanitizeFilename membuang path/control char.
- Secrets: tidak ada credential hardcode di kode utama; konfigurasi via environment; `.env` gitignored; log tidak memuat password/token/signed URL (BootstrapAdminInitializer hanya mencatat panjang password).
- AuthN/AuthZ: BCrypt(12); login timing-safe (dummy hash untuk user tak dikenal); lockout 5 gagal/15 menit; proteksi session fixation (invalidate+recreate saat login); AccountStateFilter memvalidasi ulang akun tiap request dan menegakkan mustChangePassword di backend; default deny + scope + ACL + explicit deny di backend; IDOR teruji (AclIntegrationTest, AuthorizationIntegrationTest).
- CSRF: CookieCsrfTokenRepository + header SPA, retry otomatis di client saat token basi; request tanpa token ditolak (test eksplisit).
- Error contract: GlobalExceptionHandler tidak membocorkan stacktrace/SQL/path; `server.error.include-message/stacktrace=never`; actuator hanya health,info.
- Frontend: tidak ada penyimpanan token di localStorage (hanya flag sidebar); external link dibuka `noopener,noreferrer`.

Temuan dan fix (keduanya diimplementasikan, bukan sekadar laporan):

1. MEDIUM — Ganti password mandiri tidak menginvalidasi sesi lain. Reset password admin sudah menginvalidasi seluruh sesi target, tetapi `AuthenticationService.changePassword` membiarkan sesi lain tetap hidup — sesi yang dicuri tetap valid setelah korban mengganti password. Fix: `changePassword` menerima `HttpServletRequest`, lalu `invalidateOtherSessions(username, currentSessionId)` menghapus semua sesi Spring Session JDBC milik user kecuali sesi yang sedang dipakai (sesi pemohon tetap hidup, UX tidak berubah). `AuthController` meneruskan request.
2. LOW — Label provider tautan eksternal memakai `host.endsWith("sharepoint.com")` sehingga host penipu seperti `evilsharepoint.com` dilabeli SHAREPOINT (kosmetik; tidak ada dampak authorization karena backend tidak pernah fetch URL). Fix: `matchesDomain(host, domain)` dengan batas titik (`host.equals(domain) || host.endsWith("." + domain)`); `docs.google.com` tetap exact-match.

Infrastruktur test: Docker tidak lagi tersedia di lingkungan dev, sehingga `IntegrationTestSupport` mendapat fallback opt-in PostgreSQL lokal via `KCIC_TEST_DB_URL`/`KCIC_TEST_DB_USER`/`KCIC_TEST_DB_PASSWORD` (default tetap Testcontainers bila env tidak di-set). Database test khusus `kcic_connect_test` dibuat terpisah dari DB dev; Flyway menerapkan 19 migrasi dari kosong saat test berjalan. Ini juga menyelesaikan strategi integration-test tanpa Docker untuk task #55.

Test baru: `AuthenticationIntegrationTest.selfPasswordChangeInvalidatesOtherSessionsButKeepsCurrent` (dua client login user sama; ganti password di A; `/auth/me` A=200, B=401; password lama ditolak, password baru diterima) dan `DocumentVersionServiceProviderCodeTest` (2 test: domain/subdomain sah terlabel benar; host lookalike — evilsharepoint.com, sharepoint.com.attacker.net, fake1drv.ms, docs.google.com.evil.io — semuanya EXTERNAL). `providerCode` diubah dari private menjadi package-private agar dapat diuji langsung tanpa membocorkannya ke API.

Verification (2026-11-23): full suite backend terhadap PostgreSQL lokal — 101 test, 0 failure, 0 error, BUILD SUCCESS (mencakup seluruh integration test authorization/ACL/IDOR/expiry/revoke yang sebelumnya BLOCKED tanpa Docker). Verifikasi live terhadap backend berjalan: user uji sementara dua sesi → ganti password di sesi A (204) → sesi A tetap 200, sesi B langsung 401; kedua user uji dinonaktifkan setelah verifikasi (non-destruktif). Frontend tidak berubah pada batch ini.

Residual risk tingkat deployment (bukan defect kode; dicatat untuk UAT/deployment):

- CSP belum dipasang di lapisan static host/reverse proxy — direkomendasikan saat deployment produksi.
- `KCIC_COOKIE_SECURE` default false untuk dev HTTP; wajib true di produksi HTTPS.
- Belum ada rate limiting per-IP di lapisan gateway (lockout akun per-username sudah ada); brute-force tersebar lintas akun dimitigasi di gateway saat produksi.
- Kebijakan password berbasis panjang (min 12) tanpa daftar password bocor; dapat diperkuat pasca-pilot.
- Antivirus/malware scanning di luar scope pilot (keputusan terkunci); validasi ekstensi/MIME/magic-byte bukan pengganti malware scanning — jangan melabeli file "aman dari malware".

### 2026-11-23 — Rebrand KCIC Connect → KCIC Docs

Keputusan pemilik produk (DEC-074): nama produk menjadi **KCIC Docs**. Perubahan branding menyeluruh: seluruh string UI (sidebar, halaman login, ganti password, topbar/footer AppShell, fallback RequireAuth/pages, title + meta index.html, aria-label favicon, catatan tautan eksternal di detail dokumen), 30+ dokumen markdown (AGENTS/PRD/ARCHITECTURE/SCHEMA/DESIGN_SYSTEM/IMPLEMENTATION_PLAN/README/docs/*), direktori `design-system/kcic-connect` → `design-system/kcic-docs`, `spring.application.name` → `kcic-docs`, `kcic.app.name` → `KCIC Docs`, artifact/finalName Maven → `kcic-docs-backend`/`kcic-docs`, package npm → `kcic-docs-frontend`, nama project docker-compose → `kcic-docs`, komentar seed tool. Repo GitHub diganti nama menjadi `zulazu/KCIC-DOCS` dan remote lokal diperbarui.

Identifier teknis internal dipertahankan (lihat DEC-074): package Java `com.kcic.connect`, DB `kcic_connect`, cookie `KCIC_SESSION`, env `KCIC_*`, bucket `kcic-connect-documents`. Tidak ada perubahan perilaku API, skema, atau authorization pada batch ini.

Verification (2026-11-23): frontend — tsc PASS, lint 0 errors (9 warning lama), vitest 35/35, build PASS. Backend — full suite 101 test, 0 failure, BUILD SUCCESS (exit Maven 0) terhadap PostgreSQL lokal; run ulang membuktikan idempotency setelah `AuditIntegrationTest` diperbaiki memakai `uniqueName` (sebelumnya username tetap `audit_viewer` bentrok dengan sisa run pertama di DB test persisten — isu isolasi test, bukan defect produk). Browser live: title tab "KCIC Docs", halaman login "KCIC DOCS" (`.local/ui-evidence/kcic-docs-login.png`), sidebar maroon "KCIC DOCS / Workspace dokumen" + breadcrumb "KCIC Docs / Dashboard" + footer "KCIC Docs — pilot internal" (`.local/ui-evidence/kcic-docs-dashboard.png`). Backend restart: `/api/v1/version` mengembalikan `{"name":"KCIC Docs","version":"1.0.0"}`. Repo GitHub terverifikasi `zulazu/KCIC-DOCS` (private) dan remote lokal diperbarui. Catatan operasional: port 5173 sempat diduduki proses aplikasi lain tak terkait ("ERY — Admin"); proses tersebut hilang dan dev server KCIC Docs dijalankan ulang normal.

### 2026-11-23 — Setup lokal tanpa Docker: README + .env.example + harness test (task #55 selesai)

README ditulis ulang local-first: prasyarat PostgreSQL native 16+ (terverifikasi 18.6), Docker menjadi opsional; langkah `CREATE ROLE kcic` + `CREATE DATABASE kcic_connect OWNER kcic`, `.env` default `KCIC_STORAGE_PROVIDER=filesystem`, cara menjalankan backend/frontend, dan bagian terpisah untuk infrastruktur container opsional. Quality gates mendokumentasikan dua jalur integration test: `KCIC_TEST_DB_URL`/`KCIC_TEST_DB_USERNAME`/`KCIC_TEST_DB_PASSWORD` (DB test khusus `kcic_connect_test`, tanpa Docker) atau Testcontainers bila variabel tidak di-set. `.env.example` disusun ulang mengikuti setup aktual: DB native 5432, filesystem storage dengan path absolut, blok MinIO/docker-compose dikomentari sebagai opsional, blok variabel test disertakan.

Perbaikan harness: nama env username test disamakan dengan konvensi lain menjadi `KCIC_TEST_DB_USERNAME` (sebelumnya `KCIC_TEST_DB_USER`, tidak konsisten dengan dokumentasi). Kepemilikan objek di `kcic_connect_test` dialihkan ke role `kcic` (ALTER SCHEMA/TABLE/SEQUENCE OWNER — non-destruktif) agar command yang terdokumentasi berjalan dengan role aplikasi, bukan superuser.

Verification (2026-11-23): command persis seperti README (`mvn -f backend/pom.xml test` dari root repo dengan `KCIC_TEST_DB_*` role kcic) — AuthenticationIntegrationTest 6/6 PASS; full suite 101 test, 0 failure, BUILD SUCCESS, exit Maven 0. Sisa task pending: hanya #39 (E6 cutover) yang tetap BLOCKED menunggu dataset HR terverifikasi dari pemilik produk dan resolusi konflik fixture UAT-CO; tooling preflight/validator/rehearsal sudah tersedia (rehearsal r3 PASS).

### 2026-11-23 — Excel employee (import/template/export) + Position/Jabatan (task #68, #70)

**Excel employee (DEC-075).** Backend: Apache POI 5.4.1; `GET /admin/employees/import-template` (sheet Data + Petunjuk), `POST /admin/employees/import` (multipart, validasi atomik semua baris, 5 MB/1000 baris/64 kolom/100 error, sel formula ditolak, NIP/email unik case-insensitive), `GET /admin/employees/export` (mengikuti filter list). Frontend: tombol Template Excel / Import Excel / Export Excel di halaman Employee + dialog import dengan daftar error per baris dan bantuan format.

**Position/Jabatan (DEC-076, keputusan user "Atribut employee + master data").** Migrasi `V20__position_master.sql`: katalog `master.positions` (kode unik case-insensitive, seed idempotent 6 jabatan baseline) + `identity.employees.position_id` nullable FK. Hierarki organisasi tidak berubah (tetap Direktorat→Departemen→Biro/Division). Backend: CRUD katalog gated `master_data.manage` dengan audit `POSITION_CREATED`/`POSITION_UPDATED`; lookup `GET /master-data/positions` (aktif saja) untuk form; assignment baru wajib ACTIVE, mempertahankan posisi yang dinonaktifkan boleh, clear NULL boleh; kolom `position_code` opsional di import (format legacy 5 kolom tetap diterima); export menambah `position_code`/`position_name`. Frontend: submenu Organisasi → Jabatan (`/admin/organization/positions`, gated `master_data.manage`), tab Master Data → Jabatan, select Jabatan di form buat/ubah employee, kolom Jabatan di tabel employee, baris Jabatan di detail employee dan halaman Profil.

Verification (2026-11-23):
- Backend targeted: EmployeeExcelIntegrationTest 8 test, PositionIntegrationTest 3 test, ExcelSheetReaderTest 6 test — hijau. Full suite: 118 test / 23 kelas, 0 failure, BUILD SUCCESS (exit 0), V20 tervalidasi di `kcic_connect_test` dan DB aplikasi.
- Frontend: typecheck PASS, lint 0 error, vitest 35/35, build PASS.
- Browser (admin): halaman Jabatan menampilkan 6 seed; tab Master Data → Jabatan berfungsi; ubah employee Budi Dummy → Jabatan "Manager" tersimpan dan tampil di detail, kolom tabel employee, dan profil `dummy.budi` (login ulang diverifikasi). Bukti: `.local/ui-evidence/position-catalog.png`, `employee-detail-jabatan.png`, `profile-jabatan.png`.

Batas/residual: import Excel bukan jalur sinkronisasi HR otomatis; jabatan bersifat atribut deskriptif dan tidak memengaruhi ACL/authorization. Task #69 (export modul lain + analisis kandidat import tambahan) masih terbuka.
