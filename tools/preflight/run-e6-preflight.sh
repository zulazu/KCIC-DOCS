#!/usr/bin/env bash
# Run the read-only E6 cutover preflight against the local/dev PostgreSQL.
# Override KCIC_DB_CONTAINER / KCIC_DB_USER / KCIC_DB_NAME for other environments;
# the script only pipes SQL into psql and never writes to the database.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONTAINER="${KCIC_DB_CONTAINER:-kcic-postgres}"
DB_USER="${KCIC_DB_USER:-kcic}"
DB_NAME="${KCIC_DB_NAME:-kcic_connect}"

OUTPUT="$(docker exec -i "$CONTAINER" psql -X -v ON_ERROR_STOP=1 -U "$DB_USER" -d "$DB_NAME" \
  < "$SCRIPT_DIR/e6_preflight.sql")"
printf '%s\n' "$OUTPUT"

if grep -Eq 'E6-GATE[[:space:]]*\|.*\|[[:space:]]*BLOCKED[[:space:]]*$' <<< "$OUTPUT"; then
  echo "E6 preflight BLOCKED: fix every BLOCKER above before V18." >&2
  exit 1
fi
