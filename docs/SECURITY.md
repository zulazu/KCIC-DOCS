# KCIC Connect — Security Controls and Residual Risk

Scope: pilot deployment of KCIC Connect (internal web application, local accounts). This document records **implemented controls with code evidence**, the **protected boundaries**, and the **residual risks accepted for the pilot**. It does not replace `AGENTS.md §8`, `ARCHITECTURE.md §8/§12`, or a formal security assessment.

## 1. Authentication

| Control | Implementation |
|---|---|
| Local accounts only, admin-provisioned | `identity` module; no self-registration endpoint exists |
| Password hashing | BCrypt cost 12 (`shared` `CoreBeans`); no plaintext or reversible storage |
| Password policy | Minimum length 12 (`kcic.security.password-min-length`), must not equal username/email; enforced server-side |
| Temporary password flow | Admin-create/reset sets `must_change_password`; all routes except `/auth/me`, logout, and change-password are blocked until changed |
| Login lockout | 5 failed attempts → 15-minute lock (`kcic.security.login-*`), generic invalid-credential message, both login outcomes audited |
| Session fixation | Session invalidated and re-created on successful login |
| Account state | `AccountStateFilter` rejects requests from INACTIVE/LOCKED accounts with 401/403 and invalidates their sessions on deactivation |

## 2. Session and CSRF

- Server-side sessions in PostgreSQL (Spring Session JDBC), 30-minute idle timeout.
- Cookie: `KCIC_SESSION`, `HttpOnly`, `SameSite=Lax`, `Secure` controlled by `KCIC_COOKIE_SECURE` (MUST be `true` behind HTTPS in any shared environment).
- CSRF: Spring Security `CookieCsrfTokenRepository` with `X-XSRF-TOKEN` header; the SPA reads the `XSRF-TOKEN` cookie through the central API client. Login itself is CSRF-protected (token is fetched before submit).
- Logout invalidates the server session and deletes the cookie.

## 3. Authorization

- **Backend is the authority.** Every controller calls an application service that re-checks capability, unit scope, resource ACL, resource status, classification policy, and expiry. Navigation hiding is UX only (DEC-015).
- Default deny; the `authorization` module owns capability resolution (role → permission), unit-scoped assignments, and resource grants.
- **ACL precedence:** explicit DENY in any applicable scope (document, folder ancestry, workspace) wins over every allow, including global role capabilities. Revoking one grant leaves independent grants from other sources active (DEC-032, DEC-044).
- **No IDOR by UUID:** unknown, archived-invisible, or out-of-scope resources return 404 (document/template/distribution); explicitly denied access returns `403 RESOURCE_FORBIDDEN` without disclosing existence (DEC-017).
- SYSTEM_ADMIN does not implicitly get document content access (DEC-025).
- Distribution delegation is bounded by what the sender itself holds (DEC-043); rows are provenance-linked so revocation cannot remove independent grants.
- Audit trail read requires `audit.view` (AUDITOR/SYSTEM_ADMIN); all other roles get `403` (verified in the browser and by `AuditIntegrationTest`).

## 4. File and storage security

- Storage adapter port with S3-compatible (MinIO) and filesystem (dev/test) implementations; the bucket is private and no presigned/public URL is ever issued. Content is streamed through the API after authorization (DEC-002, DEC-024).
- Object keys are backend-generated random UUIDs; the original filename is stored only as metadata and used for `Content-Disposition` (AGENTS §8.15).
- Uploads are validated backend-side in this order: extension allowlist and declared MIME against the active file policy, size ceiling, magic-byte signature sniffing, then storage write (DEC-026).
- SHA-256 checksum stored per version; binaries are immutable and versioned.
- Response headers for content: `Cache-Control: private, no-store`, `X-Content-Type-Options: nosniff`, controlled `Content-Disposition`.
- External Office links: HTTPS only, host recorded, normalized; KCIC Connect makes **no** claim about permission changes at the external provider (AGENTS §3).
- Compensating delete on metadata-persistence failure; failed compensation leaves an identifiable orphan for operators.

## 5. Input, API, and error handling

- Bean validation + service validation; frontend validation only for UX.
- Explicit DTOs; JPA entities, storage keys, and password hashes are never serialized.
- Consistent error envelope with `code`, safe message, optional `fieldErrors`, and `requestId`; stack traces and SQL are suppressed (`server.error.include-*: never`).
- CSV/HTML/SQL injection surfaces are avoided (no dynamic SQL string building; React escapes output by default; no raw HTML rendering of user content).
- Idempotency/duplicate protection: workflow transitions re-validated in one transaction (409 on repeats), optimistic locking (`@Version`) on workspace/folder metadata, duplicate ACL grant → 409.
- Multipart limits: 30 MB file, 32 MB request; policy ceiling 25 MB default.

## 6. Audit and logging

- Business audit (`audit.audit_events`) records LOGIN/LOGOUT/login-failure, user/role/unit administration, workspace/folder/document/version/ACL, request transitions, distribution, template governance/usage, and password changes.
- Audit metadata is a safe map only: ids, statuses, version numbers, reasons/notes where appropriate. No passwords, hashes, session ids, cookies, tokens, signed URLs, storage keys, or binary content (AGENTS §8.18).
- Application logs avoid secrets and personal data; the request id is correlated to the error response.
- Audit reads are filterable by actor/action/resource/outcome/date range and paginated server-side; audit rows are never editable from the application.

## 7. Secrets and configuration

- All configuration is environment-driven (`KCIC_*`); `.env` is gitignored; `.env.example` contains placeholders only.
- No credential, bootstrap password, storage key, or session secret is committed. The bootstrap admin is applied once and must change its password (DEC-013).
- Object storage credentials are only present in the backend process environment.

## 8. Verified controls (evidence)

Executed during implementation; details in `docs/PROGRESS.md`:

- Unauthenticated API access → 401; employee calling admin endpoints → 403 `ACTION_FORBIDDEN`.
- Cross-unit document read/write without ACL → 403/404; IDOR probes with foreign UUIDs → 404.
- Access after revoke (distribution and manual grant): next API call denied; independent grants survive.
- Expiry: future-only validation, lazy expiry on read; expired grants no longer authorize.
- Inactive account: login blocked, existing session invalidated.
- CSRF: state-changing request without `X-XSRF-TOKEN` → 403.
- Employee/administrator without document relation cannot read unowned document content (DEC-025 test).
- File upload: renamed executable rejected by magic-byte check; oversized file rejected; disallowed extension/MIME rejected.
- Browser UAT: employee, auditor, and administrator role separation verified for audit trail and admin pages.

## 9. Residual risks accepted for the pilot

| Risk | Status / mitigation |
|---|---|
| **No antivirus/malware scanning** | Deferred by product decision. File validation is structural only; never label uploads "malware-free". Operational acceptance must acknowledge this. |
| **No full-text/OCR content search** | Metadata search only; content is not indexed. |
| **No SSO/MFA** | Local password authentication only; admin-managed accounts and lockout are the compensating controls. |
| **TLS terminated externally** | The application server serves HTTP; TLS, HSTS, and `KCIC_COOKIE_SECURE=true` MUST be provided by the deployment edge (section 10 of `DEPLOYMENT.md`). |
| **No centralized logging/monitoring** | Local logs only; operators must ship logs and watch `/actuator/health`. |
| **Lazy expiry/read-time revocation** | An already-open page keeps its last downloaded bytes; the next API call is re-authorized (DEC-012/DEC-024). Distributed revocation of downloaded copies is out of scope. |
| **In-memory visibility filtering** | Catalog visibility filters ≤1000 candidates per read (DEC-029/DEC-050); correctness is maintained at pilot volumes. |
| **External provider permissions** | Office/SharePoint links are recorded only; KCIC Connect cannot revoke or audit access at the provider. |
| **Dev filesystem storage** | `KCIC_STORAGE_PROVIDER=filesystem` is for local development; production must use the private S3/MinIO bucket. |
| **Backup encryption/key custody** | Backups are operator-managed; encrypt at rest and control key custody outside this repository. |

## 10. Security operations checklist

- [ ] `KCIC_COOKIE_SECURE=true` and HTTPS-only ingress in front of the app.
- [ ] Strong, unique `KCIC_DB_PASSWORD`, `KCIC_STORAGE_ACCESS_KEY`/`SECRET_KEY`; rotated and stored in the platform secret store.
- [ ] Bootstrap admin variables used once, then removed from the environment.
- [ ] Bucket private; no bucket policy granting anonymous read; lifecycle/versioning per storage policy.
- [ ] Database and bucket backups scheduled, restore drill repeated (see `DEPLOYMENT.md §6`).
- [ ] Reverse proxy sets `X-Forwarded-Proto`/`Host` correctly and forwards the same `X-Request-Id` policy.
- [ ] Review `audit.audit_events` for DENIED/FAILURE spikes after go-live.
- [ ] Antivirus residual risk explicitly accepted by the business owner in the UAT sign-off.
- [ ] Zero accounts without `employee_id`; no V18 exception (DEC-068).
- [ ] No unit/placement seeded synthetically in shared environments; HR import verified.

## 11. Employee identity and organization scope (E-series controls)

| Control | Requirement |
|---|---|
| Employee is master data | `identity.employees` stores NIP, full name, email, employment status. User accounts do not duplicate these fields after E3; they may not override them. |
| One account per employee | `users.employee_id` UNIQUE enforces 1:1; linking a second account or relinking an already-linked employee returns `409`. |
| One active placement | Partial unique index on `employee_placements(employee_id) WHERE is_active` enforces at DB level; transfers close the previous row in the same transaction. |
| Biro/Division-only placement | Service validation requires an ACTIVE `DIVISION`; malformed/typed hierarchy (e.g. placement on DEPARTMENT) returns `400`, never silently accepted. |
| Derived scope | Directorate/Department are derived from the Biro/Division ancestry at authorization time; clients cannot submit directorate/department scope for a placement. |
| Separate lifecycles | Employee termination and account deactivation are separate audited actions. The backend denies login (`ACCOUNT_DISABLED`, audit `LOGIN_DENIED` reason `EMPLOYMENT_INACTIVE`), account creation for non-ACTIVE employees, linking an ACTIVE account to a non-ACTIVE employee, reactivation/unlock while the employee is non-ACTIVE, new role/ACL grants, and distribution to INACTIVE/TERMINATED employees; an employment status change away from ACTIVE invalidates existing sessions and records `EMPLOYEE_ACCESS_SUSPENDED`. A TERMINATED employee with an ACTIVE account remains a policy defect and is detected by `tools/ops/run-employment-check.sh` (E8-OPS-01), which exits `1` when the gate is `BLOCKED`. |
| Authorization remains backend | Employee endpoints, placement changes, account linking, and descendant grants all re-check capability + scope server-side. Frontend capability snapshots only hide UI. |

### 11.1 Descendant scope is opt-in and never widens legacy grants

- `include_descendants` defaults to `FALSE`; V14–V18 add the column without backfilling `TRUE` (DEC-061/DEC-062).
- Existing ACL rows and unit-scoped role assignments therefore keep exact-match semantics; users do not gain access to child/sibling units after the migration.
- Enabling descendants is an explicit, audited administrator action on a specific grant; it is only valid on unit principals/scopes.
- Explicit DENY at any applicable unit (including ancestor/descendant) still wins over descendant-derived ALLOW.
- Revoking one grant leaves other independent grants intact (provenance preserved).
- API surface cannot widen via omission: missing `includeDescendants` is treated as `false`.

### 11.2 Risks introduced by the E-series and their controls

| Risk | Control |
|---|---|
| Privilege escalation by enabling descendants broadly | Permission-gated, audited per grant; UI warns; no bulk "enable all" in pilot; acceptance test verifies old grants remain exact |
| Fabricated or imported-wrong NIP/placement | Only verified HR data; import preflight; zero synthetic data in shared env (DEC-064) |
| Terminated employee retains login | Explicit account deactivation policy + operational query; audit event on termination |
| Legacy `/users/{id}/units` and placements diverge | Legacy membership writes disabled for employee-linked users once placements are authoritative; E6-INFO lists active legacy rows on linked accounts and requires a per-row decision (no bulk delete) before cutover |
| In-memory catalog filtering with descendant scopes | Bounded depth (≤4) and page-level expansion; correctness gate in authorization tests; performance limitation documented |
| Bootstrap/system account without employee | No V18 account exception is approved (DEC-068). Every account must link to a verified Employee; a future non-human identity needs a separate enforcement design and an explicit decision. Go-live gate is zero unlinked accounts. |

## 12. Security test additions required before E8 sign-off

Status as of E8 (automated coverage in `backend/src/test/java`):

- [x] Employee with INACTIVE/TERMINATED status cannot authenticate or receive new grants — `EmploymentLifecycleSecurityIntegrationTest` (login `ACCOUNT_DISABLED`, role assignment and ACL grant rejected with `status kepegawaian` field error; status change ends sessions).
- [x] Account linked to one employee cannot be relinked; second User for same employee rejected by DB and API — `EmployeeAccountIntegrationTest`.
- [x] Account creation is rejected for a non-ACTIVE employee, an ACTIVE account cannot be linked to a non-ACTIVE employee (deactivate it first), and reactivation/unlock is rejected while the linked employee is non-ACTIVE — `EmployeeAccountIntegrationTest.accountCannotBeCreatedForNonActiveEmployee`, `activeAccountCannotLinkToNonActiveEmployeeButInactiveAccountCan`, and `accountCannotBeReactivatedWhileEmployeeIsNonActive`.
- [x] Second concurrent active placement rejected (DB partial unique) and reported as `409` — `EmployeeIntegrationTest.databaseRejectsSecondActivePlacement`.
- [x] Placement on non-DIVISION / inactive unit rejected — `EmployeeIntegrationTest.placementRequiresActiveDivision`.
- [x] Grant with `includeDescendants=true` on a Directorate allows access to a descendant Biro/Division; the same grant on an unrelated sibling denies — `DescendantScopeIntegrationTest`.
- [x] Explicit DENY on child Biro/Division overrides inherited/descendant ALLOW — `DescendantScopeIntegrationTest.explicitDenyAtChildUnitBeatsDescendantAllow`.
- [x] Legacy grant created before V17 gains no access to descendants after upgrade — `DescendantScopeIntegrationTest.legacyUnitGrantDoesNotWidenToDescendantPlacement`.
- [x] Revoking descendant grant does not revoke independent direct grant — `AclIntegrationTest.revokingOneGrantKeepsIndependentGrantIntact` (workspace ALLOW survives revoke of the document-level grant and vice versa).
- [x] IDOR probes on `/admin/employees/{uuid}` from unauthorized scopes return `404`/`403` — `EmploymentLifecycleSecurityIntegrationTest.employeeEndpointsDenyAccountsWithoutAdminCapability` (capability guard returns `403`).
- [x] Distribution excludes INACTIVE/TERMINATED employees and keeps snapshots after transfer — `DistributionIntegrationTest`.
- [ ] Operational check E8-OPS-01 executed against a production-like dataset — script committed (`tools/ops/employment_lifecycle_check.sql`); dev run returns 0 violations (`docs/organization-identity/E8_OPERATIONAL_CHECK_DEV_2026-11-22.txt`); pending E6 HR data.

Full backend suite at E8 + E6 hardening: 84 tests, 0 failures (`mvn -f backend/pom.xml test`). Frontend gates unchanged from E7 (typecheck, lint, 13 tests, build green).
