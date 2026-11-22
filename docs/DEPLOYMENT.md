# KCIC Connect — Deployment and Operations Runbook

Pilot deployment target: one Spring Boot application (modular monolith), PostgreSQL 16, private S3-compatible bucket (MinIO), and the React build served as static files behind the same reverse proxy. No Kubernetes, message broker, or distributed worker is used.

## 1. Topology

```text
Browser ── HTTPS ──> Reverse proxy (TLS termination)
                       ├── /            → frontend/dist (static)
                       └── /api, /actuator → backend :8080
Backend ── JDBC ──> PostgreSQL 16 (source of truth)
Backend ── S3 API ──> MinIO / S3 bucket (private)
```

The application server itself serves HTTP on `KCIC_SERVER_PORT` (default 8080). TLS, HSTS, and HTTP→HTTPS redirect are the deployment edge's responsibility. For same-origin cookies and CSRF to work as designed, the SPA and API must be exposed under one origin through the proxy.

## 2. Configuration reference

All settings come from environment variables (`backend/src/main/resources/application.yml`); nothing is hardcoded.

| Variable | Purpose | Pilot requirement |
|---|---|---|
| `KCIC_SERVER_PORT` | Backend HTTP port | 8080 (behind proxy) |
| `KCIC_DB_URL` | JDBC URL | `jdbc:postgresql://<host>:5432/kcic_connect` |
| `KCIC_DB_USERNAME` / `KCIC_DB_PASSWORD` | DB credentials | strong, stored in secret manager |
| `KCIC_DB_POOL_SIZE` | Hikari pool | 10 default |
| `KCIC_COOKIE_SECURE` | `Secure` flag on session + CSRF cookies | **true** in shared/production |
| `KCIC_PASSWORD_MIN_LENGTH` | Minimum password length | 12 |
| `KCIC_LOGIN_MAX_FAILURES` / `KCIC_LOGIN_LOCK_MINUTES` | Lockout policy | 5 / 15 |
| `KCIC_BOOTSTRAP_ADMIN_USERNAME` / `_PASSWORD` / `_EMAIL` / `_DISPLAY_NAME` / `_EMPLOYEE_NIP` | One-time bootstrap admin (only when `identity.users` is empty); `_EMPLOYEE_NIP` links it to an existing ACTIVE Employee and is mandatory once V18 is applied | use once, then remove |
| `KCIC_STORAGE_PROVIDER` | `minio` (default) or `filesystem` | `minio` for any shared environment |
| `KCIC_STORAGE_ENDPOINT` / `_ACCESS_KEY` / `_SECRET_KEY` / `_BUCKET` / `_REGION` | S3-compatible storage | private bucket, least-privilege key |
| `KCIC_STORAGE_LOCAL_ROOT` | Filesystem adapter root | dev/test only |
| `KCIC_FILE_MAX_SIZE` | Default file ceiling if policy missing | 26214400 (25 MB) |
| `KCIC_APP_BASE_URL` | Frontend origin | `https://<host>` |

Sessions are stored in PostgreSQL (Spring Session JDBC); there is no session secret to configure.

## 3. Build

Prerequisites: JDK 21+ (verified on 23), Maven 3.9+ (this repo provisions `tools/apache-maven-3.9.9`), Node.js 22.12+.

```bash
# Backend (runs tests, then packages an executable jar)
tools/apache-maven-3.9.9/bin/mvn -f backend/pom.xml package

# Frontend static build
npm ci --prefix frontend
npm run build --prefix frontend     # output: frontend/dist
```

Quality gate before any deploy: `mvn -f backend/pom.xml test` green (84 tests at E8 + E6 hardening), `npm run lint && npm run typecheck && npm run test && npm run build --prefix frontend` green.

## 4. Database and migrations

- Flyway runs automatically at backend startup (`spring.flyway.enabled: true`, `baseline-on-migrate: false`). Migrations V1–V17 are committed under `backend/src/main/resources/db/migration/` (V17 = E4 descendant opt-in). V18/V19 are **not** Flyway migrations in this repository; V18 exists only as the rehearsed candidate `tools/preflight/v18_candidate.sql` and is promoted to a migration only for the approved cutover release.
- E-series cutover gate: follow `docs/organization-identity/E6_CUTOVER_RUNBOOK.md`. Run `tools/preflight/run-e6-preflight.sh` and sign off PASS; V18 must not be applied while any BLOCKER check has violations (see `docs/MIGRATION_PLAN.md`).
- After go-live, schedule `tools/ops/run-employment-check.sh` (read-only) as an operational control; E8-OPS-01 must be zero. The script exits `1` when the `E8-OPS-GATE` row is `BLOCKED` (WARN rows still exit `0`), so cron/CI fails on a real violation. The backend also denies login, new grants, and distribution for INACTIVE/TERMINATED employees, and ends their sessions on status change.
- Released migrations are immutable; fixes are new migrations.
- `spring.jpa.hibernate.ddl-auto: validate` — the app refuses to start if the schema does not match the entities.
- Run migration against a restore of the production schema (or a staging clone) before go-live. Never edit the shared schema manually.
- The DB user needs DDL rights to apply Flyway migrations (recommended: dedicated migration deployment step, then run the app with a DML-only user if your platform supports it).

## 5. Storage

- Create one private bucket (default `kcic-connect-documents`). No public/anonymous policy. The backend never issues presigned or public URLs; content is streamed after authorization.
- Backend credentials need only object read/write/list within that bucket.
- `KCIC_STORAGE_PROVIDER=filesystem` is a local development convenience; do not use it for shared or production environments (no replication, no shared durability).
- If metadata persistence fails after an object write, the backend attempts a compensating delete. If compensation fails, operators must find and remove the orphan object; the failure is logged.

## 6. Backup and restore runbook (verified drill)

PostgreSQL and the bucket must be backed up together; a database restore without the matching objects produces documents with missing binaries.

### 6.1 Logical database backup

```bash
# Custom-format dump straight to container-visible /tmp (avoids MSYS path rewriting on Windows Git Bash)
MSYS_NO_PATHCONV=1 docker exec kcic-postgres pg_dump -U kcic -d kcic_connect -Fc -f /tmp/kcic_connect.dump
MSYS_NO_PATHCONV=1 docker cp kcic-postgres:/tmp/kcic_connect.dump ./.local/kcic_connect.dump
```

### 6.2 Storage backup

Mirror the bucket with your object-storage tooling (`mc mirror`, `aws s3 sync --delete` from production to the backup location, or filesystem copy for the dev adapter). Keep DB and bucket snapshots from the same point in time.

### 6.3 Restore drill

```bash
MSYS_NO_PATHCONV=1 docker exec kcic-postgres createdb -U kcic kcic_restore_check
MSYS_NO_PATHCONV=1 docker cp ./.local/kcic_connect.dump kcic-postgres:/tmp/restore.dump
MSYS_NO_PATHCONV=1 docker exec kcic-postgres pg_restore -U kcic -d kcic_restore_check --no-owner /tmp/restore.dump
# compare row counts against the source, then verify Flyway history
MSYS_NO_PATHCONV=1 docker exec kcic-postgres psql -U kcic -d kcic_restore_check -c 'select version, success from flyway_schema_history order by installed_rank;'
MSYS_NO_PATHCONV=1 docker exec kcic-postgres dropdb -U kcic kcic_restore_check
```

Verified on 2026-11-22: custom-format dump restored into a scratch database with identical counts (`users` 4, `documents` 1, `document_versions` 2, `resource_permissions` 12, `requests` 1, `distributions` 2, `templates` 1, `audit_events` 153, `notifications` 6), Flyway history intact through V13, and the filesystem storage SHA-256 matched the pre-backup value. The scratch database was dropped after the check.

Recommendation: schedule DB + bucket backups at least daily, keep several generations, store encrypted off-host, and repeat the restore drill quarterly and before each release.

## 7. First deployment sequence

1. Provision PostgreSQL and the private bucket; create the database and DB user.
2. Set all environment variables; start the backend once with `KCIC_BOOTSTRAP_ADMIN_*` set. The bootstrap admin is created only if `identity.users` is empty, marked `must_change_password`. After V18, also set `KCIC_BOOTSTRAP_ADMIN_EMPLOYEE_NIP` to an existing ACTIVE Employee; an unlinked bootstrap is rejected.
3. Confirm Flyway applied V1–V17 and `/actuator/health` reports `UP` (liveness/readiness probes enabled). V18 is a cutover migration and applies only to the production-like database with verified HR data (see `docs/MIGRATION_PLAN.md`), not to a first install before organization data exists.
4. Deploy `frontend/dist` behind the proxy with SPA fallback to `index.html`; proxy `/api` and `/actuator` to the backend without stripping the CSRF/session cookies.
5. Log in with the bootstrap admin, change the password, create the organization units, roles assignments, master data/file policy approval, and the real administrator accounts.
6. Remove the bootstrap admin variables from the environment.
7. Run the post-deploy smoke test (section 9).

## 8. Reverse proxy requirements

- Terminate TLS with a valid certificate; redirect HTTP→HTTPS.
- Forward `Host`, `X-Forwarded-Proto: https`, and client IP; cookies are `Secure` and `SameSite=Lax`, so the SPA and API must share the same site.
- Do not cache `/api/*` or `/actuator/*`; document content responses are already `private, no-store`.
- Allow upload bodies up to at least 32 MB (`spring.servlet.multipart.max-request-size`) so the 25 MB policy ceiling is usable.
- Preserve the `X-XSRF-TOKEN` request header and `XSRF-TOKEN`/`KCIC_SESSION` response cookies.
- Serve built assets with long cache headers; `index.html` must be revalidated so new deploys are picked up.

## 9. Post-deploy smoke test

- `GET /actuator/health` → `UP`.
- Login as a normal employee: dashboard loads, sidebar shows only permitted groups.
- Open the document catalog, preview and download one document (audited).
- Submit a document request and approve it as the routed approver.
- Send a distribution to one user, open it as that recipient, revoke it, confirm the recipient loses access on the next API call.
- Open `/admin/audit` as an AUDITOR and confirm the day's events are visible with no secrets in metadata.
- Confirm `/admin/users` is forbidden for a non-administrator (UI state + HTTP 403).
- Check the backend log for errors and the absence of stack traces in API responses.

## 10. Rollback

- Application: redeploy the previous jar and previous `frontend/dist`. Migrations are forward-only; if a release added a migration, rollback of the binary is safe only when the previous version tolerates the newer schema (additive migrations are written for this). Otherwise restore the database and bucket from the pre-release backup pair.
- Never run destructive schema commands against a shared environment to "roll back"; restore or add a corrective migration.

## 11. Operational limits (pilot)

- No antivirus scanning: uploads pass structural validation only. Record this residual risk in the operational acceptance.
- Expiry/revocation is evaluated on the next API call; an open page keeps its last downloaded bytes.
- No centralized logging/APM; ship local logs and monitor `/actuator/health`.
- Catalog visibility filtering is in-memory over ≤1000 candidates per read (DEC-029/DEC-050); revisit before large-scale growth.
- External Office links are recorded only; permission changes at the provider are outside KCIC Connect.
