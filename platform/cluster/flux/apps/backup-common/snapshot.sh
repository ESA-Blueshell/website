#!/usr/bin/env bash
# Stores this Job's sources in the Kopia repository and reports each group to Gatus on its own,
# so one store failing neither hides nor stops the others.
#   BACKUP_GROUPS    space-separated group=path[,path...]; each group is one Gatus endpoint
#   GATUS_PUSH_BASE  the push URL up to the group's name
#   ALLOW_CREATE     true only in data-system, whose first night creates the repository
#   FINALLY          a command run however this step ends, a deadline's SIGTERM included
set -uo pipefail

[[ -n ${FINALLY:-} ]] && trap 'sh -c "$FINALLY"' EXIT
# Bash runs a trap only between commands, so a snapshot runs in the background and is waited
# on: a deadline's SIGTERM then stops it at once, and FINALLY runs before the SIGKILL.
child=""
trap '[[ -n $child ]] && kill "$child" 2>/dev/null; exit 143' TERM
snapshot() {
  kopia snapshot create "$@" &
  child=$!
  wait "$child"
  local status=$?
  child=""
  return $status
}

read -ra groups <<<"$BACKUP_GROUPS"

report() {
  curl -fsS -m 30 -o /dev/null -X POST --get \
    -H "Authorization: Bearer $(cat /work/secrets/gatus-token 2>/dev/null)" \
    --data-urlencode "success=$2" --data-urlencode "error=$3" "$GATUS_PUSH_BASE$1/external" ||
    echo "Gatus did not take the report for $1; its 26-hour heartbeat still alerts." >&2
}

fail_all() {
  echo "$1" >&2
  for spec in "${groups[@]}"; do report "${spec%%=*}" false "$1"; done
  exit 1
}

[ -s /work/status/failed ] && fail_all "$(cat /work/status/failed)"

KOPIA_PASSWORD=$(< /work/secrets/kopia.password)
AWS_ACCESS_KEY_ID=$(< /work/secrets/writer.access_key)
AWS_SECRET_ACCESS_KEY=$(< /work/secrets/writer.secret_key)
export KOPIA_PASSWORD AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY

storage=(s3 --bucket="$BACKUP_BUCKET" --endpoint="$BACKUP_ENDPOINT" --region="$BACKUP_REGION")
# A fixed identity, so every night's snapshots of a source share one history and one
# maintenance owner, whichever namespace took them.
identity=(--override-hostname=blueshell --override-username=backup)

if ! out=$(kopia repository connect "${storage[@]}" "${identity[@]}" 2>&1); then
  [[ $out == *"not initialized"* ]] || fail_all "Kopia could not connect: ${out##*$'\n'}"
  # Two Jobs must never both create it, so only data-system's may.
  [[ ${ALLOW_CREATE:-false} == true ]] ||
    fail_all "The repository does not exist yet; the data-system backup creates it on its first night."

  # Every blob is written under COMPLIANCE retention to match the bucket's lock, and full
  # maintenance extends it, so live data never falls out of protection.
  kopia repository create "${storage[@]}" "${identity[@]}" \
    --retention-mode=COMPLIANCE --retention-period="$LOCK_PERIOD" ||
    fail_all "Kopia could not create the repository."
  kopia repository connect "${storage[@]}" "${identity[@]}" ||
    fail_all "Kopia could not connect to the repository it just created."
  kopia policy set --global --keep-latest=1 --keep-hourly=0 --keep-daily=7 --keep-weekly=4 \
    --keep-monthly=0 --keep-annual=0 || fail_all "Kopia could not set the retention."
  kopia maintenance set --owner=me --extend-object-locks=true ||
    fail_all "Kopia could not set up maintenance."
fi

status=0
for spec in "${groups[@]}"; do
  group=${spec%%=*}
  IFS=, read -ra paths <<<"${spec#*=}"
  if [ -s "/work/status/$group.failed" ]; then
    report "$group" false "$(head -1 "/work/status/$group.failed")"
    status=1
  elif snapshot "${paths[@]}"; then
    report "$group" true ""
  else
    report "$group" false "Kopia could not store ${paths[*]}."
    status=1
  fi
done
exit $status
