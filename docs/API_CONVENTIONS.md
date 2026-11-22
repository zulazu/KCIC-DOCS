# KCIC Connect — API Conventions

Baseline prefix: `/api/v1`. JSON request/response (multipart only for uploads). Resource identifiers are UUIDs.

## 1. Success responses

| Situation | Status | Body |
|---|---|---|
| Read single resource | 200 | Resource DTO |
| List | 200 | `{ "items": [...], "page": 0, "size": 20, "totalItems": 123, "totalPages": 7 }` |
| Create | 201 | Created DTO + `Location` header |
| Update / state transition | 200 | Updated DTO |
| Delete/revoke (no body) | 204 | empty |
| Async-less command with result | 200 | Command result DTO |

Pagination query: `page` (zero-based, default 0), `size` (default 20, max 100), `sort` (`field,asc|desc`) with an explicit whitelist per endpoint. Unknown sort fields produce `400 VALIDATION_ERROR`.

Dates: ISO-8601. `DATE` fields as `YYYY-MM-DD`; instants as ISO-8601 UTC (`2026-11-22T10:15:30Z`). All timestamps are stored as `TIMESTAMPTZ` UTC.

## 2. Error contract

```json
{
  "code": "RESOURCE_FORBIDDEN",
  "message": "Anda tidak memiliki akses ke resource ini.",
  "fieldErrors": [{ "field": "title", "message": "Judul wajib diisi." }],
  "requestId": "8f2c0f7d-..."
}
```

- `code`: stable machine-readable code (never a stack trace, SQL, path, bucket name, or secret).
- `message`: safe, user-facing Indonesian message.
- `fieldErrors`: optional, for bean validation and domain field errors.
- `requestId`: echoed from `X-Request-Id` or generated per request; also returned as response header.

### Error codes

| HTTP | Code | Use |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Malformed/invalid input, bad filter/sort |
| 401 | `UNAUTHENTICATED` | No/invalid session |
| 403 | `RESOURCE_FORBIDDEN` / `ACTION_FORBIDDEN` | Authenticated but not allowed; does not reveal protected resource existence |
| 404 | `RESOURCE_NOT_FOUND` | Not found **or** not permitted to know it exists |
| 409 | `CONFLICT` | Duplicate code/name, stale version, invalid state transition, double submit |
| 413 | `FILE_TOO_LARGE` | Upload exceeds policy |
| 415 | `UNSUPPORTED_FILE_TYPE` | Extension/MIME/signature rejected |
| 429 | `TOO_MANY_ATTEMPTS` | Login attempt policy |
| 500 | `INTERNAL_ERROR` | Unexpected failure with request ID |

## 3. Idempotency and concurrency

- Metadata updates use optimistic locking (`rowVersion`). Mismatch → `409 CONFLICT`.
- Workflow transitions validate the previous status inside the transaction; double approve/fulfill → `409`.
- Distribution send and request fulfillment run in one database transaction; object storage is orchestrated with compensation.

## 4. Authentication and CSRF

- Session cookie: `KCIC_SESSION` (`HttpOnly`, `SameSite=Lax`, `Secure` configurable).
- `GET /api/v1/auth/csrf` primes the `XSRF-TOKEN` cookie (not HttpOnly) and returns the token for SPA use.
- Every mutating request must send `X-XSRF-TOKEN`. Missing/invalid token → `403 CSRF`.
- `GET /api/v1/auth/me` returns current user, roles, permissions and capability summary; `401` when unauthenticated.
- Account with `mustChangePassword=true` may only call `/auth/me`, `/auth/csrf`, `/auth/change-password`, `/auth/logout`.

## 5. Authorization signals

- Backend is authoritative. Frontend capability snapshot only controls visibility.
- Unauthorized access to a resource that the caller must not know exists returns `404`; access to a known but forbidden action returns `403`.

## 6. File transfer

- Upload: `POST /api/v1/documents/{id}/versions` and `POST /api/v1/requests/{id}/attachments` as `multipart/form-data` with an `file` part.
- Download: `GET /api/v1/documents/{id}/content` and `GET /api/v1/documents/{id}/versions/{versionId}/content`.
- Backend streams content after re-authorization (no public/permanent URL, no storage credentials in browser). `Content-Disposition` is built from a sanitized filename.
- Preview: `GET .../content?disposition=inline` only for types allowed by the preview policy; otherwise `415` and the UI offers download.

## 7. Organization and employee administration (E-series, additive)

New endpoints are added under `/api/v1/admin` and require `employee.manage` or the existing organization capability as noted. Existing user/unit endpoints remain available during transition and do not change contract.

| Method | Path | Permission | Purpose |
|---|---|---|---|
| GET | `/api/v1/admin/organization-units?unitType=DIVISION` | `organization.manage` | List typed units; `unitType` optional |
| POST | `/api/v1/admin/organization-units` | `organization.manage` | Create typed unit; body includes `unitType`, `parentId` (validated by type shape) |
| PUT | `/api/v1/admin/organization-units/{id}` | `organization.manage` | Rename/status; changing `unitType` only before dependent data exists |
| GET | `/api/v1/admin/organization-units/{id}/members` | `organization.manage` | Active members: legacy assignments plus placement-derived linked accounts (`assignmentId` = placement id, type `PRIMARY`) (E7) |
| GET | `/api/v1/admin/users/{id}/units` | `user.manage` (self allowed) | Active units; for linked accounts an active Biro/Division placement is added as `PRIMARY` (E7) |
| POST | `/api/v1/admin/users/{id}/units` | `organization.manage` | Legacy assignment create; frozen with `409 CONFLICT` for accounts that have `employee_id` (E7) |
| DELETE | `/api/v1/admin/users/{id}/units/{assignmentId}` | `organization.manage` | End a legacy assignment; still allowed for linked accounts so stale rows can be retired (narrowing only) |
| GET | `/api/v1/admin/employees?page&size&sort&query&directorateId&departmentId&divisionId&employmentStatus` | `employee.view` | Server-side paged employee list with derived placement |
| GET | `/api/v1/admin/employees?...&linked=true|false` | `employee.view` | Same list filtered by account coverage; each item carries `hasAccount` (E5) |
| GET | `/api/v1/admin/employees/{id}` | `employee.view` | Employee detail including placement history and linked account summary |
| POST | `/api/v1/admin/employees` | `employee.manage` | Create employee transactionally; body: `nip`, `fullName`, `email`, `employmentStatus`, optional initial `divisionUnitId` |
| PUT | `/api/v1/admin/employees/{id}` | `employee.manage` | Update employee master fields. NIP is not self-service: only `employee.manage` may correct it, `EMPLOYEE_UPDATED` records the changed field names (not values), and the result must reconcile with the HR source. |
| POST | `/api/v1/admin/employees/{id}/placements` | `employee.manage` | Place/transfer; closes current active placement in the same transaction |
| GET | `/api/v1/admin/employees/{id}/placements` | `employee.view` | Placement history, newest first |
| POST | `/api/v1/admin/employees/{id}/account` | `user.manage` + `employee.manage` | Create the single User account for an employee; body `username`, `temporaryPassword`; email is taken from the employee; `mustChangePassword=true` |
| POST | `/api/v1/admin/users/{id}/employee-link` | `user.manage` + `employee.manage` | Link an existing unlinked User to an Employee; body `employeeId`; rejects if either side is already linked |
| GET | `/api/v1/admin/users/unlinked?keyword&page&size` | `user.manage` | Human accounts without `employee_id` (migration worklist); id, username, email, display name, status |

Rules:

- `POST /admin/employees` and account creation are separate transactions: Employee + initial placement commit together; the account is created/linked in its own transaction. The standard admin flow calls both and must surface partial failure without presenting the Employee as fully onboarded until the account link succeeds.
- Exactly one active placement is enforced by DB partial unique index; a transfer returns `409 CONFLICT` if a concurrent transfer already closed it (stale `rowVersion`).
- `divisionUnitId` must reference an ACTIVE `DIVISION`; otherwise `400 VALIDATION_ERROR` with `fieldErrors.divisionUnitId`.
- The `linked` filter on the employee list is optional (`true`/`false`); omission returns all employees. `hasAccount` is derived from `identity.users.employee_id` and is present in both list and detail responses, so the migration worklist needs no client-side join.
- Employee lifecycle and account lifecycle are independent: `employmentStatus=INACTIVE/TERMINATED` blocks new grants and login policy per security rules but does not silently delete the account; deactivation is explicit and audited.
- **Employment gate (E8):** login for a linked account whose employee is not `ACTIVE` returns `403 ACCOUNT_DISABLED` with message "Akun tidak dapat digunakan karena status kepegawaian tidak aktif. Hubungi administrator." and audit `LOGIN_DENIED` (reason `EMPLOYMENT_INACTIVE`). Changing status away from `ACTIVE` invalidates the linked account's sessions and audits `EMPLOYEE_ACCESS_SUSPENDED`.
- Role assignment (`POST /admin/users/{id}/role-assignments`) and ACL grant to a `USER` principal return `400 VALIDATION_ERROR` (`fieldErrors.userId` / `fieldErrors.principalId`) while the employee status is not `ACTIVE`.
- Distribution rejects an inactive explicit recipient (`400`, `fieldErrors.recipientUserIds`) and omits inactive employees when expanding unit recipients; emails/notifications are not sent to them.
- `tools/ops/run-employment-check.sh` detects linked accounts that remain `ACTIVE` while the employee is not; E8-OPS-01 must be zero.
- Employee–User linkage is 1:1 and enforced by the partial unique index `identity.users (employee_id) WHERE employee_id IS NOT NULL`; both endpoints return `409 CONFLICT` for an already-linked account or employee. Unlinking is not exposed in the E-series.
- Responses never expose password material; account creation returns only the user id and `mustChangePassword=true`.
- **Legacy membership freeze (E7):** `POST /admin/users/{id}/units` returns `409 CONFLICT` with the message "Akun sudah terhubung ke Employee. Kelola penempatan melalui data Employee, bukan assignment unit legacy." for any account with `employee_id`. Ending legacy assignments stays allowed because it only narrows access. `GET` endpoints and distribution resolve the active placement in addition to legacy rows, so unit-scoped behavior stays consistent for linked accounts.
- Distribution to a unit snapshots legacy active members **and** linked accounts placed in that unit at send time. Moving an employee later never changes an already-sent distribution's recipient set.
- `GET /api/v1/auth/me` `organizationUnits` includes the active Biro/Division placement (`PRIMARY`) for linked accounts, deduplicated against legacy assignments.

## 8. Descendant scope flag (E-series, additive)

- `includeDescendants` (default `false`) is accepted on ACL grant create/update and on unit-scoped role assignment create.
- It is rejected with `400 VALIDATION_ERROR` for `USER`/`ROLE` principals or `GLOBAL`/`WORKSPACE` scopes.
- Existing grants without the field remain exact-match; clients that omit it cannot widen access.
- Read DTOs always return `includeDescendants` so the UI can show coverage; changing it is audited.
- Decision precedence is unchanged: explicit DENY (any scope) overrides ALLOW, including descendant-derived ALLOW.
- List/detail DTOs may expose a `derivedFrom` hint (unit + flag) for transparency but must not expose evaluator internals.

## 9. Compatibility

- No existing path, payload field, or status code is removed during E1–E4.
- `GET /api/v1/admin/users` keeps its current shape; employee fields are added as optional summary objects only after E3.
- Legacy `/admin/users/{id}/units` create is disabled for Employee-linked users (E7, unconditional); ending an assignment remains available. Unit reads and distribution include active placements. The legacy table is retained until retirement is approved (V19), so audit/history is never destroyed.
- Contract changes require updating this document, `SCHEMA.md`, and integration tests.
