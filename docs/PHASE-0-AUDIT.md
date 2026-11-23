# KCIC Docs — Phase 0 Repository Audit

Date: 2026-11-22
Status: COMPLETE

## 1. Repository baseline

| Item | Finding |
|---|---|
| Contents at start | Only `AGENTS.md`, `PRD.md`, `ARCHITECTURE.md`, `SCHEMA.md`, `DESIGN_SYSTEM.md`, `IMPLEMENTATION_PLAN.md` |
| Existing application | None. Greenfield backend/frontend must be bootstrapped. |
| Git | Directory is **not** a git repository (`git rev-parse` fails). No history to preserve. |
| Package manager | npm 10.9.2 (Node 22.15.1). No Java build tool installed. |
| Accounts/bootstrap data | None. No seeded users, organization units, or documents exist. |

## 2. Verified runtime environment

| Runtime | Verified version | Notes |
|---|---|---|
| Node.js | 22.15.1 | Meets Vite 8 requirement (>= 22.12). |
| npm | 10.9.2 | |
| Java | 23.0.2 (Oracle JDK) | `JAVA_HOME` is unset; Maven uses JDK 23. Backend compiled with `--release 21` for portability. |
| Maven | 3.9.9 | Downloaded by the agent into `tools/apache-maven-3.9.9` because no system Maven/Gradle binary exists. `tools/` is gitignored. |
| Gradle | 9.3.1 distribution cached, no launcher | Not used; Maven chosen for Spring Boot compatibility and reproducibility. |
| PostgreSQL | 18.6 client/server binaries installed, **service not running** | Local containers used instead so schema behavior is reproducible. |
| Docker | 29.9.3, daemon running | PostgreSQL 16 and MinIO images pulled successfully. |
| Network | Maven Central and npm registry reachable (HTTP 200) | Enables dependency resolution. |

## 3. Local environment design

- `docker compose up -d` starts:
  - PostgreSQL 16 on `localhost:5433` (avoids collision with a locally started PostgreSQL on 5432).
  - MinIO on `localhost:9000` (console 9001).
- Backend: Spring Boot on port 8080, Flyway-managed schema, private bucket auto-created.
- Frontend: Vite dev server on port 5173 with `/api` proxied to 8080.
- Secrets come from environment variables (`.env`, never committed). `.env.example` documents required keys.

## 4. Locked stack implemented

| Layer | Baseline used | Version |
|---|---|---|
| Frontend | React + TypeScript + Vite | React 19, Vite 8, TypeScript 5.9 |
| Styling | Tailwind CSS + internal component system | Tailwind 4 (CSS-first tokens) |
| Routing / server state | React Router + TanStack Query | RR 7, Query 5 |
| Backend | Spring Boot modular monolith | 3.5.16 |
| Security | Spring Security + Spring Session JDBC | managed by Boot |
| Database | PostgreSQL (source of truth) | 16 container |
| Migrations | Flyway | managed by Boot |
| Object storage | MinIO (S3-compatible), private bucket | 8.5.17 client |
| Tests | JUnit 5 + Testcontainers + MockMvc; Vitest + Testing Library; Playwright | see `pom.xml` / `package.json` |

## 5. API and migration conventions

Documented in `docs/API_CONVENTIONS.md` and `docs/MIGRATION_CONVENTIONS.md`.

## 6. Risks and blockers

| ID | Risk | Mitigation | Status |
|---|---|---|---|
| R-01 | No antivirus in pilot | Allowlist extension+MIME+signature checks, size cap, private bucket, safe disposition; residual risk documented for UAT | ACCEPTED (per approved scope) |
| R-02 | Local Postgres service not running | Docker Compose database in development/test; production uses managed PostgreSQL 16+ | MITIGATED |
| R-03 | No CI platform configured | Local quality gates executed and recorded; scripts provided for CI adoption | OPEN (operational) |
| R-04 | Production TLS reverse proxy not provisioned | App documents HTTPS requirement and secure-cookie flag; dev uses HTTP with `Secure=false` only | OPEN (deployment) |
| R-05 | File policy values (allowed formats/size) are provisional | Seeded pilot-default policy is labeled `PROVISIONAL`; admin-editable; business sign-off required before go-live | OPEN (business decision) |
| R-06 | Backup/RPO/RTO targets unapproved | Backup/restore drill executed locally; numeric RPO/RTO left to IT owner | OPEN (business decision) |

## 7. Open questions (non-blocking for build, blocking for go-live)

Carried from PRD §27.2 / ARCHITECTURE §18: user scale, real org structure, final classification policy, allowed file formats and size, preview format requirements, request SLA, fallback approver, deployment/backup targets, retention periods. No numeric or policy value is invented by the agent; provisional defaults are marked.

## 8. Phase 0 exit gate

| Gate | Evidence | Result |
|---|---|---|
| Frontend/backend can run per documented instructions | `README.md`; backend boots on 8080, frontend on 5173 | PASS |
| Baseline build/test executable or blocker documented | Maven 3.9.9 + JDK 23 verified; npm registry reachable; Docker running | PASS |
| No stack/scope conflicts | Locked stack honored; one documented visual override (sidebar maroon) in `DECISIONS.md` | PASS |
| All Phase 1 stories have acceptance criteria and module owner | `docs/PROGRESS.md` | PASS |
