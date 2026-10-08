#!/usr/bin/env bash
# Seeds the nightly backup's credentials into Vault and Scaleway Secret Manager, run by an owner
# on their own machine. It makes the backup-writer key and the Kopia password itself and hands
# them straight on, so neither ever reaches the screen, a file or a command line.
#
#   scripts/seed-backup-credentials.sh                   first seeding; safe to run again
#   scripts/seed-backup-credentials.sh --new-writer-key  replaces the writer key, deletes the old
#
# It opens its own port-forward to Vault and signs in through the site in a browser; the Vault
# token stays in this process and is revoked on exit. platform/docs/backup.md has the rest.
set -euo pipefail

REGION=nl-ams
SECRET_NAME=kopia-repository-password
WRITER_APP=backup-writer
VAULT_PATH=secret/platform/backup
BLUESHELL_OIDC=https://esa-blueshell.nl/api
API=${SCW_API_URL:-https://api.scaleway.com}

new_writer=false
case "${1:-}" in
  "") ;;
  --new-writer-key) new_writer=true ;;
  *) echo "Usage: $0 [--new-writer-key]" >&2; exit 64 ;;
esac

die() { echo "Stopped: $*" >&2; exit 1; }
step() { echo "- $*"; }

for tool in kubectl vault curl jq openssl; do
  command -v "$tool" >/dev/null || die "$tool is not installed."
done

if [[ -z ${SCW_SECRET_KEY:-} ]]; then
  read -rsp "Your Scaleway secret key (not shown): " SCW_SECRET_KEY
  echo
fi
[[ -n $SCW_SECRET_KEY ]] || die "No Scaleway key given."
if [[ -z ${SCW_DEFAULT_ORGANIZATION_ID:-} ]]; then
  read -rp "Scaleway organization ID: " SCW_DEFAULT_ORGANIZATION_ID
fi
# The organization's first project shares its ID, and the backups live there.
SCW_DEFAULT_PROJECT_ID=${SCW_DEFAULT_PROJECT_ID:-$SCW_DEFAULT_ORGANIZATION_ID}

# The key reaches curl through a file descriptor, never as an argument `ps` would show.
scw() {
  local method=$1 path=$2
  shift 2
  curl -sS --fail-with-body -X "$method" -H @<(printf 'X-Auth-Token: %s\n' "$SCW_SECRET_KEY") \
    -H 'Content-Type: application/json' "$API$path" "$@"
}

forward_pid=""
cleanup() {
  if [[ -n ${VAULT_TOKEN:-} ]]; then
    vault token revoke -self >/dev/null 2>&1 && echo "- Your Vault token is revoked."
  fi
  [[ -n $forward_pid ]] && kill "$forward_pid" 2>/dev/null
  unset VAULT_TOKEN SCW_SECRET_KEY
}
trap cleanup EXIT

# Vault's public host sits behind the site's sign-in, which the vault CLI cannot pass, so the
# script reaches the Service directly. An exported VAULT_ADDR skips this.
if [[ -z ${VAULT_ADDR:-} ]]; then
  # Never kubectl's current context: on a machine with several clusters that is whichever was
  # used last, and every cluster may have a data-system/vault.
  context=${KUBE_CONTEXT:-blueshell}
  kubectl config get-contexts -o name | grep -qx "$context" ||
    die "kubectl has no context \"$context\". Name Blueshell's with KUBE_CONTEXT=<name> $0"
  server=$(kubectl config view -o jsonpath="{.clusters[?(@.name==\"$(kubectl config view -o jsonpath="{.contexts[?(@.name==\"$context\")].context.cluster}")\")].cluster.server}")
  read -rp "kubectl context \"$context\" ($server). Is this Blueshell's cluster? Type yes: " answer ||
    die "No answer given."
  [[ $answer == yes ]] || die "Not confirmed. Pick the cluster with KUBE_CONTEXT=<name> $0"
  log=$(mktemp)
  kubectl --context "$context" -n data-system port-forward svc/vault :8200 >"$log" 2>&1 &
  forward_pid=$!
  port=""
  for _ in $(seq 1 30); do
    port=$(sed -n 's/^Forwarding from 127\.0\.0\.1:\([0-9]*\) .*/\1/p' "$log" | head -1)
    [[ -n $port ]] && break
    kill -0 "$forward_pid" 2>/dev/null || break
    sleep 0.5
  done
  [[ -n $port ]] || die "kubectl could not reach Vault: $(tail -1 "$log")"
  rm -f "$log"
  export VAULT_ADDR=http://127.0.0.1:$port
  step "Reaching Vault through a port-forward on $port."
fi

# Kept out of ~/.vault-token: the token lives in this process and is revoked on exit.
if [[ -z ${VAULT_TOKEN:-} ]]; then
  echo "- A browser opens on the site's sign-in; sign in with an admin account."
  VAULT_TOKEN=$(vault login -method=oidc -no-store -token-only) || die "The Vault sign-in did not complete."
  export VAULT_TOKEN
fi

# --- Preflight: every check before anything is written --------------------------------------

vault token lookup >/dev/null 2>&1 || die "Vault does not accept the token."
# The port-forward reaches whichever cluster kubectl named; only Blueshell's Vault signs in
# through the site, so this is the last check before anything is read or written.
oidc=$(vault read -field=oidc_discovery_url auth/oidc/config 2>/dev/null || true)
[[ $oidc == "$BLUESHELL_OIDC" ]] || die "This Vault signs in through ${oidc:-an unknown provider}, not $BLUESHELL_OIDC. It is not Blueshell's Vault; nothing was written."
step "This is Blueshell's Vault: it signs in through $BLUESHELL_OIDC."
caps=$(vault token capabilities "secret/data/${VAULT_PATH#secret/}")
[[ $caps == *root* || ($caps == *create* && $caps == *update* && $caps == *read*) ]] ||
  die "Your Vault token may not write $VAULT_PATH (it has: $caps)."
step "Vault accepts you and lets you write $VAULT_PATH."

secret_id=$(scw GET "/secret-manager/v1beta1/regions/$REGION/secrets?name=$SECRET_NAME&project_id=$SCW_DEFAULT_PROJECT_ID" |
  jq -r '.secrets[0].id // empty') || die "Scaleway refused your key for Secret Manager."
[[ -n $secret_id ]] || die "Secret Manager has no $SECRET_NAME in $REGION. Has platform/scaleway/backup been applied?"
enabled_versions=$(scw GET "/secret-manager/v1beta1/regions/$REGION/secrets/$secret_id/versions?status=enabled" |
  jq -r '.total_count')

app_id=$(scw GET "/iam/v1alpha1/applications?name=$WRITER_APP&organization_id=$SCW_DEFAULT_ORGANIZATION_ID" |
  jq -r --arg n "$WRITER_APP" '[.applications[] | select(.name == $n)][0].id // empty') ||
  die "Scaleway refused your key for IAM."
[[ -n $app_id ]] || die "IAM has no application $WRITER_APP."
step "Scaleway has $SECRET_NAME ($enabled_versions enabled version(s)) and $WRITER_APP."

if current=$(vault kv get -format=json "$VAULT_PATH" 2>&1); then
  current=$(jq -c '.data.data' <<<"$current")
  path_exists=true
elif [[ $current == *"No value found"* ]]; then
  current='{}'
  path_exists=false
else
  die "Could not read $VAULT_PATH: $current"
fi

# --- The Kopia password: one value, in Vault and in Secret Manager --------------------------

in_vault=$(jq -r '."kopia.password" // empty' <<<"$current")
in_sm=""
if (( enabled_versions > 0 )); then
  (( enabled_versions == 1 )) || die "$SECRET_NAME has $enabled_versions enabled versions; disable all but the one the repository opens with."
  in_sm=$(scw GET "/secret-manager/v1beta1/regions/$REGION/secrets/$secret_id/versions/latest_enabled/access" |
    jq -r '.data' | openssl base64 -d -A)
fi

copy_to_sm=false
copy_to_vault=true
if [[ -n $in_vault && -n $in_sm ]]; then
  [[ $in_vault == "$in_sm" ]] || die "Vault and Secret Manager hold different Kopia passwords. Find the one the repository opens with, by hand."
  password=$in_vault
  copy_to_vault=false
  step "The Kopia password is already in Vault and Secret Manager, and they match."
elif [[ -n $in_vault ]]; then
  password=$in_vault
  copy_to_vault=false
  copy_to_sm=true
  step "The Kopia password is in Vault only; it goes to Secret Manager too."
elif [[ -n $in_sm ]]; then
  password=$in_sm
  step "The Kopia password is in Secret Manager only; it goes to Vault too."
else
  password=$(openssl rand -base64 32)
  copy_to_sm=true
  step "No Kopia password yet; a new one is generated."
fi
unset in_vault in_sm

# --- The writer key ----------------------------------------------------------------------------

old_access=$(jq -r '."writer.access_key" // empty' <<<"$current")
make_writer=false
if [[ -z $old_access ]]; then
  make_writer=true
elif $new_writer; then
  make_writer=true
else
  step "Vault already holds a writer key; it stays. Use --new-writer-key to replace it."
fi

# --- Writing -----------------------------------------------------------------------------------

if $copy_to_sm; then
  data=$(printf '%s' "$password" | openssl base64 -A)
  D=$data jq -n '{data: env.D, description: "Opens the Kopia repository in esa-blueshell-backups"}' |
    scw POST "/secret-manager/v1beta1/regions/$REGION/secrets/$secret_id/versions" --data-binary @- >/dev/null ||
    die "Secret Manager refused the new version. Nothing was written to Vault."
  unset data
  step "Secret Manager holds the Kopia password."
fi

access=""
secret=""
if $make_writer; then
  expires=$(date -u -v+365d +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -d '+365 days' +%Y-%m-%dT%H:%M:%SZ)
  key=$(jq -n --arg a "$app_id" --arg p "$SCW_DEFAULT_PROJECT_ID" --arg e "$expires" --arg d "Nightly backup, $(date -u +%Y-%m-%d)" \
    '{application_id: $a, default_project_id: $p, expires_at: $e, description: $d}' |
    scw POST /iam/v1alpha1/api-keys --data-binary @-) || die "IAM refused to make a writer key."
  access=$(jq -r '.access_key' <<<"$key")
  secret=$(jq -r '.secret_key' <<<"$key")
  unset key
  [[ -n $access && -n $secret && $secret != null ]] || die "IAM answered without a key."
  step "A writer key $access is made, expiring $expires."
fi

# The values travel on stdin, never as arguments another process could read.
if $copy_to_vault || $make_writer; then
  update=$(KP=$password AK=$access SK=$secret jq -n '{"kopia.password": env.KP}
    + (if env.AK != "" then {"writer.access_key": env.AK, "writer.secret_key": env.SK} else {} end)')
  if $path_exists; then
    vault kv patch "$VAULT_PATH" - <<<"$update" >/dev/null || die "Vault refused the write."
  else
    vault kv put "$VAULT_PATH" - <<<"$update" >/dev/null || die "Vault refused the write."
  fi
  step "Vault holds the Kopia password$( [[ -n $access ]] && echo ' and the new writer key')."
fi
unset update password secret

if $make_writer && [[ -n $old_access && $old_access != "$access" ]]; then
  scw DELETE "/iam/v1alpha1/api-keys/$old_access" >/dev/null ||
    die "The old writer key $old_access could not be deleted; delete it in the console."
  step "The old writer key $old_access is deleted."
fi

# --- Checking ----------------------------------------------------------------------------------

after=$(vault kv get -format=json "$VAULT_PATH" | jq -c '.data.data')
for field in kopia.password writer.access_key writer.secret_key; do
  [[ -n $(jq -r --arg f "$field" '.[$f] // empty' <<<"$after") ]] || die "$VAULT_PATH has no $field after writing."
done
unset after
versions=$(scw GET "/secret-manager/v1beta1/regions/$REGION/secrets/$secret_id/versions?status=enabled" | jq -r '.total_count')
keys=$(scw GET "/iam/v1alpha1/api-keys?application_id=$app_id&organization_id=$SCW_DEFAULT_ORGANIZATION_ID" | jq -r '.total_count')
step "Check: Vault has all three fields, Secret Manager $versions enabled version, $WRITER_APP $keys key(s)."
(( keys == 1 )) || echo "  $WRITER_APP has $keys keys; delete every one but $( [[ -n $access ]] && echo "$access" || echo "the one in Vault") in the console." >&2

echo "Done. Run the first night: kubectl --context ${context:-<blueshell>} -n data-system create job --from=cronjob/backup backup-first"
