#!/usr/bin/env bash
# Stores the night's dump and Vault snapshot in the Kopia repository, and tells Gatus how it went.
set -uo pipefail

report() {
  curl -fsS -m 30 -o /dev/null -X POST --get \
    -H "Authorization: Bearer $(cat /work/secrets/gatus-token 2>/dev/null)" \
    --data-urlencode "success=$1" --data-urlencode "error=$2" "$GATUS_PUSH_URL" ||
    echo "Gatus did not take the report; its 26-hour heartbeat still alerts." >&2
}

fail() {
  echo "$1" >&2
  report false "$1"
  exit 1
}

[ -s /work/status/failed ] && fail "$(cat /work/status/failed)"

KOPIA_PASSWORD=$(< /work/secrets/kopia.password)
AWS_ACCESS_KEY_ID=$(< /work/secrets/writer.access_key)
AWS_SECRET_ACCESS_KEY=$(< /work/secrets/writer.secret_key)
export KOPIA_PASSWORD AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY

storage=(s3 --bucket="$BACKUP_BUCKET" --endpoint="$BACKUP_ENDPOINT" --region="$BACKUP_REGION")
# A fixed identity, so every night's snapshots share one source and one maintenance owner.
identity=(--override-hostname=blueshell --override-username=backup)

if ! out=$(kopia repository connect "${storage[@]}" "${identity[@]}" 2>&1); then
  [[ $out == *"not initialized"* ]] || fail "Kopia could not connect: ${out##*$'\n'}"

  # The first night. Every blob is written under COMPLIANCE retention to match the bucket's
  # lock, and full maintenance extends it, so live data never falls out of protection.
  kopia repository create "${storage[@]}" "${identity[@]}" \
    --retention-mode=COMPLIANCE --retention-period="$LOCK_PERIOD" ||
    fail "Kopia could not create the repository."
  kopia repository connect "${storage[@]}" "${identity[@]}" ||
    fail "Kopia could not connect to the repository it just created."
  kopia policy set --global --keep-latest=1 --keep-hourly=0 --keep-daily=7 --keep-weekly=4 \
    --keep-monthly=0 --keep-annual=0 || fail "Kopia could not set the retention."
  kopia maintenance set --owner=me --extend-object-locks=true ||
    fail "Kopia could not set up maintenance."
fi

kopia snapshot create /work/mariadb /work/vault || fail "Kopia could not store the snapshots."
report true ""
