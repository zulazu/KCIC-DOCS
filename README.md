# KCIC Docs

Internal document governance platform: catalog, private files and Office links, versioning, RBAC + scope + resource ACL, document requests/approvals/fulfillment, internal distribution, template catalog, in-app notifications, and business audit.

This is a pilot built to production-minded engineering standards: modular monolith, versioned migrations, backend-authoritative authorization, private object storage, automated tests, and evidence-backed quality gates.

## Stack

| Layer | Technology |
|---|---|
| Frontend | React 19 + TypeScript + Vite, Tailwind CSS 4, React Router, TanStack Query |
| Backend | Java 21 target (JDK 23 runtime), Spring Boot 3.5, Spring Security, Spring Session JDBC, Flyway, JPA |
| Database | PostgreSQL 16+ (native install; verified with PostgreSQL 18) |
| Object storage | Filesystem adapter for local dev; MinIO (private bucket) for shared/production, accessed only by the backend |
| Tests | JUnit 5, MockMvc, Testcontainers (optional) or local PostgreSQL fallback; Vitest + Testing Library; Playwright E2E (`frontend/e2e`) |

## Repository layout

```text
backend/                 Spring Boot modular monolith
  src/main/java/com/kcic/connect/{identity,authorization,organization,masterdata,repository,workflow,distribution,template,notification,audit,shared}
  src/main/resources/db/migration/   Flyway migrations
frontend/                React SPA
docs/                    Audit, conventions, decisions, progress, guides
docker-compose.yml       Optional: containerized PostgreSQL + MinIO (not required for local dev)
```

## Prerequisites

- JDK 21+ (verified with JDK 23.0.2)
- Node.js 22.12+ and npm 10+
- PostgreSQL 16+ installed natively (verified with 18.6 on `127.0.0.1:5432`)
- Apache Maven 3.9+ (this environment provisions it under `tools/apache-maven-3.9.9`; the directory is gitignored)
- Docker Desktop — **optional**, only if you prefer containerized PostgreSQL/MinIO or Testcontainers

## Local setup (native PostgreSQL, no Docker)

1. Create a database role and database once, using your PostgreSQL superuser:

   ```sql
   CREATE ROLE kcic LOGIN PASSWORD '<choose a local password>';
   CREATE DATABASE kcic_connect OWNER kcic;
   ```

   Flyway applies all migrations automatically on first backend start; never edit the shared schema manually.

2. Configure the environment:

   ```bash
   cp .env.example .env                     # set local passwords; never commit .env
   ```

   The example defaults to `KCIC_STORAGE_PROVIDER=filesystem` with a gitignored local root, so no MinIO is needed for development.

3. Run the applications:

   ```bash
   # Backend (loads .env; Git Bash syntax)
   set -a && source .env && set +a
   tools/apache-maven-3.9.9/bin/mvn -f backend/pom.xml spring-boot:run

   # Frontend (separate terminal)
   cd frontend
   npm install
   npm run dev                              # http://localhost:5173, proxies /api to :8080
   ```

The administrator is created **once**, only when `identity.users` is empty, from `KCIC_BOOTSTRAP_ADMIN_*`. There is no seeded password in source code. The account is marked `must_change_password` and must set a new password after first login. Set `KCIC_BOOTSTRAP_ADMIN_EMPLOYEE_NIP` once `identity.users.employee_id` is `NOT NULL` (V18): the bootstrap admin is then linked to that existing ACTIVE Employee, and an unknown or non-ACTIVE NIP aborts startup instead of creating an invalid account.

### Optional: containerized infrastructure

`docker compose up -d` still provides PostgreSQL (5433) + MinIO (9000/9001) if you prefer containers. Point `KCIC_DB_URL` at port 5433 and set `KCIC_STORAGE_PROVIDER=minio` with the MinIO credentials from `.env`. This path is no longer the default and is not required for any gate.

## Quality gates

```bash
# Backend — integration tests need a PostgreSQL database.
# Option A (no Docker): dedicated local test database, created once:
#   CREATE DATABASE kcic_connect_test OWNER kcic;
KCIC_TEST_DB_URL='jdbc:postgresql://127.0.0.1:5432/kcic_connect_test' \
KCIC_TEST_DB_USERNAME=kcic KCIC_TEST_DB_PASSWORD='<local password>' \
tools/apache-maven-3.9.9/bin/mvn -f backend/pom.xml test
# Option B: with Docker running, omit the KCIC_TEST_DB_* variables and
# Testcontainers provisions a disposable PostgreSQL automatically.
tools/apache-maven-3.9.9/bin/mvn -f backend/pom.xml package

# Frontend
cd frontend
npm run lint
npm run typecheck
npm run test
npm run build

# E2E smoke suite (requires backend + database + running frontend; credentials come from the environment)
export KCIC_E2E_BASE_URL=http://localhost:5173
export KCIC_E2E_ADMIN_USERNAME='<admin username>'
export KCIC_E2E_ADMIN_PASSWORD='<admin password>'
export KCIC_E2E_EMPLOYEE_USERNAME='<employee username>'
export KCIC_E2E_EMPLOYEE_PASSWORD='<employee password>'
npm run test:e2e --prefix frontend
```

The E2E suite fails fast with a clear message when the `KCIC_E2E_*` credentials are missing; passwords are never committed. It covers unauthenticated redirects, invalid login, employee navigation/forbidden pages, admin deep links, audit filtering, and the not-found route. Full-journey acceptance evidence is recorded in `docs/UAT.md`.

Integration/security tests use a dedicated test database — never the development database. Without `KCIC_TEST_DB_URL` they fall back to Testcontainers and require Docker; if neither is available they fail with an explicit environment error (documented, never silently skipped). Tests are idempotent (unique usernames per run), so reruns against the persistent local test database are safe.

## Configuration

All configuration is environment-driven (`backend/src/main/resources/application.yml`). Secrets are never committed. See `.env.example` for the full list and `docs/DEPLOYMENT.md` for production requirements (TLS, secure cookies, private bucket, backups).

## Documentation

- `AGENTS.md`, `PRD.md`, `ARCHITECTURE.md`, `SCHEMA.md`, `DESIGN_SYSTEM.md`, `IMPLEMENTATION_PLAN.md` — governing documents
- `docs/PHASE-0-AUDIT.md` — environment and repository audit
- `docs/API_CONVENTIONS.md` — API contract rules
- `docs/MIGRATION_CONVENTIONS.md` — schema change rules
- `docs/MIGRATION_PLAN.md` — E-series organization/identity cutover and rollback plan
- `docs/organization-identity/` — E0 audit, gap analysis, and migration risk register
- `docs/DECISIONS.md` — engineering decisions and overrides
- `docs/PROGRESS.md` — story/phase status with evidence
- `docs/SECURITY.md` — security model and residual risks
- `docs/UAT.md`, `docs/DEPLOYMENT.md`, `docs/USER_GUIDE.md`, `docs/ADMIN_GUIDE.md` — operational handover (Phase 9)

## Scope boundaries

Deferred by product decision: SSO/OIDC/LDAP/HRIS, Microsoft Graph/SharePoint/OneDrive API and permission sync, antivirus/malware scanning, centralized logging/APM, Redis, message brokers, Kubernetes, microservices, email/WhatsApp/SMS/push, guest/public sharing, OCR/full-text content search, collaborative editing.

Deferred does **not** mean missing basic controls: input/file validation, private storage, safe logs, audit trail, error handling, authorization, tests, and backup/restore remain in scope.
