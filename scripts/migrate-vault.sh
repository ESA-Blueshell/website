#!/usr/bin/env bash
# Moves Blueshell's Vault into vault-v2 (#2392) while the old one is still unsealed. The old Vault
# was initialised with a single unseal key, which is lost, so it is copied rather than rekeyed:
# every KV secret, the transit keys by backup and restore, and the policies, after which
# bootstrap-auth.sh rebuilds the auth methods, roles, database engine and OIDC on vault-v2.
#
#   scripts/migrate-vault.sh           initialises vault-v2 on the first run, then copies and checks
#   scripts/migrate-vault.sh --check   only compares the two; writes nothing
#   scripts/migrate-vault.sh --finish  after the switch: revokes vault-v2's root token
#
# Safe to run again: a second run copies only what changed, so it doubles as the last sync before
# the switch. Everything stays in this process: nothing is written to disk, and no secret reaches
# a command line. The old Vault is only ever read, except that its transit keys are made
# exportable, which cannot be undone. vault-v2's root token waits in the Secret
# vault-v2-bootstrap-token until --finish revokes it; the bootstrap Job signs in by its service
# account instead.
# platform/docs/vault-migration.md has the whole procedure.
set -euo pipefail

BLUESHELL_OIDC=https://esa-blueshell.nl/api
NS=data-system
NEW_POD=vault-v2-0
TOKEN_SECRET=vault-v2-bootstrap-token
BOOTSTRAP=$(cd "$(dirname "$0")/../platform/cluster/flux/apps/data/vault" && pwd)/bootstrap-auth.sh
KNOWN_MOUNTS="cubbyhole/ database/ identity/ secret/ sys/ transit/"
KNOWN_AUTH="kubernetes/ oidc/ token/"
KNOWN_DB_ROLES="api backup"
# role:namespace:service account, as bootstrap-auth.sh binds them.
K8S_ROLES="api:default:api migrate:default:migrate stalwart:mail-system:stalwart
  backup:data-system:backup backup-mail:mail-system:backup backup-gatus:utility-system:backup
  vso:data-system:vault-secrets-operator bootstrap:data-system:vault-bootstrap-auth"

check_only=false
finish=false
case "${1:-}" in
  "") ;;
  --check) check_only=true ;;
  --finish) finish=true ;;
  *) echo "Usage: $0 [--check | --finish]" >&2; exit 64 ;;
esac

die() { echo "Stopped: $*" >&2; exit 1; }
step() { echo "- $*"; }
failed=false
fail() { echo "  FAILED: $*"; failed=true; }

for tool in kubectl vault jq openssl; do
  command -v "$tool" >/dev/null || die "$tool is not installed."
done

forward_pids=()
old_token=""
signed_in=false
cleanup() {
  $signed_in && old token revoke -self >/dev/null 2>&1
  for pid in ${forward_pids[@]+"${forward_pids[@]}"}; do kill "$pid" 2>/dev/null; done
  unset old_token new_token init
}
trap cleanup EXIT

# --- Reaching both Vaults ---------------------------------------------------------------------

# Never kubectl's current context: every cluster may have a data-system/vault.
context=${KUBE_CONTEXT:-blueshell}
K=(kubectl --context "$context" -n "$NS")
kubectl config get-contexts -o name | grep -qx "$context" ||
  die "kubectl has no context \"$context\". Name Blueshell's with KUBE_CONTEXT=<name> $0"
server=$(kubectl config view -o jsonpath="{.clusters[?(@.name==\"$(kubectl config view -o jsonpath="{.contexts[?(@.name==\"$context\")].context.cluster}")\")].cluster.server}")
read -rp "kubectl context \"$context\" ($server). Is this Blueshell's cluster? Type yes: " answer ||
  die "No answer given."
[[ $answer == yes ]] || die "Not confirmed. Pick the cluster with KUBE_CONTEXT=<name> $0"

# Prints the local port of a new port-forward to $1.
forward() {
  local log port="" pid
  log=$(mktemp)
  "${K[@]}" port-forward "$1" :8200 >"$log" 2>&1 &
  pid=$!
  for _ in $(seq 1 30); do
    port=$(sed -n 's/^Forwarding from 127\.0\.0\.1:\([0-9]*\) .*/\1/p' "$log" | head -1)
    [[ -n $port ]] && break
    kill -0 "$pid" 2>/dev/null || break
    sleep 0.5
  done
  [[ -n $port ]] || { echo "kubectl could not reach $1: $(tail -1 "$log")" >&2; rm -f "$log"; return 1; }
  rm -f "$log"
  echo "$port $pid"
}
if [[ -z ${OLD_VAULT_ADDR:-} ]] && ! $finish; then
  read -r port pid < <(forward svc/vault) || die "No port-forward to the old Vault."
  forward_pids+=("$pid"); OLD_VAULT_ADDR=http://127.0.0.1:$port
fi
if [[ -z ${NEW_VAULT_ADDR:-} ]]; then
  read -r port pid < <(forward "pod/$NEW_POD") || die "No port-forward to $NEW_POD. Is the vault-v2 release deployed?"
  forward_pids+=("$pid"); NEW_VAULT_ADDR=http://127.0.0.1:$port
fi
old() { VAULT_ADDR=$OLD_VAULT_ADDR VAULT_TOKEN=$old_token vault "$@"; }
new() { VAULT_ADDR=$NEW_VAULT_ADDR VAULT_TOKEN=${new_token:-} vault "$@"; }
# A rerun after --finish needs a root token made with `vault operator generate-root`.
stored_token() {
  if [[ -n ${NEW_VAULT_TOKEN:-} ]]; then printf '%s' "$NEW_VAULT_TOKEN"; return; fi
  "${K[@]}" get secret "$TOKEN_SECRET" -o jsonpath='{.data.token}' 2>/dev/null | base64 -d
}

# --- After the switch: no root token left lying around -------------------------------------------

if $finish; then
  new_token=$(stored_token) || true
  [[ -n $new_token ]] || die "There is no $TOKEN_SECRET; the root token is already gone."
  new token lookup >/dev/null 2>&1 || die "vault-v2 refuses the stored root token; it may be revoked already."
  # The bootstrap Job must manage without it before it goes.
  jwt=$("${K[@]}" create token vault-bootstrap-auth --duration=10m) ||
    die "No token for the service account vault-bootstrap-auth."
  job_token=$(printf '%s' "$jwt" | new write -field=token auth/kubernetes/login role=bootstrap jwt=- 2>/dev/null) ||
    die "The bootstrap Job cannot sign in to vault-v2 yet. Run this script without --finish from a checkout that has the bootstrap role, then again with --finish."
  caps=$(VAULT_ADDR=$NEW_VAULT_ADDR VAULT_TOKEN=$job_token vault token capabilities sys/policies/acl/api)
  VAULT_ADDR=$NEW_VAULT_ADDR VAULT_TOKEN=$job_token vault token revoke -self >/dev/null 2>&1 || true
  unset jwt job_token
  [[ $caps == *update* ]] || die "The bootstrap role may not write policies (it has: $caps)."
  new token revoke -self >/dev/null
  "${K[@]}" delete secret "$TOKEN_SECRET" --ignore-not-found >/dev/null
  unset new_token
  step "vault-v2's root token is revoked and $TOKEN_SECRET deleted."
  step "When one is needed, three share holders make it with vault operator generate-root."
  exit 0
fi

if [[ -n ${OLD_VAULT_TOKEN:-} ]]; then
  old_token=$OLD_VAULT_TOKEN
else
  echo "- A browser opens on the site's sign-in; sign in with an admin account."
  old_token=$(VAULT_ADDR=$OLD_VAULT_ADDR vault login -method=oidc -no-store -token-only) ||
    die "The Vault sign-in did not complete."
  signed_in=true
fi
oidc=$(old read -field=oidc_discovery_url auth/oidc/config 2>/dev/null || true)
[[ $oidc == "$BLUESHELL_OIDC" ]] || die "The old Vault signs in through ${oidc:-an unknown provider}, not $BLUESHELL_OIDC."
step "The old Vault is Blueshell's, and it accepts you."

# --- What the old Vault holds -------------------------------------------------------------------

# Anything bootstrap-auth.sh does not build would be lost silently, so it stops the run instead.
unknown() { # known list, actual list
  local name out=""
  for name in $2; do [[ " $1 " == *" $name "* ]] || out+=" $name"; done
  echo "${out# }"
}
mounts=$(old secrets list -format=json | jq -r 'keys | join(" ")')
[[ -z $(unknown "$KNOWN_MOUNTS" "$mounts") ]] || die "The old Vault has mounts this script does not move: $(unknown "$KNOWN_MOUNTS" "$mounts")"
[[ $(old secrets list -format=json | jq -r '."secret/".options.version') == 2 ]] || die "secret/ is not a KV version 2 engine."
auths=$(old auth list -format=json | jq -r 'keys | join(" ")')
[[ -z $(unknown "$KNOWN_AUTH" "$auths") ]] || die "The old Vault has auth methods this script does not move: $(unknown "$KNOWN_AUTH" "$auths")"
k8s_known=$(for r in $K8S_ROLES; do printf '%s ' "${r%%:*}"; done)
k8s_roles=$(old list -format=json auth/kubernetes/role 2>/dev/null | jq -r 'join(" ")' || true)
[[ -z $(unknown "$k8s_known" "$k8s_roles") ]] || die "The old Vault has Kubernetes roles bootstrap-auth.sh does not make: $(unknown "$k8s_known" "$k8s_roles")"
db_roles=$(old list -format=json database/roles 2>/dev/null | jq -r 'join(" ")' || true)
[[ -z $(unknown "$KNOWN_DB_ROLES" "$db_roles") ]] || die "The old Vault has database roles bootstrap-auth.sh does not make: $(unknown "$KNOWN_DB_ROLES" "$db_roles")"
transit_keys=$(old list -format=json transit/keys 2>/dev/null | jq -r 'join(" ")' || true)
policies=$(old policy list | grep -vx root | tr '\n' ' ')

kv_paths() { # every secret under secret/$1, one per line
  local entry
  old list -format=json "secret/metadata/$1" 2>/dev/null | jq -r '.[]' | while IFS= read -r entry; do
    if [[ $entry == */ ]]; then kv_paths "$1$entry"; else printf '%s\n' "$1$entry"; fi
  done
}
secrets=()
while IFS= read -r path; do secrets+=("$path"); done < <(kv_paths "")
step "The old Vault holds ${#secrets[@]} secrets, transit keys: $transit_keys."

# --- vault-v2: initialise, or open what an earlier run made -----------------------------------

new_status=$(new status -format=json 2>/dev/null || true)
[[ -n $new_status ]] || die "vault-v2 does not answer."
if [[ $(jq -r .initialized <<<"$new_status") == false ]]; then
  $check_only && die "vault-v2 is not initialised yet; run without --check first."
  # A token left by an earlier vault-v2 that was wiped belongs to nothing.
  "${K[@]}" delete secret "$TOKEN_SECRET" --ignore-not-found >/dev/null

  init=$(new operator init -key-shares=5 -key-threshold=3 -format=json)
  new_token=$(printf '%s' "$init" | jq -r .root_token)
  # Kept first, so a run that stops halfway can pick up again.
  printf '%s' "$new_token" | "${K[@]}" create secret generic "$TOKEN_SECRET" --from-file=token=/dev/stdin >/dev/null ||
    die "The root token could not be stored; wipe vault-v2 and start again (docs)."

  clear_screen() { printf '\033[3J\033[H\033[2J'; }
  echo
  echo "vault-v2 has five unseal shares; any three open it. Each is shown once. Store each in your"
  echo "password manager as \"Blueshell Vault v2, unseal share N\" before going on."
  read -rp "Press Enter to see share 1. " _
  for i in 0 1 2 3 4; do
    clear_screen
    echo "Unseal share $((i + 1)) of 5:"
    echo
    printf '%s' "$init" | jq -r ".unseal_keys_b64[$i]"
    echo
    read -rp "Press Enter once it is stored. The screen clears. " _
  done
  clear_screen

  # Three shares typed back from the password manager prove they were stored.
  for i in 1 3 4; do
    while :; do
      read -rsp "Type share $((i + 1)) back from your password manager (hidden): " typed; echo
      [[ $typed == "$(printf '%s' "$init" | jq -r ".unseal_keys_b64[$i]")" ]] && break
      echo "  That is not share $((i + 1)). Check the entry and try again."
    done
  done
  unset typed
  for i in 0 1 2; do
    printf '%s' "$init" | jq -r ".unseal_keys_b64[$i]" | new write sys/unseal key=- >/dev/null
  done
  unset init
  step "vault-v2 is initialised 5-of-3 and unsealed; its root token is in the Secret $TOKEN_SECRET."
else
  new_token=$(stored_token) || true
  [[ -n $new_token ]] ||
    die "vault-v2 has no root token in $TOKEN_SECRET. Make one with vault operator generate-root and pass it as NEW_VAULT_TOKEN."
  while [[ $(new status -format=json 2>/dev/null | jq -r .sealed) == true ]]; do
    echo "vault-v2 is sealed. Unseal key (hidden):"
    new operator unseal >/dev/null || echo "  vault-v2 refused that share."
  done
fi
for _ in $(seq 1 60); do
  [[ $(new read -format=json sys/leader 2>/dev/null | jq -r .data.is_self) == true ]] && break
  sleep 1
done
new token lookup >/dev/null 2>&1 || die "vault-v2 does not accept its root token."

# --- Copy ---------------------------------------------------------------------------------------

if ! $check_only; then
  enabled=$(new secrets list -format=json | jq -r 'keys | join(" ")')
  [[ " $enabled " == *" secret/ "* ]] || new secrets enable -path=secret kv-v2 >/dev/null
  [[ " $enabled " == *" transit/ "* ]] || new secrets enable transit >/dev/null
  old read -format=json secret/config | jq -c '.data' | new write secret/config - >/dev/null
  # The chart mounts /vault/audit for it; Vault refuses every request it cannot log.
  audits=$(new audit list -format=json 2>/dev/null | jq 'length' 2>/dev/null || echo 0)
  [[ ${audits:-0} -gt 0 ]] || new audit enable file file_path=/vault/audit/audit.log >/dev/null

  step "Copying ${#secrets[@]} secrets."
  copied=0; gone=0
  for path in ${secrets[@]+"${secrets[@]}"}; do
    src=$(old read -format=json "secret/data/$path")
    if [[ $(printf '%s' "$src" | jq -c .data.data) == null ]]; then gone=$((gone + 1)); continue; fi
    target=$(new read -format=json "secret/data/$path" 2>/dev/null || echo '{"data":{"data":null}}')
    if [[ $(printf '%s' "$src" | jq -cS .data.data) != "$(printf '%s' "$target" | jq -cS .data.data)" ]]; then
      printf '%s' "$src" | jq -c '{data: .data.data}' | new write "secret/data/$path" - >/dev/null
      copied=$((copied + 1))
    fi
    old read -format=json "secret/metadata/$path" |
      jq -c '.data | {max_versions, cas_required, delete_version_after, custom_metadata: (.custom_metadata // {})}' |
      new write "secret/metadata/$path" - >/dev/null
  done
  unset src target
  step "KV: written $copied, already equal $((${#secrets[@]} - copied - gone)), left out because their newest version is deleted $gone."

  for key in $transit_keys; do
    if new read "transit/keys/$key" >/dev/null 2>&1; then continue; fi
    old write "transit/keys/$key/config" exportable=true allow_plaintext_backup=true >/dev/null
    old read -field=backup "transit/backup/$key" | new write "transit/restore/$key" backup=- >/dev/null
    step "Transit: $key restored into vault-v2."
  done

  for policy in $policies; do
    [[ $policy == default ]] && continue
    old policy read "$policy" | new policy write "$policy" - >/dev/null
  done
  step "Policies copied; bootstrap-auth.sh runs in $NEW_POD next."

  # Run inside the pod: the Kubernetes auth config needs its service host and CA.
  if ! out=$({ printf '%s\n' "$new_token"; cat "$BOOTSTRAP"; } |
      "${K[@]}" exec -i "$NEW_POD" -c vault -- \
      sh -c 'IFS= read -r VAULT_TOKEN; export VAULT_TOKEN VAULT_ADDR=http://127.0.0.1:8200; exec sh -s' 2>&1); then
    printf '%s\n' "$out" | tail -20
    die "bootstrap-auth.sh failed on vault-v2. Rerun this script once the cause is fixed."
  fi
  step "bootstrap-auth.sh ran on vault-v2."
fi

# --- Check --------------------------------------------------------------------------------------

echo "Checks"
equal=0
for path in ${secrets[@]+"${secrets[@]}"}; do
  a=$(old read -format=json "secret/data/$path" | jq -cS .data.data)
  [[ $a == null ]] && continue
  b=$(new read -format=json "secret/data/$path" 2>/dev/null | jq -cS .data.data || echo missing)
  if [[ $a == "$b" ]]; then equal=$((equal + 1)); else fail "secret/$path differs"; fi
done
step "KV: $equal secrets equal."
unset a b

for key in $transit_keys; do
  shape='{type, derived, latest_version, min_decryption_version, min_encryption_version,
          auto_rotate_period, keys}'
  if ! new read "transit/keys/$key" >/dev/null 2>&1; then fail "transit/$key is missing"; continue; fi
  if [[ $(old read -format=json "transit/keys/$key" | jq -cS ".data | $shape") != \
        "$(new read -format=json "transit/keys/$key" | jq -cS ".data | $shape")" ]]; then
    fail "transit/$key has other versions or settings"; continue
  fi
  info=$(old read -format=json "transit/keys/$key" | jq .data)
  ctx=()
  [[ $(jq -r .derived <<<"$info") == true ]] && ctx=(context="$(printf 'migration-check' | base64)")
  if [[ $(jq -r .supports_encryption <<<"$info") == true ]]; then
    from=$(jq -r '[.min_decryption_version, .min_encryption_version, 1] | max' <<<"$info")
    for version in $(seq "$from" "$(jq -r .latest_version <<<"$info")"); do
      plain=$(openssl rand -base64 24)
      sealed=$(old write -field=ciphertext "transit/encrypt/$key" plaintext="$plain" key_version="$version" ${ctx[@]+"${ctx[@]}"})
      [[ $(new write -field=plaintext "transit/decrypt/$key" ciphertext="$sealed" ${ctx[@]+"${ctx[@]}"}) == "$plain" ]] ||
        fail "transit/$key version $version does not open on vault-v2"
    done
  fi
  if [[ $(jq -r .supports_signing <<<"$info") == true ]]; then
    input=$(openssl rand -base64 24)
    signature=$(new write -field=signature "transit/sign/$key" input="$input")
    [[ $(old write -field=valid "transit/verify/$key" input="$input" signature="$signature") == true ]] ||
      fail "transit/$key signs differently on vault-v2"
  fi
  step "Transit: $key equal, every version opens."
done

# bootstrap-auth.sh owns the policies it writes, so those follow this checkout rather than the old Vault.
managed=$(sed -n 's/^vault policy write \([a-z-]*\) .*/\1/p' "$BOOTSTRAP" | tr '\n' ' ')
for policy in $policies $managed; do
  if [[ " $managed " == *" $policy "* ]]; then
    new policy read "$policy" >/dev/null 2>&1 || fail "policy $policy is missing"
  else
    [[ $(old policy read "$policy") == "$(new policy read "$policy" 2>/dev/null)" ]] || fail "policy $policy differs"
  fi
done
step "Policies: as bootstrap-auth.sh writes them: $managed; copied: $(unknown "$managed" "$policies")"

for entry in $K8S_ROLES; do
  role=${entry%%:*}; rest=${entry#*:}; namespace=${rest%%:*}; account=${rest#*:}
  if ! jwt=$(kubectl --context "$context" -n "$namespace" create token "$account" --duration=10m 2>/dev/null); then
    echo "  skipped: role $role, there is no service account $namespace/$account"
    continue
  fi
  if token=$(printf '%s' "$jwt" | new write -field=token auth/kubernetes/login role="$role" jwt=- 2>/dev/null); then
    VAULT_ADDR=$NEW_VAULT_ADDR VAULT_TOKEN=$token vault token revoke -self >/dev/null 2>&1 || true
  else
    fail "role $role cannot log in to vault-v2"
  fi
done
unset jwt token
step "Kubernetes roles checked."

if new read database/config/mariadb >/dev/null 2>&1; then
  if lease=$(new read -format=json database/creds/backup 2>/dev/null | jq -r .lease_id); then
    new lease revoke "$lease" >/dev/null
    step "Database: vault-v2 issued and revoked a login."
  else
    fail "vault-v2 could not issue a database login"
  fi
else
  fail "vault-v2 has no database engine config"
fi
[[ $(new read -field=oidc_discovery_url auth/oidc/config 2>/dev/null) == "$BLUESHELL_OIDC" ]] ||
  fail "vault-v2 does not sign in through $BLUESHELL_OIDC"

echo
if $failed; then
  echo "vault-v2 does not match the old Vault yet; see FAILED above. Do not switch."
  exit 1
fi
echo "vault-v2 matches the old Vault. Next: merge the switch PR (platform/docs/vault-migration.md)."
