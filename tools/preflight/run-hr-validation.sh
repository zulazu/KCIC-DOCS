#!/usr/bin/env bash
# Validate an HR employee CSV against the target database. READ-ONLY for domain tables.
# Usage: tools/preflight/run-hr-validation.sh [path/to/hr.csv]
# Default: tools/preflight/e6_hr_template.csv (fails by design: placeholders remain).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CSV="${1:-$SCRIPT_DIR/e6_hr_template.csv}"
CONTAINER="${KCIC_DB_CONTAINER:-kcic-postgres}"
DB_USER="${KCIC_DB_USER:-kcic}"
DB_NAME="${KCIC_DB_NAME:-kcic_connect}"

if [[ ! -f "$CSV" ]]; then
  echo "CSV not found: $CSV" >&2
  exit 2
fi

EXPECTED_HEADER='nip,full_name,email,employment_status,division_unit_code'
HEADER="$(head -n 1 "$CSV" | tr -d '\r' | sed '1s/^\xEF\xBB\xBF//')"
if [[ "$HEADER" != "$EXPECTED_HEADER" ]]; then
  printf 'CSV header mismatch.\nexpected: %s\nfound:    %s\n' "$EXPECTED_HEADER" "$HEADER" >&2
  exit 2
fi

docker cp "$CSV" "$CONTAINER:/tmp/e6_hr.csv" >/dev/null
OUTPUT="$(docker exec -i "$CONTAINER" psql -X -v ON_ERROR_STOP=1 -U "$DB_USER" -d "$DB_NAME" \
  < "$SCRIPT_DIR/e6_validate_hr.sql")"
printf '%s\n' "$OUTPUT"

if grep -q 'E6-HR-GATE.*FAIL' <<< "$OUTPUT"; then
  exit 1
fi
