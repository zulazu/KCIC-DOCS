#!/usr/bin/env bash
# Run the read-only employment/account lifecycle check against PostgreSQL.
# Override KCIC_DB_CONTAINER / KCIC_DB_USER / KCIC_DB_NAME for other environments;
# the script only pipes SQL into psql and never writes to the database.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONTAINER="${KCIC_DB_CONTAINER:-kcic-postgres}"
DB_USER="${KCIC_DB_USER:-kcic}"
DB_NAME="${KCIC_DB_NAME:-kcic_connect}"

OUTPUT="$(docker exec -i "$CONTAINER" psql -X -v ON_ERROR_STOP=1 -U "$DB_USER" -d "$DB_NAME" \
  < "$SCRIPT_DIR/employment_lifecycle_check.sql")"
printf '%s\n' "$OUTPUT"

if grep -Eq 'E8-OPS-GATE[[:space:]]*\|.*\|[[:space:]]*BLOCKED[[:space:]]*$' <<< "$OUTPUT"; then
  echo "Employment lifecycle check BLOCKED: E8-OPS-01 must be zero." >&2
  exit 1
fi
