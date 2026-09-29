#!/usr/bin/env bash
#
# Renders the SQL the changesets this branch adds would run. The base's
# changelog is applied first, so only what the branch adds is left to render.
set -euo pipefail

BASE=${1:?usage: changeset-sql.sh <base-ref> [out]}
OUT=${2:-changeset-sql/changeset.sql}
REL=services/api/src/main/resources/db/changelog
PORT=${PORT:-3416}
NAME=${NAME:-changeset-sql}
WORK=$(mktemp -d)
trap 'docker rm -f "$NAME" >/dev/null 2>&1 || true; rm -rf "$WORK"' EXIT

added=$(git diff --name-only --diff-filter=AM "$BASE...HEAD" -- "$REL/changes" \
  | grep -E '\.ya?ml$' || true)
if [ -z "$added" ]; then
  echo "no changeset added or edited"
  exit 0
fi

mkdir -p "$WORK/base" "$(dirname "$OUT")"
git archive "$BASE" "$REL" | tar -x -C "$WORK/base" --strip-components=7
# A changeset edited in place is one no release has run yet. The base goes on
# without it, so its whole SQL renders rather than a checksum failure.
edited=$(git diff --name-only --diff-filter=M "$BASE...HEAD" -- "$REL/changes" \
  | grep -E '\.ya?ml$' || true)
for file in $edited; do rm -f "$WORK/base/changes/${file##*/}"; done

# A customChange is api code the Liquibase image cannot load, so both copies stand the same
# output change in its place, and the render names it instead of its SQL.
mkdir -p "$WORK/head"
cp -R "$REL/." "$WORK/head/"
python3 - "$WORK/base/changes" "$WORK/head/changes" <<'PY'
import pathlib, re, sys
for folder in sys.argv[1:]:
    for path in pathlib.Path(folder).glob("*.y*ml"):
        text = path.read_text()
        swapped = re.sub(
            r"^(\s*)- customChange:\s*\n\s*class:\s*(\S+)\s*$",
            lambda m: f"{m.group(1)}- output:\n{m.group(1)}    message: custom change {m.group(2)}",
            text,
            flags=re.M,
        )
        if swapped != text:
            path.write_text(swapped)
PY

docker rm -f "$NAME" >/dev/null 2>&1 || true
# Production's version; see platform/cluster/flux/apps/data/mariadb/release.yaml.
docker run -d --name "$NAME" -e MARIADB_ROOT_PASSWORD=x -p "$PORT:3306" mariadb:10.11.10 >/dev/null
for _ in $(seq 1 30); do
  docker exec "$NAME" mariadb -uroot -px -e 'SELECT 1' >/dev/null 2>&1 && break
  sleep 2
done
docker exec "$NAME" mariadb -uroot -px -e 'CREATE DATABASE blueshell' 2>/dev/null

lb() {
  local dir=$1; shift
  docker run --rm --network host -v "$dir:/liquibase/db/changelog" liquibase/liquibase:4.31 \
    --url='jdbc:mariadb://127.0.0.1:'"$PORT"'/blueshell' --username=root --password=x \
    --changeLogFile=db/changelog/db.changelog-master.yaml "$@"
}

lb "$WORK/base" update >/dev/null 2>&1 \
  || { echo "::error::the base changelog does not apply"; exit 1; }

# DATABASECHANGELOG rows are Liquibase's own bookkeeping, not the change.
lb "$WORK/head" update-sql \
  | sed -n '/^--  *Changeset/,$p' \
  | sed '/^--  *Release Database Lock/,$d' \
  | { grep -vE '^INSERT INTO blueshell\.DATABASECHANGELOG' || true; } > "$OUT"
for file in $added; do
  sed -n 's/^[[:space:]]*class:[[:space:]]*\([^[:space:]]*\).*/-- Also runs \1, a custom change in the api that no SQL render shows./p' "$file" >> "$OUT"
done

echo "$added" | sed 's|.*/||' > "${OUT%.sql}.files"
echo "rendered $(grep -c '' "$OUT") lines for $(echo "$added" | grep -c '') changeset file(s)"
