# KCIC Docs

Internal document governance platform: catalog, private files and Office links, versioning, RBAC + scope + resource ACL, document requests/approvals/fulfillment, internal distribution, template catalog, in-app notifications, and business audit.

This is a pilot built to production-minded engineering standards: modular monolith, versioned migrations, backend-authoritative authorization, private object storage, automated tests, and evidence-backed quality gates.

## Stack

| Layer | Technology |
|---|---|
| Frontend | React 19 + TypeScript + Vite, Tailwind CSS 4, React Router, TanStack Query |
| Backend | Java 21 target (JDK 23 runtime), Spring Boot 3.5, Spring Security, Spring Session JDBC, Flyway, JPA |
| Database | PostgreSQL 16 |
| Object storage | MinIO (private bucket), accessed only by the backend |
| Tests | JUnit 5, Testcontainers (PostgreSQL + MinIO), MockMvc; Vitest + Testing Library; Playwright E2E (`frontend/e2e`) |

## Repository layout

```text
backend/                 Spring Boot modular monolith
  src/main/java/com/kcic/connect/{identity,authorization,organization,masterdata,repository,workflow,distribution,template,notification,audit,shared}
  src/main/resources/db/migration/   Flyway migrations
frontend/                React SPA
docs/                    Audit, conventions, decisions, progress, guides
docker-compose.yml       Local PostgreSQL + MinIO
```

## Prerequisites

- JDK 21+ (verified with JDK 23.0.2)
- Node.js 22.12+ and npm 10+
- Docker Desktop (PostgreSQL + MinIO)
- Apache Maven 3.9+ (this environment provisions it under `tools/apache-maven-3.9.9`; the directory is gitignored)

## Local setup

```bash
cp .env.example .env                     # set local passwords; never commit .env
docker compose up -d                     # PostgreSQL (5433) + MinIO (9000/9001)

# Backend
export KCIC_BOOTSTRAP_ADMIN_USERNAME=admin
export KCIC_BOOTSTRAP_ADMIN_PASSWORD='<at least 12 chars>'
export KCIC_BOOTSTRAP_ADMIN_EMAIL=admin@kcic.example
# Required after the E6/V18 cutover: NIP of an existing ACTIVE Employee.
export KCIC_BOOTSTRAP_ADMIN_EMPLOYEE_NIP='<verified NIP>'
tools/apache-maven-3.9.9/bin/mvn -f backend/pom.xml spring-boot:run

# Frontend (separate terminal)
cd frontend
npm install
npm run dev                              # http://localhost:5173, proxies /api to :8080
```

The administrator is created **once**, only when `identity.users` is empty, from `KCIC_BOOTSTRAP_ADMIN_*`. There is no seeded password in source code. The account is marked `must_change_password` and must set a new password after first login. Set `KCIC_BOOTSTRAP_ADMIN_EMPLOYEE_NIP` once `identity.users.employee_id` is `NOT NULL` (V18): the bootstrap admin is then linked to that existing ACTIVE Employee, and an unknown or non-ACTIVE NIP aborts startup instead of creating an invalid account.

## Quality gates

```bash
# Backend
tools/apache-maven-3.9.9/bin/mvn -f backend/pom.xml test
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

Integration/security tests use Testcontainers and require Docker. If Docker is unavailable, those tests fail with an explicit environment error (documented, never silently skipped).

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
