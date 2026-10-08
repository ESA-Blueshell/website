#!/bin/sh
# Takes Vault's Raft snapshot and the night's credentials onto the pod's memory-backed volume.
# A failure is written to /work/status/failed and the step exits 0, so the Kopia step still
# runs and reports it to Gatus.
set -eu
umask 077
mkdir -p /work/status /work/secrets /work/vault

fail() {
  echo "$1" >&2
  printf '%s\n' "$1" > /work/status/failed
  exit 0
}

VAULT_TOKEN=$(vault write -field=token auth/kubernetes/login role=backup \
  jwt=@/var/run/secrets/kubernetes.io/serviceaccount/token) || fail "Vault refused the backup role's login."
export VAULT_TOKEN

# Read first, so even a failure further down can still be reported.
vault kv get -field=gatus.backup_token secret/platform/alerting > /work/secrets/gatus-token || true

for field in kopia.password writer.access_key writer.secret_key; do
  vault kv get -field="$field" secret/platform/backup > "/work/secrets/$field" 2>/dev/null ||
    fail "secret/platform/backup has no $field yet; an owner seeds it (platform/docs/backup.md)."
done

vault operator raft snapshot save /work/vault/vault.snap || fail "Vault refused the Raft snapshot."

# One login, read once: a second read would lease a second user.
vault read -format=json database/creds/backup > /work/secrets/db.json ||
  fail "Vault handed out no database login for the backup role."
