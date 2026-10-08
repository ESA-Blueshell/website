#!/bin/sh
# Reads the night's credentials from Vault onto the pod's memory-backed volume and, where asked,
# takes Vault's Raft snapshot and leases a read-only database login. A failure is written under
# /work/status and the step exits 0, so the Kopia step still runs and reports it.
#   VAULT_ROLE      the kubernetes auth role to log in as; it may read this namespace's report token
#   COLLECT_PAIR    true in data-system: also the Raft snapshot and the database login, the pair
#                   PAIR_GROUP names
set -eu
umask 077
mkdir -p /work/status /work/secrets /work/vault

fail() {
  echo "$1" >&2
  printf '%s\n' "$1" > "/work/status/$2"
  exit 0
}

VAULT_TOKEN=$(vault write -field=token auth/kubernetes/login role="$VAULT_ROLE" \
  jwt=@/var/run/secrets/kubernetes.io/serviceaccount/token) || fail "Vault refused the $VAULT_ROLE role's login." failed
export VAULT_TOKEN

# Read first, so even a failure further down can still be reported.
ns=$(cat /var/run/secrets/kubernetes.io/serviceaccount/namespace)
vault kv get -field=token "secret/platform/backup-report/$ns" > /work/secrets/gatus-token || true

for field in kopia.password writer.access_key writer.secret_key; do
  vault kv get -field="$field" secret/platform/backup > "/work/secrets/$field" 2>/dev/null ||
    fail "secret/platform/backup has no $field yet; an owner seeds it (platform/docs/backup.md)." failed
done

[ "${COLLECT_PAIR:-false}" = true ] || exit 0

vault operator raft snapshot save /work/vault/vault.snap ||
  fail "Vault refused the Raft snapshot." "$PAIR_GROUP.failed"

# One login, read once: a second read would lease a second user.
vault read -format=json database/creds/backup > /work/secrets/db.json ||
  fail "Vault handed out no database login for the backup role." "$PAIR_GROUP.failed"
