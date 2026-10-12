#!/usr/bin/env bash
# Rehearses the routine restore: the newest MariaDB dump and Vault snapshot of one night come from
# Scaleway into a throwaway stack on this machine and are read back against the live cluster.
# Three holders of Vault's unseal shares type their share twice, once to unseal and once for a
# root token. No production secret reaches the screen, a file or a command line.
#
#   scripts/rehearse-restore.sh
#
# It signs in to the live Vault through the site in a browser, like seed-backup-credentials.sh,
# and deletes the stack, the downloaded backup and its tokens on exit. platform/docs/backup.md
# describes the restore itself.
set -euo pipefail

VAULT_PATH=secret/platform/backup
BLUESHELL_OIDC=https://esa-blueshell.nl/api
BUCKET=esa-blueshell-backups
PROJECT=blueshell-rehearsal
RECOVERY=$(cd "$(dirname "$0")/../platform/recovery" && pwd)

die() { echo "Stopped: $*" >&2; exit 1; }
step() { echo "- $*"; }
check() { echo "  $1: $2"; [[ $1 == ok ]] || failed=true; }
failed=false

for tool in kubectl vault jq docker openssl; do
  command -v "$tool" >/dev/null || die "$tool is not installed."
done
docker info >/dev/null 2>&1 || die "Docker is not running."

work=$(mktemp -d "${TMPDIR:-/tmp}/$PROJECT.XXXXXX")
cp "$RECOVERY"/docker-compose.yml "$RECOVERY"/vault.hcl "$work"/
mkdir -p "$work"/restore "$work"/kopia
echo "DB_PASSWORD=$(openssl rand -hex 16)" > "$work/.env"
# The recovery stack keeps Vault inside Docker; the rehearsal reaches it from here instead, so
# shares are typed at this machine's prompt rather than passed through docker exec.
cat > "$work/rehearsal.yml" <<'EOF'
services:
  vault:
    ports:
      - "127.0.0.1::8200"
EOF
dc() { docker compose -p "$PROJECT" --project-directory "$work" -f "$work/docker-compose.yml" -f "$work/rehearsal.yml" "$@"; }

forward_pid=""
live_token=""
cleanup() {
  [[ -n $live_token ]] && VAULT_ADDR=$live_addr VAULT_TOKEN=$live_token vault token revoke -self >/dev/null 2>&1
  [[ -n $forward_pid ]] && kill "$forward_pid" 2>/dev/null
  dc down -v >/dev/null 2>&1 || true
  # Kopia writes the restore as root, which a Linux user cannot delete.
  rm -rf "$work" 2>/dev/null ||
    { docker run --rm -v "$work:/w" mariadb:10.11 rm -rf /w/restore /w/kopia >/dev/null 2>&1; rm -rf "$work"; }
  unset KOPIA_PASSWORD AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY restored_token
  echo "- The stack, the downloaded backup and the Vault tokens are gone."
}
trap cleanup EXIT

# --- The live cluster ---------------------------------------------------------------------------

# Never kubectl's current context: every cluster may have a data-system/vault.
context=${KUBE_CONTEXT:-blueshell}
K=(kubectl --context "$context")
kubectl config get-contexts -o name | grep -qx "$context" ||
  die "kubectl has no context \"$context\". Name Blueshell's with KUBE_CONTEXT=<name> $0"
server=$(kubectl config view -o jsonpath="{.clusters[?(@.name==\"$(kubectl config view -o jsonpath="{.contexts[?(@.name==\"$context\")].context.cluster}")\")].cluster.server}")
read -rp "kubectl context \"$context\" ($server). Is this Blueshell's cluster? Type yes: " answer ||
  die "No answer given."
[[ $answer == yes ]] || die "Not confirmed. Pick the cluster with KUBE_CONTEXT=<name> $0"

log=$(mktemp)
"${K[@]}" -n data-system port-forward svc/vault-v2 :8200 >"$log" 2>&1 &
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
live_addr=http://127.0.0.1:$port

echo "- A browser opens on the site's sign-in; sign in with an admin account."
live_token=$(VAULT_ADDR=$live_addr vault login -method=oidc -no-store -token-only) ||
  die "The Vault sign-in did not complete."
live() { VAULT_ADDR=$live_addr VAULT_TOKEN=$live_token vault "$@"; }
oidc=$(live read -field=oidc_discovery_url auth/oidc/config 2>/dev/null || true)
[[ $oidc == "$BLUESHELL_OIDC" ]] || die "This Vault signs in through ${oidc:-an unknown provider}, not $BLUESHELL_OIDC."
step "This is Blueshell's Vault."

KOPIA_PASSWORD=$(live kv get -field=kopia.password "$VAULT_PATH") || die "Vault would not hand over $VAULT_PATH."
AWS_ACCESS_KEY_ID=$(live kv get -field=writer.access_key "$VAULT_PATH")
AWS_SECRET_ACCESS_KEY=$(live kv get -field=writer.secret_key "$VAULT_PATH")
export KOPIA_PASSWORD AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY

# --- Download one night ------------------------------------------------------------------------

kopia() { dc run --rm -T -e KOPIA_PASSWORD -e AWS_ACCESS_KEY_ID -e AWS_SECRET_ACCESS_KEY kopia "$@"; }
kopia repository connect s3 --bucket="$BUCKET" --endpoint=s3.nl-ams.scw.cloud --region=nl-ams \
  --readonly >/dev/null || die "Kopia could not connect to the repository."
snapshots=$(kopia snapshot list --all --json)

# The newest Vault snapshot, and the dump of the same run, taken minutes before it.
vault_snap=$(jq -c '[.[] | select(.source.path == "/work/vault")] | max_by(.startTime) // empty' <<<"$snapshots")
[[ -n $vault_snap ]] || die "The repository holds no Vault snapshot."
vault_time=$(jq -r .startTime <<<"$vault_snap")
db_snap=$(jq -c --arg t "$vault_time" '
  def epoch: sub("\\.[0-9]+"; "") | fromdateiso8601;
  [.[] | select(.source.path == "/work/mariadb")
       | select((($t | epoch) - (.startTime | epoch)) | . >= 0 and . < 3600)]
  | max_by(.startTime) // empty' <<<"$snapshots")
[[ -n $db_snap ]] || die "No MariaDB snapshot comes from the same run as the Vault snapshot of $vault_time."
db_time=$(jq -r .startTime <<<"$db_snap")
step "Restoring the run of $db_time: MariaDB $(jq -r .id <<<"$db_snap"), Vault $(jq -r .id <<<"$vault_snap")."

kopia snapshot restore "$(jq -r .id <<<"$db_snap")" /restore/mariadb >/dev/null
kopia snapshot restore "$(jq -r .id <<<"$vault_snap")" /restore/vault >/dev/null
[[ -s $work/restore/mariadb/blueshell.sql.gz ]] || die "The MariaDB snapshot holds no blueshell.sql.gz."
[[ -s $work/restore/vault/vault.snap ]] || die "The Vault snapshot holds no vault.snap."

# --- MariaDB --------------------------------------------------------------------------------------

dc up -d db vault >/dev/null 2>&1 || die "The recovery stack did not start."
for _ in $(seq 1 60); do
  dc exec -T db mariadb -uroot -e "SELECT 1" >/dev/null 2>&1 && break
  sleep 1
done
dc exec -T db mariadb -uroot -e "CREATE DATABASE blueshell"
gunzip -c "$work/restore/mariadb/blueshell.sql.gz" | dc exec -T db mariadb -uroot blueshell ||
  die "The dump did not load."
step "The dump is loaded."

echo "Database read-back"
# Rows from the day before the backup, so nothing written since, or around 03:00, counts.
cut=$(jq -rn --arg t "$db_time" '$t[:10] | strptime("%Y-%m-%d") | mktime - 86400 | strftime("%Y-%m-%d 00:00:00")')
query="SELECT COUNT(*), MAX(id) FROM users WHERE created_at < '$cut'"
restored_rows=$(dc exec -T db mariadb -uroot -N -B blueshell -e "$query")
# shellcheck disable=SC2016 # the password and query expand in the pod, not here
live_rows=$(live kv get -field=root-password secret/platform/mariadb |
  "${K[@]}" -n data-system exec -i mariadb-0 -c mariadb -- \
  sh -c 'MYSQL_PWD=$(cat) exec mariadb -uroot -N -B blueshell -e "$1"' sh "$query") ||
  die "The live database did not answer."
if [[ $restored_rows == "$live_rows" ]]; then
  check ok "users created before $cut: $(tr '\t' ' ' <<<"$restored_rows") (count, highest id) in both"
else
  check FAILED "users created before $cut: restored $(tr '\t' ' ' <<<"$restored_rows"), live $(tr '\t' ' ' <<<"$live_rows")"
fi

# --- Vault ----------------------------------------------------------------------------------------

scratch_addr=http://$(dc port vault 8200)
scratch() { VAULT_ADDR=$scratch_addr VAULT_TOKEN=${restored_token:-} vault "$@"; }
sealed() { scratch status -format=json 2>/dev/null | jq -r .sealed; }
# vault status exits 2 while sealed or uninitialised, and 1 while it cannot be reached.
for _ in $(seq 1 30); do
  rc=0; scratch status >/dev/null 2>&1 || rc=$?
  [[ $rc -ne 1 ]] && break
  sleep 1
done

# A one-share Vault only to receive the snapshot; afterwards production's shares rule.
init=$(scratch operator init -key-shares=1 -key-threshold=1 -format=json)
scratch operator unseal "$(jq -r '.unseal_keys_b64[0]' <<<"$init")" >/dev/null
VAULT_ADDR=$scratch_addr VAULT_TOKEN=$(jq -r .root_token <<<"$init") \
  vault operator raft snapshot restore -force "$work/restore/vault/vault.snap"
unset init
for _ in $(seq 1 30); do
  [[ $(sealed) == true ]] && break
  sleep 1
done
[[ $(sealed) == true ]] || die "The throwaway Vault did not take the snapshot."
step "The Vault snapshot is loaded; it is sealed with production's shares."

echo
echo "Three share holders now unseal it, each typing their own share."
while [[ $(sealed) == true ]]; do
  echo "Unseal key (will be hidden):"
  scratch operator unseal >/dev/null || echo "  Vault refused that share."
done
[[ $(sealed) == false ]] || die "The throwaway Vault stopped answering."
step "Unsealed."

echo
echo "Three share holders now make a root token for it, each typing their share again."
otp_and_nonce=$(scratch operator generate-root -init -format=json)
nonce=$(jq -r .nonce <<<"$otp_and_nonce")
otp=$(jq -r .otp <<<"$otp_and_nonce")
encoded=""
while [[ -z $encoded ]]; do
  echo "Unseal key (will be hidden):"
  # Vault writes its own prompt to stdout, ahead of the JSON.
  if progress=$(scratch operator generate-root -nonce="$nonce" -format=json 2>/dev/null | sed -n '/^{/,$p'); then
    encoded=$(jq -r '.encoded_token // empty' <<<"$progress")
  else
    echo "  Vault refused that share."
  fi
done
restored_token=$(printf '%s' "$encoded" | scratch operator generate-root -decode=- -otp="$otp" | tr -d '[:space:]')
unset encoded otp otp_and_nonce
[[ $restored_token == hvs.* ]] || die "The root token did not decode."

echo "Vault read-back"
live_meta=$(live kv metadata get -format=json "$VAULT_PATH" | jq -c '{version: .data.current_version, written: .data.updated_time}')
restored_meta=$(scratch kv metadata get -format=json "$VAULT_PATH" | jq -c '{version: .data.current_version, written: .data.updated_time}')
if [[ $restored_meta == "$live_meta" ]]; then
  check ok "$VAULT_PATH has the same version in both: $restored_meta"
else
  check FAILED "$VAULT_PATH differs: restored $restored_meta, live $live_meta (fine only if it was rewritten since the backup)"
fi
fields=$(scratch kv get -format=json "$VAULT_PATH" | jq -r '.data.data | keys | join(" ")')
if [[ $fields == "kopia.password writer.access_key writer.secret_key" ]]; then
  check ok "$VAULT_PATH holds its three fields"
else
  check FAILED "$VAULT_PATH holds: $fields"
fi

opened=0; refused=0
while IFS=$'\t' read -r user sealed_address; do
  if scratch write -field=plaintext transit/decrypt/api-address ciphertext="$sealed_address" \
      context="$(printf '%s' "address:$user" | base64)" >/dev/null 2>&1; then
    opened=$((opened + 1))
  else
    refused=$((refused + 1))
  fi
done < <(dc exec -T db mariadb -uroot -N -B blueshell -e \
  "SELECT u.id, a.sealed_address FROM addresses a JOIN users u ON u.address_id = a.id WHERE a.sealed_address IS NOT NULL")
if [[ $opened -gt 0 && $refused -eq 0 ]]; then
  check ok "every sealed address opens against the restored Vault: $opened"
else
  check FAILED "sealed addresses: $opened opened, $refused refused"
fi

echo
if $failed; then
  echo "The rehearsal of the run of $db_time FAILED on the checks above."
  exit 1
fi
echo "The rehearsal of the run of $db_time passed."
