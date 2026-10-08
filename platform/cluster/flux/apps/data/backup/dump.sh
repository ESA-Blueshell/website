#!/usr/bin/env bash
# Dumps the database in one transaction, gzipped, as the read-only login collect.sh leased.
set -uo pipefail
umask 077

fail() {
  echo "$1" >&2
  printf '%s\n' "$1" > /work/status/failed
  exit 0
}

[ -s /work/status/failed ] && exit 0

field() { sed -n "s/^ *\"$1\": \"\\(.*\\)\",\\{0,1\\}\$/\\1/p" /work/secrets/db.json; }
user=$(field username)
password=$(field password)
[ -n "$user" ] && [ -n "$password" ] || fail "The database login from Vault could not be read."

cnf=/work/secrets/my.cnf
printf '[client]\nuser=%s\npassword=%s\nhost=%s\n' "$user" "$password" "$MARIADB_HOST" > "$cnf"
mkdir -p /work/mariadb

if ! mariadb-dump --defaults-extra-file="$cnf" --single-transaction --quick --triggers \
    --no-tablespaces --hex-blob blueshell | gzip > /work/mariadb/blueshell.sql.gz; then
  rm -f "$cnf"
  fail "mariadb-dump failed."
fi
rm -f "$cnf" /work/secrets/db.json
