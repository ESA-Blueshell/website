#!/usr/bin/env bash
# Generates the OpenAPI spec from the running Docker API container,
# downloads external specs, regenerates the Brevo client,
# and generates frontend TypeScript clients.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "$ROOT_DIR"

# shellcheck source=scripts/openapi-common.sh
source "$ROOT_DIR/scripts/openapi-common.sh"

# ---- Prerequisites ----

check_common_prerequisites

if ! docker compose version >/dev/null 2>&1; then
  echo "docker compose is not available" >&2
  exit 1
fi

if [ ! -f "docker-compose.yml" ]; then
  echo "docker-compose.yml not found in current directory" >&2
  exit 1
fi

# ---- Fetch Blueshell spec from running API container ----

echo "Fetching Blueshell OpenAPI spec from API container..."
docker compose exec -T api sh -c \
  "curl -fsSS http://localhost:8080/v3/api-docs" > "${API_OPENAPI_SPEC%.json}.raw.json"

# ---- Shared steps ----

download_external_specs
regen_brevo_client
normalize_specs

# ---- Generate frontend TypeScript clients ----

echo "Generating frontend TypeScript clients..."
# `up -d frontend` is a no-op when the compose config is unchanged,
# but forces a recreate when mounts or env have drifted (e.g. after
# the spec path changing). Without this, `exec` runs
# against a stale container that still has the old bind-mounts.
docker compose up -d frontend
docker compose exec frontend sh -c \
  "cd /usr/app && yarn gen:all && (yarn lint:gen || true)"

# Restore properties literally named `required`. @hey-api/openapi-ts
# 0.92.x silently drops object properties named `required` because it
# collides with the JSON Schema metadata keyword (the array sibling of
# `properties`). We carry that field on QuestionRequest / QuestionResponse,
# so re-inject it post-gen until the upstream parser is fixed.
TYPES_FILE="services/frontend/src/services/api/blueshell/types.gen.ts"
if [ -f "$TYPES_FILE" ]; then
  python3 - "$TYPES_FILE" <<'PY'
import re, sys
path = sys.argv[1]
src = open(path).read()
def inject(src, type_name):
    pattern = re.compile(
        r'(export type ' + re.escape(type_name) + r' = \{\n)((?:[^}]*?\n)*?)(\};)',
        re.MULTILINE,
    )
    def repl(m):
        head, body, tail = m.group(1), m.group(2), m.group(3)
        if 'required?:' in body or 'required:' in body:
            return m.group(0)
        lines = body.rstrip("\n").split("\n")
        lines.append("    required?: boolean;")
        lines.sort(key=lambda l: l.strip().split('?')[0].split(':')[0])
        return head + "\n".join(lines) + "\n" + tail
    return pattern.sub(repl, src, count=1)
for t in ("QuestionRequest", "QuestionResponse"):
    src = inject(src, t)
open(path, "w").write(src)
PY
fi

echo "OpenAPI spec and frontend clients generated successfully."
