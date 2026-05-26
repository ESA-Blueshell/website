#!/usr/bin/env bash
# check-migration-order.sh — fail loudly when a Flyway migration on this
# branch would deploy badly against the current `main`.
#
# Catches the failure mode that bit #243 / #245: two open PRs each add
# a migration; whichever lands second uses a version number ≤ the head
# Flyway has already applied, Spring Boot's Flyway refuses to migrate
# ("Detected resolved migration not applied to database: <n>"), the
# new pod fails its readiness probe, and the rolling deploy stalls
# behind the still-healthy old replica.
#
# Rules enforced:
#   1. No two migration files on this branch share a version number.
#   2. Every migration file *added* on this branch (incl. renamed-to)
#      must have a version strictly greater than the highest version
#      currently on ${BASE_REF}.
#   3. No migration file already on ${BASE_REF} may be *modified* by
#      this branch — once a migration is applied, its content is
#      effectively immutable.
#
# Usage:
#   scripts/check-migration-order.sh                 # vs origin/main
#   BASE_REF=origin/develop scripts/check-migration-order.sh
#
# Exit codes: 0 ok, 1 rule violated, 2 script can't run.
set -euo pipefail

MIGRATION_DIR="services/api/src/main/resources/db/migration"
BASE_REF="${BASE_REF:-origin/main}"

if ! git rev-parse --verify --quiet "$BASE_REF" >/dev/null; then
  echo "error: base ref '$BASE_REF' is not available locally." >&2
  echo "       Run 'git fetch origin main' first or set BASE_REF." >&2
  exit 2
fi

version_of() {
  # Extract the version number from a Flyway filename. Echoes nothing
  # for non-Flyway paths so the caller can skip them with a simple test.
  sed -nE 's|.*/V([0-9]+)__.*\.sql$|\1|p' <<< "$1"
}

versions_on() {
  # Print one numeric version per migration tracked at the given ref.
  git ls-tree -r --name-only "$1" -- "$MIGRATION_DIR" 2>/dev/null \
    | grep -E "/V[0-9]+__.*\.sql$" \
    | sed -nE 's|.*/V([0-9]+)__.*\.sql$|\1|p' \
    | sort -n
}

base_max="$(versions_on "$BASE_REF" | tail -1)"
base_max="${base_max:-0}"
echo "Highest Flyway migration version on $BASE_REF: V$base_max"

violations=()

# ── Rule 1 — duplicate version number on this branch ─────────────────────────
while IFS= read -r v; do
  [[ -z "$v" ]] && continue
  files="$(git ls-tree -r --name-only HEAD -- "$MIGRATION_DIR" \
            | grep -E "/V${v}__.*\.sql$" | tr '\n' ' ')"
  violations+=("Duplicate version V${v} on branch: ${files}")
done < <(versions_on HEAD | uniq -d)

# ── Rules 2 + 3 — diff-driven ────────────────────────────────────────────────
# `--name-status` prints a status letter (A/M/D/R…) per touched file.
# Renames carry R<percent> + old + new paths; the new path is what
# Flyway sees, so that's the version we validate.
while IFS=$'\t' read -r status path1 path2; do
  case "$status" in
    A)
      file="$path1"
      v="$(version_of "$file")"
      [[ -z "$v" ]] && continue
      if (( v <= base_max )); then
        violations+=("Out-of-order: ${file} is V${v} but ${BASE_REF} is already at V${base_max} — renumber to V$((base_max + 1)) or later")
      fi
      ;;
    M)
      file="$path1"
      v="$(version_of "$file")"
      [[ -z "$v" ]] && continue
      # The modified file might be one this branch added in an earlier
      # commit (v > base_max, not yet on main) — that's fine, the
      # author is still iterating. Only block edits to files already
      # on the base ref.
      if (( v <= base_max )); then
        violations+=("Modifying applied migration: ${file} (V${v}) is already on ${BASE_REF} — Flyway will reject the checksum change at boot")
      fi
      ;;
    R*)
      newfile="$path2"
      v="$(version_of "$newfile")"
      [[ -z "$v" ]] && continue
      if (( v <= base_max )); then
        violations+=("Out-of-order (renamed): ${newfile} is V${v} but ${BASE_REF} is already at V${base_max} — renumber to V$((base_max + 1)) or later")
      fi
      ;;
  esac
done < <(git diff --name-status "$BASE_REF...HEAD" -- "$MIGRATION_DIR")

if (( ${#violations[@]} > 0 )); then
  echo
  echo "❌ Flyway migration check failed:" >&2
  for msg in "${violations[@]}"; do
    echo "  - $msg" >&2
  done
  echo >&2
  echo "Why this matters: Spring Boot's Flyway runs in strict-validate mode" >&2
  echo "by default. A migration with a version ≤ the schema's current head," >&2
  echo "or any change to an applied migration's content, is rejected at" >&2
  echo "boot, the new pod fails its readiness probe, and the rolling deploy" >&2
  echo "never replaces the old pod." >&2
  exit 1
fi

touched="$(git diff --name-only "$BASE_REF...HEAD" -- "$MIGRATION_DIR" | wc -l | tr -d ' ')"
echo "✅ ${touched} migration file(s) touched on this branch, none out-of-order."
