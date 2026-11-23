# KCIC Docs — Organization & Identity Migration Plan (E-series)

Status: **plan; V14–V17 are applied to the dev database, V18/V19 are not written as Flyway migrations and not applied.** Baseline was V1–V13. Detail evidence and risk rationale: `docs/organization-identity/MIGRATION_RISK_REGISTER.md`. Naming and integrity rules: `docs/MIGRATION_CONVENTIONS.md`, `SCHEMA.md` §21.

## 1. Objectives

- Introduce typed organization hierarchy, Employee master, Biro/Division placements, and 1:1 Employee–User linkage without destructive change.
- Preserve every existing grant's access exactly (no automatic widening).
- Enforce one active placement and one account per employee at database level.
- Keep existing API behavior during transition; disable legacy paths only after parity is proven.
- Never fabricate NIP, email, unit types, or placements.

## 2. Migration staging

| Version | Object | Change | Reversible? | Gate |
|---|---|---|---|---|
| V14 | `organization.organization_units` | Add nullable `unit_type`, CHECK values, indexes | Yes (drop column/index) | E1 code deployed |
| V15 | `identity.employees`, `organization.employee_placements`, `authorization` | Create tables/FKs/partial unique/indexes; seed `employee.view`, `employee.manage` (needed by E2 API) | Yes (drop tables, only if empty) | E2 code deployed |
| V16 | `identity.users`, `authorization` | Add nullable unique `employee_id` + FK; re-assert `employee.*` permissions | Yes (drop index/FK/column) | E3 code deployed; applied to dev DB |
| V17 | `authorization.resource_permissions`, `authorization.user_role_assignments` | Add `include_descendants` default false + unit-only CHECK | Yes (drop columns/constraints) | E4 code deployed |
| V18 | `identity.users.employee_id`, `organization_units.unit_type` | Set `NOT NULL` after verified backfill | No (requires restore to revert) | Preflight PASS + sign-off |
| V19 (optional) | `organization.user_organization_assignments` | Revoke write privileges / retire table | No | E7 approval only |

Rules:

- V14–V17 are additive and deployable before data import.
- V18 is a cutover migration and must not run in the same release as unverified data import.
- V19 is not part of the initial cutover; dropping legacy data requires explicit approval and backup verification.
- E7 already freezes legacy unit-assignment create for Employee-linked accounts at the application level; ending assignments stays allowed. The table and V19 retirement remain pending approval.
- Every migration runs on an empty DB and on V13; no manual schema edits in shared environments.

## 3. External inputs (blocking)

1. Verified HR dataset: NIP, full name, email, employment status, and DIVISION code per employee — request/verification procedure, unlinked-account worklist, and validator: `docs/organization-identity/E6_HR_VERIFICATION.md`, `tools/preflight/e6_unlinked_worklist.sql`, `tools/preflight/run-hr-validation.sh`. The validator requires the exact header order `nip,full_name,email,employment_status,division_unit_code`, rejects a header-only file (E6-HR-00), and rejects untrimmed NIP/division codes (E6-HR-08).
2. Unit-type mapping for existing units (KCIC-HO, KCIC-OPS, and any others) plus full hierarchy — proposal, sign-off table, and guarded apply script: `docs/organization-identity/E6_UNIT_MAPPING.md`. The product owner approved `KCIC-HO → COMPANY` and `KCIC-OPS → DIRECTORATE` on 2026-11-22; the official org chart and the HR Biro/Division list are still required before placements.
3. Placement data (Biro/Division per employee) from HR, not inferred from legacy `user_organization_assignments`.
4. Policy for bootstrap/system accounts without Employee that must survive V18. The bootstrap admin can now be linked at creation from `KCIC_BOOTSTRAP_ADMIN_EMPLOYEE_NIP` (verified ACTIVE Employee required; unknown/non-ACTIVE NIP aborts startup), so keep V18 as plain `NOT NULL` (DEC-066). **No account exception is approved (DEC-068)**; any future non-human identity requires a different enforcement design and an explicit decision.
5. Type (and parent where applicable) for **every** legacy unit, including inactive ones; `NULL` may remain only until mapping, but V18's table-wide `NOT NULL` means E6-05 must reach zero before cutover.

No import may proceed with placeholders, sequential fake NIPs, or guessed unit types.

## 4. Preflight (run before V18; must all pass)

Tooling: `tools/preflight/e6_preflight.sql` (read-only, grouped checks E6-01…E6-09 with an E6-GATE summary plus E6-INFO review sections) and `tools/preflight/run-e6-preflight.sh`. Run it against every environment and retain the output as evidence. E6-07 enforces exactly one ACTIVE COMPANY root (the application already enforces this on create/update); E6-08 blocks ACTIVE accounts linked to non-ACTIVE employees; E6-09 blocks blank/placeholder/untrimmed/malformed employee master fields; E6-05 covers **all** units, because V18 applies a table-wide `NOT NULL`. E6-INFO lists active legacy unit assignments on linked accounts because unit scope is the union of legacy and placement; rows that differ from the placement can widen access and need a per-row decision. The SQL below is the canonical definition; the script adds detail listings and a machine-readable gate row.

Latest dev run: `docs/organization-identity/E6_PREFLIGHT_DEV_2026-11-22_r4.txt` — **BLOCKED**: 5 unlinked accounts, 2 active untyped units; E6-07/08/09 pass and E6-INFO legacy-linked = 0. V18 was intentionally not applied. The unit mapping cannot be applied to this dev database until the UAT-CO fixture conflict is resolved (the mapping script aborts by design; see `E6_UNIT_MAPPING.md` §3). Rehearsal evidence with the hardened gate and strict runner: `docs/organization-identity/E6_REHEARSAL_2026-11-22_r3.txt` (PASS, exit 0).

```sql
-- 1. Human users without employee (excluding approved service accounts)
SELECT u.id, u.username FROM identity.users u
LEFT JOIN identity.employees e ON e.id = u.employee_id
WHERE u.employee_id IS NULL AND u.username NOT IN (/* approved list */);

-- 2. Employees with zero or multiple active placements
SELECT employee_id, COUNT(*) FROM organization.employee_placements
WHERE is_active GROUP BY employee_id HAVING COUNT(*) <> 1;

-- 3. Duplicate NIP/email and invalid email
SELECT nip, COUNT(*) FROM identity.employees GROUP BY nip HAVING COUNT(*) > 1;
SELECT lower(email), COUNT(*) FROM identity.employees GROUP BY lower(email) HAVING COUNT(*) > 1;

-- 4. Placements on missing/inactive/non-DIVISION units
SELECT p.id FROM organization.employee_placements p
JOIN organization.organization_units u ON u.id = p.division_unit_id
WHERE p.is_active AND (u.unit_type IS DISTINCT FROM 'DIVISION' OR u.unit_status <> 'ACTIVE');

-- 5. Units without type (V18 is table-wide NOT NULL; inactive units count too) and hierarchy violations
SELECT id, unit_code, unit_status FROM organization.organization_units WHERE unit_type IS NULL;
SELECT id FROM organization.organization_units WHERE unit_type = 'COMPANY' AND parent_id IS NOT NULL; -- must be empty
SELECT count(*) FROM organization.organization_units
WHERE unit_type = 'COMPANY' AND unit_status = 'ACTIVE'; -- must equal 1

-- 6. No accidental widening
SELECT COUNT(*) FROM authorization.resource_permissions WHERE include_descendants;
SELECT COUNT(*) FROM authorization.user_role_assignments WHERE include_descendants;

-- 7. ACTIVE accounts linked to non-ACTIVE employees (must be empty)
SELECT u.id FROM identity.users u JOIN identity.employees e ON e.id = u.employee_id
WHERE u.account_status = 'ACTIVE' AND e.employment_status <> 'ACTIVE';

-- 8. Blank/placeholder/untrimmed/malformed employee master fields (must be empty)
SELECT id FROM identity.employees
WHERE trim(nip) = '' OR trim(full_name) = '' OR trim(email) = ''
   OR nip <> trim(nip) OR full_name <> trim(full_name) OR email <> trim(email)
   OR nip LIKE '%<%>%' OR full_name LIKE '%<%>%' OR email LIKE '%<%>%'
   OR email !~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$';

-- 9. Review: active legacy assignments on linked accounts (not necessarily zero)
SELECT u.username, e.nip, lu.unit_code AS legacy_unit, a.assignment_type,
       pu.unit_code AS placement_unit
FROM organization.user_organization_assignments a
JOIN identity.users u ON u.id = a.user_id
JOIN identity.employees e ON e.id = u.employee_id
JOIN organization.organization_units lu ON lu.id = a.organization_unit_id
LEFT JOIN organization.employee_placements p ON p.employee_id = e.id AND p.is_active
LEFT JOIN organization.organization_units pu ON pu.id = p.division_unit_id
WHERE a.is_active AND pu.id IS DISTINCT FROM a.organization_unit_id;
```

Expected at cutover: E6-01/03/04/05/08/09 = 0 (no account exceptions, DEC-068), E6-02/06 = 0, E6-07 exactly one active COMPANY, the E6-INFO descendant counts reviewed (zero unless approved grant-by-grant), and every `DIFFERS_FROM_PLACEMENT` legacy row reviewed and signed off (or ended through the UI; no bulk delete).

## 5. Dry run

Full procedure and owners: `docs/organization-identity/E6_CUTOVER_RUNBOOK.md`.

1. Restore a recent production-like backup to an isolated database (no UAT fixtures).
2. Apply V14–V17; run import in a transaction and record counts/exceptions.
3. Validate the HR extract first (`tools/preflight/run-hr-validation.sh`); execute preflight and store output as evidence.
4. Run application regression: login, document read/download, request, distribution, template, audit.
5. Rehearse V18 on a throwaway database with `tools/preflight/run-e6-rehearsal.sh` (V1–V17 + labeled fixtures + preflight PASS + `tools/preflight/v18_candidate.sql` + verification). Evidence: `docs/organization-identity/E6_REHEARSAL_2026-11-22_r3.txt`.
6. Apply V18 (promote `v18_candidate.sql` to a Flyway migration only for the cutover release); rerun authorization regression and spot-check per-role visibility.
7. Measure runtime; confirm locks are short and no table rewrite beyond expectations.
8. Run `tools/ops/run-employment-check.sh`; E8-OPS-01 must be zero. The script exits `1` when `E8-OPS-GATE` is `BLOCKED`, so it can gate automation.

## 6. Cutover sequence

1. Announce change window; freeze employee/unit administration.
2. Back up database and object storage metadata; verify restore point.
3. Deploy compatible backend/frontend (supports nullable linkage and both paths).
4. Apply V14–V17.
5. Import verified HR data (employees, placements); link accounts; record exceptions.
6. Run preflight; resolve every failure. Do not proceed on unresolved rows.
7. Enable employee-first admin; freeze legacy `/admin/users/{id}/units` for linked users. After V18 the legacy `POST /admin/users` without an Employee link returns `409`; create accounts from the Employee detail.
8. Apply V18 (`NOT NULL` cutover).
9. Verify: login, capability snapshot, catalog visibility, document content access, request routing, distribution, audit.
10. Monitor logs/audit for DENIED spikes and 500s; keep change window open for rollback.
11. After UAT sign-off, remove transitional feature flag and plan E7 retirement.

## 7. Rollback

| Stage | Action |
|---|---|
| Before V18 | Revert application release; leave additive columns/tables (harmless) or drop in a maintenance migration if truly needed |
| After V18, before data acceptance | Restore pre-cutover backup; do not attempt ad-hoc `DROP NOT NULL` in shared env |
| Data error discovered | Freeze writes, correct from HR source, re-run import reconciliation; restore if integrity unknown |
| Partial account linking | Unlink only with audit + approval; never delete Employee rows to force pass |
| Storage unaffected | No object keys or binary changes in this migration |

Rollback never includes destructive reset of unrelated schemas, and never discards audit history.

## 8. Ownership and evidence

| Role | Responsibility |
|---|---|
| Backend owner | Migrations, import tooling, preflight, rollback rehearsal |
| DBA/operator | Backup/restore, execution, lock monitoring |
| HR/business owner | Verified dataset, unit mapping, import data-exception review |
| Security reviewer | Widening review, deny precedence, access-after-revoke tests |
| Product owner | Cutover approval and UAT sign-off |

Evidence to retain: migration checksums, preflight outputs, import counts/rejects, regression results, rollback drill log, approval records. Link them from `docs/PROGRESS.md`.

## 9. Post-migration acceptance

- [ ] V14–V18 applied successfully on empty and upgrade paths.
- [ ] 100% of accounts linked to a verified Employee; zero exceptions (DEC-068).
- [ ] Exactly one active placement per employee; history preserved.
- [ ] Every unit typed (E6-05 = 0; V18 is table-wide `NOT NULL`); active hierarchy valid; legacy unit decisions documented.
- [ ] Legacy grants `include_descendants=false`; access diff review shows no widening.
- [ ] Explicit DENY still wins; revoke/expiry behavior unchanged.
- [ ] Distribution snapshots and request routing regressions pass.
- [ ] Audit shows employee/placement/link events; no secrets logged.
- [ ] Rollback rehearsal completed and timed.
- [ ] Residual risks in `docs/SECURITY.md` accepted.
