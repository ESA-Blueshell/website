#!/usr/bin/env bash
# Single-quoted commands below run in pods and containers, and expand there.
# shellcheck disable=SC2016
# Restores the nightly backup from Scaleway: into throwaway containers on this machine to prove it
# opens (rehearse), or back over production (cluster). Every store by default, or the ones named.
#
#   scripts/restore.sh rehearse [STORE...] [--at TIME]
#   scripts/restore.sh cluster  [STORE...] [--at TIME] [--apply]
#
#   STORE    database  MariaDB and Vault, always together: sealed values open only with the Vault
#                      snapshot of the same night. Needs three unseal shares.
#            uploads   the api's uploaded files
#            valkey    Valkey's save
#            stalwart  the mail store
#            gatus     the status page's history
#   --at     the newest backup at or before this moment, UTC: 2026-10-12 or 2026-10-12T03:30
#   --apply  cluster only: actually write production. Without it, cluster is a dry run that
#            downloads and checks the backup and prints every step it would take.
#
# It signs in to the live Vault through the site for the Kopia credentials, as
# platform/bootstrap/secrets.sh does. Shares are typed once and held in this process only; no secret
# reaches a command line or a file. Everything downloaded is deleted on exit, pass or fail.
# platform/docs/backup.md describes what each mode does to each store.
set -euo pipefail
((BASH_VERSINFO[0] >= 4)) || { echo "Needs bash 4 or newer (brew install bash)." >&2; exit 1; }

STORES="database uploads valkey stalwart gatus"
VAULT_PATH=secret/platform/backup
BLUESHELL_OIDC=https://esa-blueshell.nl/api
BUCKET=esa-blueshell-backups
PROJECT=blueshell-restore
ROOT=$(cd "$(dirname "$0")/.." && pwd)
RECOVERY=$ROOT/platform/recovery
APPS=$ROOT/platform/cluster/flux/apps

usage() { sed -n '7,19p' "$0" | sed 's/^# \{0,1\}//' >&2; exit 64; }
die() { echo "Stopped: $*" >&2; exit 1; }
step() { echo "- $*"; }
failed=false
check() { echo "  $1: $2"; [[ $1 == ok ]] || failed=true; }

mode=${1:-}
[[ $mode == rehearse || $mode == cluster ]] || usage
shift
chosen=() at="" apply=false
while (($#)); do
  case $1 in
    --at) at=${2:-}; [[ -n $at ]] || usage; shift 2 ;;
    --apply) [[ $mode == cluster ]] || usage; apply=true; shift ;;
    -h | --help) usage ;;
    *) [[ " $STORES " == *" $1 "* ]] || { echo "Unknown store: $1" >&2; usage; }; chosen+=("$1"); shift ;;
  esac
done
((${#chosen[@]})) || read -ra chosen <<<"$STORES"
wants() { [[ " ${chosen[*]} " == *" $1 "* ]]; }

for tool in kubectl vault jq docker curl openssl; do
  command -v "$tool" >/dev/null || die "$tool is not installed."
done
docker info >/dev/null 2>&1 || die "Docker is not running."

# Images match what production runs, so a backup is opened by the version that wrote it.
image_of() { sed -n "s|^ *image: \\($1:[^ ]*\\).*|\\1|p" "$2" | head -1; }
VALKEY_IMAGE=$(image_of valkey/valkey "$APPS/data/valkey/deployment.yaml")
STALWART_IMAGE=$(image_of stalwartlabs/stalwart "$APPS/mail/stalwart/deployment.yaml")
[[ -n $VALKEY_IMAGE && -n $STALWART_IMAGE ]] || die "Could not read the Valkey and Stalwart images from the manifests."

work=$(mktemp -d "${TMPDIR:-/tmp}/$PROJECT.XXXXXX")
cp "$RECOVERY"/docker-compose.yml "$RECOVERY"/vault.hcl "$work"/
mkdir -p "$work"/restore "$work"/kopia
echo "DB_PASSWORD=$(openssl rand -hex 16)" > "$work/.env"
echo "STALWART_PASSWORD=$(openssl rand -hex 16)" >> "$work/.env"
printf '{"@type":"RocksDb","path":"/var/lib/stalwart/data"}\n' > "$work/stalwart.json"
# Stalwart sits on a network with no way out: a restored queue must never send real mail, and
# its ACME and DNS jobs must not touch the real ones. The probe container talks to it from inside.
cat > "$work/stores.yml" <<EOF
services:
  vault:
    ports:
      - "127.0.0.1::8200"
  valkey:
    image: $VALKEY_IMAGE
    profiles: [stores]
    user: "0"
    command: ["valkey-server", "--dir", "/data", "--dbfilename", "dump.rdb", "--save", "", "--appendonly", "no"]
    volumes:
      - ./restore/valkey:/data
  sqlite:
    image: python:3.13-alpine
    profiles: [tools]
    volumes:
      - ./restore/gatus:/gatus:ro
  stalwart:
    image: $STALWART_IMAGE
    profiles: [stores]
    user: "0"
    environment:
      STALWART_RECOVERY_ADMIN: rehearsal:\${STALWART_PASSWORD}
    volumes:
      - ./restore/stalwart:/var/lib/stalwart
      - ./stalwart.json:/etc/stalwart/config.json:ro
    networks: [sealed]
  probe:
    image: curlimages/curl:8.22.0
    profiles: [tools]
    networks: [sealed]
networks:
  sealed:
    internal: true
EOF
dc() { docker compose -p "$PROJECT" --project-directory "$work" -f "$work/docker-compose.yml" -f "$work/stores.yml" "$@"; }

context=${KUBE_CONTEXT:-blueshell}
forward_pids=()
live_token="" restored_token="" shares=()
resume_flux=() scaled=()
cleanup() {
  # Production first: whatever happened, nothing stays scaled down or unreconciled.
  local entry ns kind name replicas
  for entry in ${scaled[@]+"${scaled[@]}"}; do
    IFS=/ read -r ns kind name replicas <<<"$entry"
    kubectl --context "$context" -n "$ns" scale "$kind/$name" --replicas="$replicas" >/dev/null 2>&1 &&
      echo "- $ns/$name is back at $replicas."
  done
  for entry in ${resume_flux[@]+"${resume_flux[@]}"}; do
    kubectl --context "$context" -n flux-system patch kustomization "$entry" --type=merge \
      -p '{"spec":{"suspend":false}}' >/dev/null 2>&1 && echo "- Flux reconciles $entry again."
  done
  kubectl --context "$context" -n default delete pod uploads-lister --ignore-not-found --wait=false >/dev/null 2>&1 || true
  if $apply; then
    for ns in default data-system mail-system utility-system; do
      kubectl --context "$context" -n "$ns" delete pod restore-helper --ignore-not-found --wait=false >/dev/null 2>&1 || true
    done
  fi
  [[ -n $live_token ]] && VAULT_ADDR=$live_addr VAULT_TOKEN=$live_token vault token revoke -self >/dev/null 2>&1
  for pid in ${forward_pids[@]+"${forward_pids[@]}"}; do kill "$pid" 2>/dev/null; done
  dc --profile stores --profile tools down -v >/dev/null 2>&1 || true
  # Kopia and the containers write as root, which a Linux user cannot delete.
  rm -rf "$work" 2>/dev/null ||
    { docker run --rm -v "$work:/w" busybox:1.38-musl rm -rf /w/restore /w/kopia >/dev/null 2>&1; rm -rf "$work"; }
  unset KOPIA_PASSWORD AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY restored_token live_token shares
  echo "- The downloaded backup, the containers and the tokens are gone."
}
trap cleanup EXIT

# --- The live cluster ---------------------------------------------------------------------------

# Never kubectl's current context: every cluster may have a data-system/vault.
k() { kubectl --context "$context" "$@"; }
kubectl config get-contexts -o name | grep -qx "$context" ||
  die "kubectl has no context \"$context\". Name Blueshell's with KUBE_CONTEXT=<name> $0"
server=$(kubectl config view -o jsonpath="{.clusters[?(@.name==\"$(kubectl config view -o jsonpath="{.contexts[?(@.name==\"$context\")].context.cluster}")\")].cluster.server}")
read -rp "kubectl context \"$context\" ($server). Is this Blueshell's cluster? Type yes: " answer ||
  die "No answer given."
[[ $answer == yes ]] || die "Not confirmed. Pick the cluster with KUBE_CONTEXT=<name> $0"

# Prints "port pid" of a new port-forward.
forward() { # namespace target remote-port
  local log port="" pid
  log=$(mktemp)
  k -n "$1" port-forward "$2" ":$3" >"$log" 2>&1 &
  pid=$!
  for _ in $(seq 1 30); do
    port=$(sed -n 's/^Forwarding from 127\.0\.0\.1:\([0-9]*\) .*/\1/p' "$log" | head -1)
    [[ -n $port ]] && break
    kill -0 "$pid" 2>/dev/null || break
    sleep 0.5
  done
  [[ -n $port ]] || { echo "kubectl could not reach $1/$2: $(tail -1 "$log")" >&2; rm -f "$log"; return 1; }
  rm -f "$log"
  echo "$port $pid"
}
read -r port pid < <(forward data-system svc/vault-v2 8200) || die "No port-forward to Vault."
forward_pids+=("$pid"); live_addr=http://127.0.0.1:$port

# Only the restore role may overwrite Vault; everything else signs in as a plain admin.
role="admin"
[[ $mode == cluster ]] && wants database && role=restore
echo "- A browser opens on the site's sign-in; sign in with an admin account."
live_token=$(VAULT_ADDR=$live_addr vault login -method=oidc -no-store -token-only role=$role) ||
  die "The Vault sign-in did not complete."
live() { VAULT_ADDR=$live_addr VAULT_TOKEN=$live_token vault "$@"; }
oidc=$(live read -field=oidc_discovery_url auth/oidc/config 2>/dev/null || true)
[[ $oidc == "$BLUESHELL_OIDC" ]] || die "This Vault signs in through ${oidc:-an unknown provider}, not $BLUESHELL_OIDC."
step "This is Blueshell's Vault."

KOPIA_PASSWORD=$(live kv get -field=kopia.password "$VAULT_PATH") || die "Vault would not hand over $VAULT_PATH."
AWS_ACCESS_KEY_ID=$(live kv get -field=writer.access_key "$VAULT_PATH")
AWS_SECRET_ACCESS_KEY=$(live kv get -field=writer.secret_key "$VAULT_PATH")
export KOPIA_PASSWORD AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY

# --- Which snapshots ----------------------------------------------------------------------------

# A run cut short can leave its containers and volumes behind, holding that run's passwords.
dc --profile stores --profile tools down -v >/dev/null 2>&1 || true
kopia() { dc run --rm -T -e KOPIA_PASSWORD -e AWS_ACCESS_KEY_ID -e AWS_SECRET_ACCESS_KEY kopia "$@"; }
kopia repository connect s3 --bucket="$BUCKET" --endpoint=s3.nl-ams.scw.cloud --region=nl-ams \
  --readonly >/dev/null || die "Kopia could not connect to the repository."
snapshots=$(kopia snapshot list --all --json)

until_epoch=""
if [[ -n $at ]]; then
  [[ $at == *T* ]] || at="${at}T23:59:59"
  [[ $at == *Z ]] || at="${at}Z"
  [[ $at =~ T[0-9]{2}:[0-9]{2}Z$ ]] && at="${at%Z}:00Z"
  [[ $at =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$ ]] || die "--at wants 2026-10-12 or 2026-10-12T03:30."
  until_epoch=$(jq -rn --arg t "$at" '$t | fromdateiso8601')
fi
# The newest snapshot of a source, at or before --at; with $2, only one taken within an hour
# before that moment, which is how the dump and the Vault snapshot of one run are paired.
newest() { # source-path [paired-with-time]
  jq -c --arg p "$1" --arg until "$until_epoch" --arg pair "${2:-}" '
    def epoch: sub("\\.[0-9]+"; "") | fromdateiso8601;
    [.[] | select(.source.path == $p)
         | select($until == "" or (.startTime | epoch) <= ($until | tonumber))
         | select($pair == "" or ((($pair | epoch) - (.startTime | epoch)) | . >= 0 and . < 3600))]
    | max_by(.startTime) // empty' <<<"$snapshots"
}
declare -A snap=() source_of=(
  [uploads]=/uploads [valkey]=/work/valkey [stalwart]=/stalwart [gatus]=/work/gatus
)
if wants database; then
  snap[vault]=$(newest /work/vault)
  [[ -n ${snap[vault]} ]] || die "The repository holds no Vault snapshot${at:+ at or before $at}."
  snap[mariadb]=$(newest /work/mariadb "$(jq -r .startTime <<<"${snap[vault]}")")
  [[ -n ${snap[mariadb]} ]] || die "No MariaDB dump comes from the same run as the newest Vault snapshot."
fi
for store in uploads valkey stalwart gatus; do
  wants "$store" || continue
  snap[$store]=$(newest "${source_of[$store]}")
  [[ -n ${snap[$store]} ]] || die "The repository holds no $store snapshot${at:+ at or before $at}."
done
echo "Snapshots"
for part in mariadb vault uploads valkey stalwart gatus; do
  [[ -n ${snap[$part]:-} ]] && printf '  %-9s %s  %s\n' "$part" "$(jq -r '.startTime[:19]' <<<"${snap[$part]}")" "$(jq -r .id <<<"${snap[$part]}")"
done
taken() { jq -r '.startTime[:16]' <<<"${snap[$1]}"; }

download() { # part
  kopia snapshot restore "$(jq -r .id <<<"${snap[$1]}")" "/restore/$1" >/dev/null 2>&1 ||
    die "Kopia could not restore the $1 snapshot."
}

# --- Unseal shares ------------------------------------------------------------------------------

ask_shares() {
  ((${#shares[@]})) && return
  echo
  echo "Three holders of Vault's unseal shares each type their share once. It stays in this process."
  local i share
  for i in 1 2 3; do
    read -rsp "Unseal share $i of 3 (hidden): " share; echo
    shares+=("$share")
  done
  unset share
}
# The share travels in the request body on stdin, never as an argument.
unseal_with_shares() { # vault address
  local share
  for share in "${shares[@]}"; do
    printf '{"key":"%s"}' "$share" | curl -sS -X PUT --data-binary @- "$1/v1/sys/unseal" >/dev/null
  done
  unset share
  [[ $(VAULT_ADDR=$1 vault status -format=json 2>/dev/null | jq -r .sealed) == false ]]
}
# --- Rehearsals, one per store ------------------------------------------------------------------

scratch_db_up() {
  dc up -d db >/dev/null 2>&1 || die "The scratch MariaDB did not start."
  for _ in $(seq 1 60); do
    dc exec -T db mariadb -uroot -e "SELECT 1" >/dev/null 2>&1 && return
    sleep 1
  done
  die "The scratch MariaDB did not let root in within a minute."
}
live_sql() { # query; root password from Vault, on stdin
  live kv get -field=root-password secret/platform/mariadb |
    k -n data-system exec -i mariadb-0 -c mariadb -- \
    sh -c 'MYSQL_PWD=$(cat) exec mariadb -uroot -N -B blueshell -e "$1"' sh "$1"
}

# Loads the dump and proves the Vault snapshot opens with the shares. cluster --apply runs this
# first, so it never writes a snapshot production's shares cannot open.
rehearse_database() {
  download mariadb; download vault
  [[ -s $work/restore/mariadb/blueshell.sql.gz ]] || die "The MariaDB snapshot holds no blueshell.sql.gz."
  [[ -s $work/restore/vault/vault.snap ]] || die "The Vault snapshot holds no vault.snap."
  scratch_db_up
  dc exec -T db mariadb -uroot -e "CREATE DATABASE blueshell CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
  gunzip -c "$work/restore/mariadb/blueshell.sql.gz" | dc exec -T db mariadb -uroot --default-character-set=utf8mb4 blueshell ||
    die "The dump did not load."
  echo "database"
  local cut query restored live_rows
  cut=$(jq -rn --arg t "$(jq -r .startTime <<<"${snap[mariadb]}")" \
    '$t[:10] | strptime("%Y-%m-%d") | mktime - 86400 | strftime("%Y-%m-%d 00:00:00")')
  query="SELECT COUNT(*), MAX(id) FROM users WHERE created_at < '$cut'"
  restored=$(dc exec -T db mariadb -uroot -N -B blueshell -e "$query" | tr '\t' ' ')
  if [[ $mode == rehearse ]]; then
    live_rows=$(live_sql "$query" | tr '\t' ' ') || die "The live database did not answer."
    if [[ $restored == "$live_rows" ]]; then
      check ok "users created before $cut: $restored (count, highest id), as live"
    else
      check FAILED "users created before $cut: restored $restored, live $live_rows"
    fi
  else
    check ok "the dump loads: $restored users created before $cut (count, highest id)"
  fi

  dc up -d vault >/dev/null 2>&1 || die "The scratch Vault did not start."
  scratch_addr=http://$(dc port vault 8200)
  local rc init
  for _ in $(seq 1 30); do
    rc=0; VAULT_ADDR=$scratch_addr vault status >/dev/null 2>&1 || rc=$?
    [[ $rc -ne 1 ]] && break
    sleep 1
  done
  # A one-share Vault only to receive the snapshot; afterwards production's shares rule.
  init=$(VAULT_ADDR=$scratch_addr vault operator init -key-shares=1 -key-threshold=1 -format=json)
  VAULT_ADDR=$scratch_addr vault operator unseal "$(jq -r '.unseal_keys_b64[0]' <<<"$init")" >/dev/null
  VAULT_ADDR=$scratch_addr VAULT_TOKEN=$(jq -r .root_token <<<"$init") \
    vault operator raft snapshot restore -force "$work/restore/vault/vault.snap" ||
    die "The scratch Vault did not take the snapshot."
  unset init
  for _ in $(seq 1 30); do
    [[ $(VAULT_ADDR=$scratch_addr vault status -format=json 2>/dev/null | jq -r .sealed) == true ]] && break
    sleep 1
  done
  ask_shares
  if ! unseal_with_shares "$scratch_addr"; then
    check FAILED "the Vault snapshot does not unseal with these shares"
    return
  fi
  check ok "the Vault snapshot unseals with these shares"
  [[ $mode == rehearse ]] || return 0

  # Vault 2.0 refuses an unauthenticated generate-root; the restored admin policy reads both checks.
  echo "- The browser opens again; sign in to the restored Vault with the same account."
  restored_token=$(VAULT_ADDR=$scratch_addr vault login -method=oidc -no-store -token-only) || true
  [[ $restored_token == hvs.* ]] || { check FAILED "the restored Vault did not sign you in"; return; }
  scratch() { VAULT_ADDR=$scratch_addr VAULT_TOKEN=$restored_token vault "$@"; }
  local shape='{version: .data.current_version, written: .data.updated_time}' a b
  a=$(scratch kv metadata get -format=json "$VAULT_PATH" | jq -c "$shape")
  b=$(live kv metadata get -format=json "$VAULT_PATH" | jq -c "$shape")
  if [[ $a == "$b" ]]; then
    check ok "$VAULT_PATH is as live: $a"
  else
    check FAILED "$VAULT_PATH restored $a, live $b (fine only if it was rewritten since the backup)"
  fi
  local opened=0 refused=0 user sealed_address
  while IFS=$'\t' read -r user sealed_address; do
    if scratch write -field=plaintext transit/decrypt/api-address ciphertext="$sealed_address" \
        context="$(printf '%s' "address:$user" | base64)" >/dev/null 2>&1; then
      opened=$((opened + 1))
    else
      refused=$((refused + 1))
    fi
  done < <(dc exec -T db mariadb -uroot -N -B blueshell -e \
    "SELECT u.id, a.sealed_address FROM addresses a JOIN users u ON u.address_id = a.id WHERE a.sealed_address IS NOT NULL")
  if ((opened > 0 && refused == 0)); then
    check ok "every sealed address opens: $opened"
  elif ((opened + refused == 0)); then
    check FAILED "the backup holds no sealed address to open; run the Seal addresses job"
  else
    check FAILED "sealed addresses: $opened open, $refused refused"
  fi
}

# "size path" for every file under a directory, sorted; run where the files are.
LIST_FILES='cd "$1" && find . -type f -exec stat -c "%s %n" {} + | sort -k2'

# The api image has no shell, so a read-only pod lists the live uploads.
list_live_uploads() {
  k -n default run uploads-lister --image=busybox:1.38-musl --restart=Never --overrides='{
    "spec": {"containers": [{"name": "uploads-lister", "image": "busybox:1.38-musl", "command": ["sleep", "600"],
      "volumeMounts": [{"name": "store", "mountPath": "/store", "readOnly": true}]}],
      "volumes": [{"name": "store", "persistentVolumeClaim": {"claimName": "api-storage", "readOnly": true}}]}}' >/dev/null ||
    return 1
  local listed
  k -n default wait --for=condition=Ready pod/uploads-lister --timeout=180s >/dev/null || return 1
  listed=$(k -n default exec uploads-lister -- sh -c "$LIST_FILES" sh /store) || return 1
  k -n default delete pod uploads-lister --wait=false >/dev/null
  printf '%s\n' "$listed"
}

rehearse_uploads() {
  download uploads
  echo "uploads"
  local restored live_list missing=0 differ=0 size name count bytes
  restored=$(docker run --rm -v "$work/restore/uploads:/u:ro" busybox:1.38-musl sh -c "$LIST_FILES" sh /u)
  [[ -n $restored ]] || { check FAILED "the snapshot holds no files"; return; }
  live_list=$(list_live_uploads) || die "The api's uploads could not be listed."
  declare -A live_size=()
  while read -r size name; do live_size[$name]=$size; done <<<"$live_list"
  while read -r size name; do
    if [[ -z ${live_size[$name]:-} ]]; then missing=$((missing + 1))
    elif [[ ${live_size[$name]} != "$size" ]]; then differ=$((differ + 1)); fi
  done <<<"$restored"
  count=$(wc -l <<<"$restored" | tr -d ' ')
  bytes=$(awk '{s += $1} END {printf "%.1f MB", s / 1048576}' <<<"$restored")
  if ((differ == 0)); then
    check ok "$count files, $bytes; each one still live has the same size ($(wc -l <<<"$live_list" | tr -d ' ') live, $missing deleted since)"
  else
    check FAILED "$differ restored files differ in size from live"
  fi
}

rehearse_valkey() {
  download valkey
  echo "valkey"
  [[ -s $work/restore/valkey/dump.rdb ]] || { check FAILED "the snapshot holds no dump.rdb"; return; }
  dc --profile stores up -d valkey >/dev/null 2>&1 || die "The scratch Valkey did not start."
  local keys=""
  for _ in $(seq 1 30); do
    keys=$(dc --profile stores exec -T valkey valkey-cli dbsize 2>/dev/null | tr -d '\r') || true
    [[ $keys =~ ^[0-9]+$ ]] && break
    keys=""; sleep 1
  done
  [[ -n $keys ]] || { check FAILED "Valkey would not load the save"; return; }
  check ok "the save loads: $keys keys (live now: $(k -n data-system exec deploy/valkey -- valkey-cli dbsize 2>/dev/null || echo unknown))"
}

rehearse_gatus() {
  download gatus
  echo "gatus"
  [[ -s $work/restore/gatus/data.db ]] || { check FAILED "the snapshot holds no data.db"; return; }
  local report
  report=$(dc --profile tools run --rm -T sqlite python3 - 2>"$work/sqlite.err" <<'PY'
import sqlite3
db = sqlite3.connect("file:/gatus/data.db?mode=ro", uri=True)
print(db.execute("pragma integrity_check").fetchone()[0])
tables = [r[0] for r in db.execute("select name from sqlite_master where type = 'table'")]
print(" ".join(f"{t}={db.execute(f'select count(*) from \"{t}\"').fetchone()[0]}" for t in tables))
PY
  ) || { check FAILED "SQLite could not open data.db: $(tail -1 "$work/sqlite.err")"; return; }
  local integrity=${report%%$'\n'*} counts=${report#*$'\n'}
  if [[ $integrity == ok && $counts == *results=[1-9]* ]]; then
    check ok "data.db is intact, with history: $counts"
  else
    check FAILED "data.db integrity $integrity, rows: $counts"
  fi
}

# Accounts and the mail each one stores, from a Stalwart's JMAP management objects.
ACCOUNTS_JQ='[.methodResponses[0][1].list[] | {name, used: (.usedDiskQuota // 0)}] | sort_by(.name)'
accounts_call() { # jmap account id
  printf '{"using":["urn:ietf:params:jmap:core","urn:stalwart:jmap"],"methodCalls":[["x:Account/get",{"accountId":"%s"},"0"]]}' "$1"
}

rehearse_stalwart() {
  download stalwart
  echo "stalwart"
  [[ -d $work/restore/stalwart/data ]] || { check FAILED "the snapshot holds no data/ store"; return; }
  dc --profile stores up -d stalwart >/dev/null 2>&1 || die "The scratch Stalwart did not start."
  local password code="" id restored live_auth live_accounts port pid
  password=$(sed -n 's/^STALWART_PASSWORD=//p' "$work/.env")
  probe() { dc --profile tools run --rm -T probe -sS -u "rehearsal:$password" "$@"; }
  for _ in $(seq 1 60); do
    code=$(probe -o /dev/null -w '%{http_code}' http://stalwart:8080/jmap/session 2>/dev/null || true)
    [[ $code == 200 ]] && break
    sleep 2
  done
  [[ $code == 200 ]] || { check FAILED "Stalwart did not open the restored store within two minutes"; return; }
  id=$(probe http://stalwart:8080/jmap/session | jq -r '.primaryAccounts["urn:stalwart:jmap"] // empty')
  restored=$(probe -H 'Content-Type: application/json' -d "$(accounts_call "$id")" http://stalwart:8080/jmap/ | jq -c "$ACCOUNTS_JQ") ||
    { check FAILED "the restored Stalwart did not list its accounts"; return; }

  # The live admin's credentials reach curl on stdin, never as an argument.
  read -r port pid < <(forward mail-system svc/stalwart 8080) || die "No port-forward to Stalwart."
  forward_pids+=("$pid")
  live_auth=$(k -n mail-system get secret stalwart-secrets -o jsonpath='{.data.STALWART_RECOVERY_ADMIN}' | base64 -d)
  id=$(printf 'user = "%s"\n' "$live_auth" | curl -sS -K - "http://127.0.0.1:$port/jmap/session" |
    jq -r '.primaryAccounts["urn:stalwart:jmap"] // empty')
  live_accounts=$(printf 'user = "%s"\n' "$live_auth" | curl -sS -K - -H 'Content-Type: application/json' \
    -d "$(accounts_call "$id")" "http://127.0.0.1:$port/jmap/" | jq -c "$ACCOUNTS_JQ") ||
    die "The live Stalwart did not list its accounts."
  unset live_auth
  local names_restored names_live used used_live
  names_restored=$(jq -r 'map(.name) | join(" ")' <<<"$restored")
  names_live=$(jq -r 'map(.name) | join(" ")' <<<"$live_accounts")
  used=$(jq -r 'map(.used) | add // 0 | . / 1048576 | floor' <<<"$restored")
  used_live=$(jq -r 'map(.used) | add // 0 | . / 1048576 | floor' <<<"$live_accounts")
  if [[ $names_restored == "$names_live" && $(jq -r 'map(.used) | add // 0' <<<"$restored") -gt 0 ]]; then
    check ok "the store opens: $(jq length <<<"$restored") accounts as live, $used MB of mail ($used_live MB live)"
  else
    check FAILED "accounts restored: ${names_restored:-none} ($used MB); live: $names_live ($used_live MB)"
  fi
}

# --- Back into production, one store at a time --------------------------------------------------

suspend_flux() { # kustomization
  [[ " ${resume_flux[*]-} " == *" $1 "* ]] && return
  k -n flux-system patch kustomization "$1" --type=merge -p '{"spec":{"suspend":true}}' >/dev/null ||
    die "Flux would not suspend $1."
  resume_flux+=("$1")
}
scale_down() { # namespace kind name
  local replicas
  replicas=$(k -n "$1" get "$2/$3" -o jsonpath='{.spec.replicas}')
  scaled+=("$1/$2/$3/${replicas:-1}")
  k -n "$1" scale "$2/$3" --replicas=0 >/dev/null
  k -n "$1" wait --for=delete pod -l "app.kubernetes.io/name=$3" --timeout=180s >/dev/null 2>&1 || true
}
scale_up() { # namespace kind name
  local entry keep=()
  for entry in "${scaled[@]}"; do
    if [[ $entry == "$1/$2/$3/"* ]]; then
      k -n "$1" scale "$2/$3" --replicas="${entry##*/}" >/dev/null
      k -n "$1" rollout status "$2/$3" --timeout=300s >/dev/null || check FAILED "$1/$3 did not come back"
    else
      keep+=("$entry")
    fi
  done
  scaled=(${keep[@]+"${keep[@]}"})
}
# A pod that mounts a store's claim, so its files can be replaced while the app is down.
helper() { # namespace claim [uid gid]
  local security='{}'
  [[ -n ${3:-} ]] && security="{\"runAsUser\":$3,\"runAsGroup\":$4}"
  k -n "$1" run restore-helper --image=busybox:1.38-musl --restart=Never --overrides="{
    \"spec\": {\"securityContext\": $security,
      \"containers\": [{\"name\": \"restore-helper\", \"image\": \"busybox:1.38-musl\",
        \"command\": [\"sleep\", \"3600\"], \"volumeMounts\": [{\"name\": \"store\", \"mountPath\": \"/store\"}]}],
      \"volumes\": [{\"name\": \"store\", \"persistentVolumeClaim\": {\"claimName\": \"$2\"}}]}}" >/dev/null
  k -n "$1" wait --for=condition=Ready pod/restore-helper --timeout=180s >/dev/null || die "The helper pod in $1 did not start."
}
helper_sh() { k -n "$1" exec -i restore-helper -- sh -c "$2"; }
helper_done() { k -n "$1" delete pod restore-helper --wait=true >/dev/null; }
push() { # namespace local-dir: streams it into the helper's /store
  tar -C "$2" -cf - . | k -n "$1" exec -i restore-helper -- tar -xf - -C /store
}

# What each store's restore does, printed by the dry run and before --apply starts it.
plan_database() {
  echo "database ($(taken mariadb), Vault $(taken vault))"
  echo "  1. check the dump loads and the Vault snapshot unseals with three shares, in scratch"
  echo "  2. suspend Flux apps-stateless; scale default/api-primary and default/api to 0"
  echo "  3. MariaDB: drop the blueshell database and load the dump into a new one"
  echo "  4. Vault: restore the snapshot over vault-v2 as the restore role"
  echo "  5. scale the api back up; resume Flux"
}
plan_uploads() {
  echo "uploads ($(taken uploads))"
  echo "  1. copy every file into the api-storage claim as the api's user; files added since stay"
}
plan_valkey() {
  echo "valkey ($(taken valkey))"
  echo "  1. suspend Flux apps-data; scale data-system/valkey to 0"
  echo "  2. replace dump.rdb in the valkey-data claim; scale Valkey back up; resume Flux"
}
plan_stalwart() {
  echo "stalwart ($(taken stalwart))"
  echo "  1. suspend Flux apps-mail; scale mail-system/stalwart to 0 (mail is down; senders retry)"
  echo "  2. empty the stalwart-data claim and copy the store in, owned by 2000; scale Stalwart back up; resume Flux"
}
plan_gatus() {
  echo "gatus ($(taken gatus))"
  echo "  1. suspend Flux apps-utility-system; scale utility-system/gatus to 0"
  echo "  2. replace data.db in the gatus-data claim; scale Gatus back up; resume Flux"
}

# Proves each target exists before anything is changed.
preflight() {
  local missing=()
  wants database && { k -n default get deployment api-primary api >/dev/null 2>&1 || missing+=("default/api-primary"); }
  wants database && { k -n data-system get pod mariadb-0 >/dev/null 2>&1 || missing+=("data-system/mariadb-0"); }
  wants uploads && { k -n default get pvc api-storage >/dev/null 2>&1 || missing+=("default/api-storage"); }
  wants valkey && { k -n data-system get deployment/valkey pvc/valkey-data >/dev/null 2>&1 || missing+=("data-system/valkey"); }
  wants stalwart && { k -n mail-system get deployment/stalwart pvc/stalwart-data >/dev/null 2>&1 || missing+=("mail-system/stalwart"); }
  wants gatus && { k -n utility-system get deployment/gatus pvc/gatus-data >/dev/null 2>&1 || missing+=("utility-system/gatus"); }
  ((${#missing[@]} == 0)) || die "Not in the cluster: ${missing[*]}."
  if wants database; then
    live token capabilities sys/storage/raft/snapshot-force | grep -qw sudo ||
      die "This sign-in may not restore Vault: the restore OIDC role is missing or lacks the restore policy."
  fi
}

restore_database() {
  step "database: the api stops, MariaDB and Vault take the backup, the api starts again."
  suspend_flux apps-stateless
  scale_down default deployment api-primary
  scale_down default deployment api
  { live kv get -field=root-password secret/platform/mariadb; echo
    printf 'DROP DATABASE IF EXISTS blueshell;\nCREATE DATABASE blueshell CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;\nUSE blueshell;\n'
    gunzip -c "$work/restore/mariadb/blueshell.sql.gz"
  } | k -n data-system exec -i mariadb-0 -c mariadb -- \
    sh -c 'IFS= read -r p; MYSQL_PWD=$p exec mariadb -uroot --default-character-set=utf8mb4' ||
    die "MariaDB did not take the dump. The api stays down until this script exits; rerun once the cause is fixed."
  check ok "MariaDB holds the dump of $(taken mariadb)"

  live operator raft snapshot restore -force "$work/restore/vault/vault.snap" ||
    die "Vault did not take the snapshot. MariaDB is restored; Vault is not."
  # The snapshot replaced the token store, so the sign-in is gone and has nothing to revoke.
  live_token=""
  for _ in $(seq 1 60); do
    [[ $(VAULT_ADDR=$live_addr vault status -format=json 2>/dev/null | jq -r '.sealed') == false ]] && break
    sleep 2
  done
  if [[ $(VAULT_ADDR=$live_addr vault status -format=json 2>/dev/null | jq -r '.sealed') != false ]]; then
    unseal_with_shares "$live_addr" || die "The live Vault is sealed and the shares do not open it."
  fi
  check ok "Vault holds the snapshot of $(taken vault) and is unsealed"
  scale_up default deployment api
  scale_up default deployment api-primary
}

restore_uploads() {
  step "uploads: the files come back beside the live ones."
  helper default api-storage 1002 1001
  push default "$work/restore/uploads" || die "The uploads did not reach the cluster."
  helper_done default
  check ok "the uploads hold every file of $(taken uploads)"
}

restore_valkey() {
  step "valkey: Valkey stops, its save is replaced, it starts again."
  suspend_flux apps-data
  scale_down data-system deployment valkey
  helper data-system valkey-data
  helper_sh data-system 'stat -c %u:%g /store/dump.rdb 2>/dev/null > /tmp/owner || echo 999:1000 > /tmp/owner; rm -f /store/dump.rdb'
  push data-system "$work/restore/valkey" || die "The save did not reach the cluster."
  helper_sh data-system 'chown "$(cat /tmp/owner)" /store/dump.rdb'
  helper_done data-system
  scale_up data-system deployment valkey
  check ok "Valkey runs on the save of $(taken valkey)"
}

restore_stalwart() {
  step "stalwart: mail stops, its store is replaced, it starts again."
  suspend_flux apps-mail
  scale_down mail-system deployment stalwart
  helper mail-system stalwart-data
  helper_sh mail-system 'find /store -mindepth 1 -maxdepth 1 -exec rm -rf {} +'
  push mail-system "$work/restore/stalwart" || die "The mail store did not reach the cluster. Stalwart stays down until this script exits."
  helper_sh mail-system 'chown -R 2000:2000 /store'
  helper_done mail-system
  scale_up mail-system deployment stalwart
  check ok "Stalwart runs on the store of $(taken stalwart)"
}

restore_gatus() {
  step "gatus: the status page stops, its history is replaced, it starts again."
  suspend_flux apps-utility-system
  scale_down utility-system deployment gatus
  helper utility-system gatus-data
  helper_sh utility-system 'stat -c %u:%g /store/data.db 2>/dev/null > /tmp/owner || echo 0:0 > /tmp/owner; rm -f /store/data.db /store/data.db-wal /store/data.db-shm'
  push utility-system "$work/restore/gatus" || die "The history did not reach the cluster."
  helper_sh utility-system 'chown "$(cat /tmp/owner)" /store/data.db'
  helper_done utility-system
  scale_up utility-system deployment gatus
  check ok "Gatus runs on the history of $(taken gatus)"
}

# --- Run ----------------------------------------------------------------------------------------

if [[ $mode == rehearse ]]; then
  for store in "${chosen[@]}"; do "rehearse_$store"; done
  echo
  $failed && { echo "The rehearsal FAILED on the checks above."; exit 1; }
  echo "The rehearsal passed: ${chosen[*]}."
  exit 0
fi

preflight
echo
echo "Plan"
for store in "${chosen[@]}"; do "plan_$store"; done
echo
if ! $apply; then
  # The dry run proves the snapshots download and hold what the restore needs.
  holds() { # description, then a test
    local what=$1; shift
    if "$@"; then check ok "$what downloaded"; else check FAILED "$what is missing from the snapshot"; fi
  }
  for store in "${chosen[@]}"; do
    case $store in
      database) download mariadb; download vault
        holds "database: blueshell.sql.gz" test -s "$work/restore/mariadb/blueshell.sql.gz"
        holds "database: vault.snap" test -s "$work/restore/vault/vault.snap" ;;
      uploads) download uploads
        holds "uploads: $(find "$work/restore/uploads" -type f | wc -l | tr -d ' ') files" test -n "$(find "$work/restore/uploads" -type f | head -1)" ;;
      valkey) download valkey
        holds "valkey: dump.rdb" test -s "$work/restore/valkey/dump.rdb" ;;
      stalwart) download stalwart
        holds "stalwart: the data/ store" test -d "$work/restore/stalwart/data" ;;
      gatus) download gatus
        holds "gatus: data.db" test -s "$work/restore/gatus/data.db" ;;
    esac
  done
  echo
  $failed && { echo "Dry run: the backup is incomplete, so --apply would stop."; exit 1; }
  echo "Dry run: nothing in production was changed. Run again with --apply to carry out the plan above."
  exit 0
fi

echo "This replaces production's ${chosen[*]} with the snapshots above. Everything written since"
echo "they were taken is lost: accounts, sign-ups, secrets, mail and history. Uploads only come back"
echo "beside the live files. Flux pauses for the stores involved until the script ends."
night=$(jq -r '.startTime[:10]' <<<"${snap[vault]:-${snap[${chosen[0]}]}}")
read -rp "Type the backup's date, $night, to restore it over production: " answer || die "No answer given."
[[ $answer == "$night" ]] || die "Not confirmed; nothing was changed."

# The database is proven to open before production is touched at all.
if wants database; then
  echo "Checking the database backup first"
  rehearse_database
  $failed && die "The backup does not open as it should; production is unchanged."
fi
for store in uploads valkey stalwart gatus; do
  wants "$store" && download "$store"
done
echo "Restoring"
for store in "${chosen[@]}"; do "restore_$store"; done
echo
$failed && { echo "The restore finished with FAILED steps above; check them before anything else."; exit 1; }
echo "Production runs on the backup of $night: ${chosen[*]}. Run a backup by hand once it is checked."
