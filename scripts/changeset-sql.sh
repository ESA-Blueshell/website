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

docker rm -f "$NAME" >/dev/null 2>&1 || true
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
lb "$PWD/$REL" update-sql \
  | sed -n '/^--  *Changeset/,$p' \
  | sed '/^--  *Release Database Lock/,$d' \
  | grep -vE '^INSERT INTO blueshell\.DATABASECHANGELOG' > "$OUT"

echo "$added" | sed 's|.*/||' > "${OUT%.sql}.files"
echo "rendered $(grep -c '' "$OUT") lines for $(echo "$added" | grep -c '') changeset file(s)"
