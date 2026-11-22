#!/usr/bin/env bash
# E6 cutover rehearsal on a throwaway database.
# 1. creates an empty database, applies V1-V17
# 2. loads labeled rehearsal fixtures
# 3. runs the E6 preflight and requires PASS
# 4. applies the V18 candidate and verifies the NOT NULL enforcement
# 5. drops the database (also on failure)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
MIGRATION_DIR="$REPO_ROOT/backend/src/main/resources/db/migration"
CONTAINER="${KCIC_DB_CONTAINER:-kcic-postgres}"
DB_USER="${KCIC_DB_USER:-kcic}"
DB_NAME="${KCIC_REHEARSAL_DB:-kcic_e6_rehearsal}"

psql_run() {
  docker exec -i "$CONTAINER" psql -X -v ON_ERROR_STOP=1 -U "$DB_USER" -d "$DB_NAME" "$@"
}

cleanup() {
  docker exec "$CONTAINER" dropdb -U "$DB_USER" --if-exists "$DB_NAME" >/dev/null 2>&1 || true
}
trap cleanup EXIT

cleanup
docker exec "$CONTAINER" createdb -U "$DB_USER" "$DB_NAME"

echo "== applying V1-V17 to $DB_NAME =="
while IFS= read -r migration; do
  echo "-- $(basename "$migration")"
  psql_run < "$migration" >/dev/null
done < <(ls -1 "$MIGRATION_DIR"/V*.sql | sort -V)

echo "== loading rehearsal fixtures =="
psql_run < "$SCRIPT_DIR/e6_rehearsal_fixture.sql" >/dev/null

echo "== applying the recommended unit mapping (KCIC-HO / KCIC-OPS) =="
psql_run < "$SCRIPT_DIR/e6_apply_unit_mapping.sql"

echo "== E6 preflight (must PASS) =="
if ! PREFLIGHT_OUTPUT="$(KCIC_DB_NAME="$DB_NAME" "$SCRIPT_DIR/run-e6-preflight.sh")"; then
  printf '%s\n' "$PREFLIGHT_OUTPUT"
  echo "REHEARSAL FAILED: preflight is not PASS" >&2
  exit 1
fi
printf '%s\n' "$PREFLIGHT_OUTPUT"

echo "== applying V18 candidate =="
psql_run < "$SCRIPT_DIR/v18_candidate.sql" >/dev/null

echo "== verifying NOT NULL enforcement =="
psql_run -c "
SELECT table_schema || '.' || table_name AS target, column_name, is_nullable
FROM information_schema.columns
WHERE (table_schema = 'identity' AND table_name = 'users' AND column_name = 'employee_id')
   OR (table_schema = 'organization' AND table_name = 'organization_units' AND column_name = 'unit_type')
ORDER BY 1;"

psql_run -c "
DO \$\$
DECLARE
    nullable_count integer;
BEGIN
    SELECT count(*) INTO nullable_count
    FROM information_schema.columns
    WHERE (
            (table_schema = 'identity' AND table_name = 'users' AND column_name = 'employee_id')
         OR (table_schema = 'organization' AND table_name = 'organization_units' AND column_name = 'unit_type')
          )
      AND is_nullable = 'YES';
    IF nullable_count <> 0 THEN
        RAISE EXCEPTION 'V18 did not enforce NOT NULL on all targets';
    END IF;
    RAISE NOTICE 'V18 rehearsal verification: PASS';
END \$\$;"

echo "== rehearsal PASS (database will be dropped) =="
