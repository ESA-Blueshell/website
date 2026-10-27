# shellcheck shell=bash
# shellcheck disable=SC2034 # the names below are read by the scripts that source this
# What Blueshell's bootstrap scripts share above the wizard library: the cluster
# and Vault they act on, the sign-in, and the KV reads and writes. Sourced after
# lib/wizard.sh, never run.

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
REPO="ESA-Blueshell/website"

# Never kubectl's current context: on a machine with several clusters that is
# whichever was used last, and every cluster may have a data-system Vault.
CONTEXT=${KUBE_CONTEXT:-blueshell}

DOMAIN=esa-blueshell.nl
MAIL_HOST=mail.esa-blueshell.nl
SERVER_V4=157.173.115.164
SERVER_V6=2a02:c207:2316:2642::1

VAULT_NS=data-system
VAULT_POD=vault-v2-0
BOOTSTRAP_JOB=vault-bootstrap-auth
# Only Blueshell's Vault signs in through the site, so this tells a port-forward
# to the wrong cluster apart.
BLUESHELL_OIDC=https://esa-blueshell.nl/api

# Values that are not secret (a username, a guild id, a step done by hand) are
# remembered outside the repository. The wizard library defaults ENV_FILE to
# ./.env, so set it outright. No secret is ever written here.
ENV_FILE="${BLUESHELL_ENV_FILE:-$HOME/.config/blueshell-bootstrap.env}"
mkdir -p "$(dirname "$ENV_FILE")"

# BLUESHELL_VAULT_ADDR names a Vault directly, for a dry run against a local
# `vault server -dev`: every cluster step and live check is skipped.
LOCAL_VAULT=""
[[ -n "${BLUESHELL_VAULT_ADDR:-}" ]] && LOCAL_VAULT=yes

VAULT_PF_PID=""
VAULT_PF_LOG=""
VAULT_TOKEN_MINTED=""

k() { kubectl --context "$CONTEXT" "$@"; }

ok()  { printf '  %s✓%s %s\n' "$GREEN" "$RESET" "$1"; }
die() { printf '\n  %s✗ %s%s\n\n' "$RED" "$1" "$RESET" >&2; exit 1; }

# fp VALUE prints the first 12 hex characters of the value's SHA-256: how a
# secret is shown, since the value never is.
fp() {
  if command -v sha256sum >/dev/null 2>&1; then
    printf '%s' "$1" | sha256sum | cut -c1-12
  else
    printf '%s' "$1" | shasum -a 256 | cut -c1-12
  fi
}

# gen_password prints 32 characters without `/`, `+` or `=`, which some mail
# client forms refuse.
gen_password() { openssl rand -base64 48 | tr -d '/+=\n' | cut -c1-32; }

require_tools() {
  local tool
  for tool in "$@"; do
    command -v "$tool" >/dev/null 2>&1 || die "$tool is not on PATH"
  done
}

require_bash4() {
  (( BASH_VERSINFO[0] >= 4 )) || die "bash 4 or newer is needed (macOS: brew install bash, then run it with that bash)"
}

# require_cluster stops unless the context exists, reaches a cluster, and you
# confirm it is Blueshell's. A script started by another confirms only once.
require_cluster() {
  local cluster server
  kubectl config get-contexts -o name | grep -qx "$CONTEXT" \
    || die "kubectl has no context \"$CONTEXT\"; name Blueshell's with KUBE_CONTEXT=<name>"
  cluster=$(kubectl config view -o jsonpath="{.contexts[?(@.name==\"$CONTEXT\")].context.cluster}")
  server=$(kubectl config view -o jsonpath="{.clusters[?(@.name==\"$cluster\")].cluster.server}")
  k get nodes >/dev/null 2>&1 || die "context $CONTEXT ($server) does not answer"
  ok "context $CONTEXT reaches $server"
  [[ -n "${_BLUESHELL_CLUSTER_CONFIRMED:-}" ]] && return 0
  confirm "Is $server Blueshell's cluster?" || die "pick the cluster with KUBE_CONTEXT=<name> and re-run"
  export _BLUESHELL_CLUSTER_CONFIRMED=yes
}

# blueshell_cleanup is every script's EXIT trap: it revokes a Vault token this
# run signed in for, closes the port-forward and removes $WORK.
blueshell_cleanup() {
  if [[ -n "$VAULT_TOKEN_MINTED" ]]; then
    vault token revoke -self >/dev/null 2>&1 </dev/null && printf '  revoked the Vault token this run signed in with\n'
  fi
  if [[ -n "$VAULT_PF_PID" ]]; then
    kill "$VAULT_PF_PID" 2>/dev/null || true
    wait "$VAULT_PF_PID" 2>/dev/null || true
  fi
  [[ -n "$VAULT_PF_LOG" ]] && rm -f "$VAULT_PF_LOG"
  [[ -n "${WORK:-}" ]] && rm -rf "$WORK"
  return 0
}

# vault_forward points VAULT_ADDR at the Vault pod. The pod, not the Service: a
# sealed Vault is not ready, so the Service has no endpoint for it.
vault_forward() {
  local port="" _
  if [[ -n "$LOCAL_VAULT" ]]; then
    export VAULT_ADDR="$BLUESHELL_VAULT_ADDR"
    warn "BLUESHELL_VAULT_ADDR: Vault at $VAULT_ADDR; cluster steps and live checks are skipped"
    return 0
  fi
  [[ -n "$VAULT_PF_PID" ]] && return 0
  k -n "$VAULT_NS" wait --for=jsonpath='{.status.phase}'=Running "pod/$VAULT_POD" --timeout=300s >/dev/null \
    || die "$VAULT_POD is not running"
  VAULT_PF_LOG=$(mktemp)
  # Local port 0: kubectl picks a free one and logs it, so the OIDC callback on
  # localhost:8250 never meets the forward.
  k -n "$VAULT_NS" port-forward "pod/$VAULT_POD" 0:8200 </dev/null >"$VAULT_PF_LOG" 2>&1 &
  VAULT_PF_PID=$!
  for _ in $(seq 1 30); do
    kill -0 "$VAULT_PF_PID" 2>/dev/null || die "the port-forward to $VAULT_POD exited: $(tr '\n' ' ' <"$VAULT_PF_LOG")"
    port=$(sed -n 's/^Forwarding from 127\.0\.0\.1:\([0-9]*\) .*/\1/p' "$VAULT_PF_LOG" | head -n 1)
    [[ -n "$port" ]] && break
    sleep 1
  done
  [[ -n "$port" ]] || die "the port-forward to $VAULT_POD did not start within 30s"
  export VAULT_ADDR="http://127.0.0.1:$port"
  unset VAULT_NAMESPACE
}

# vault_status prints `vault status` as JSON. It exits 2 while sealed, so the
# JSON is read whatever the exit code.
vault_status() { vault status -format=json 2>/dev/null </dev/null || true; }

vault_require_open() {
  local s
  s=$(vault_status)
  [[ -n "$s" ]] || die "Vault does not answer at $VAULT_ADDR"
  [[ "$(jq -r .initialized <<<"$s")" == true ]] || die "Vault is not initialised: run platform/bootstrap/cluster.sh"
  [[ "$(jq -r .sealed <<<"$s")" == false ]] || die "Vault is sealed: run platform/bootstrap/cluster.sh --unseal"
  ok "Vault answers at $VAULT_ADDR, initialised and unsealed"
}

# vault_unseal_prompt asks for shares on vault's own hidden prompt until Vault
# opens, so no share reaches a command line or disk.
vault_unseal_prompt() {
  local s
  [[ -t 0 ]] || die "unsealing asks for each share on a hidden prompt: run it in a terminal"
  while true; do
    s=$(vault_status)
    [[ -n "$s" ]] || die "Vault does not answer at $VAULT_ADDR"
    [[ "$(jq -r .sealed <<<"$s")" == false ]] && break
    say "Vault is sealed: $(jq -r .progress <<<"$s") of $(jq -r .t <<<"$s") shares in."
    vault operator unseal || warn "Vault refused that share"
  done
  ok "Vault is unsealed"
}

_vault_oidc_ready() {
  [[ "$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 -X POST \
    --data '{"role":"","redirect_uri":"http://localhost:8250/oidc/callback"}' \
    "$VAULT_ADDR/v1/auth/oidc/oidc/auth_url")" == 200 ]]
}

# vault_signin takes, in order: a VAULT_TOKEN in the environment that Vault
# accepts, OIDC sign-in through the site (a browser opens), a valid
# ~/.vault-token, or a token you paste: the root token on a Vault initialised
# minutes ago, which has no OIDC yet.
vault_signin() {
  local token=""
  if [[ -n "${VAULT_TOKEN:-}" ]] && vault token lookup >/dev/null 2>&1 </dev/null; then
    ok "Vault accepts the VAULT_TOKEN in the environment"
  else
    unset VAULT_TOKEN
    if [[ -z "$LOCAL_VAULT" ]] && _vault_oidc_ready; then
      say "Signing in to Vault through the site with an admin account. A browser opens;"
      say "the vault CLI catches the callback on localhost:8250."
      # -token-only implies -no-store: the token lives in this process and is revoked on exit.
      if token=$(vault login -method=oidc -token-only </dev/null) && [[ -n "$token" ]]; then
        export VAULT_TOKEN="$token"
        VAULT_TOKEN_MINTED=yes
      else
        warn "the OIDC sign-in did not complete"
      fi
    fi
    if [[ -z "${VAULT_TOKEN:-}" ]] && vault token lookup >/dev/null 2>&1 </dev/null; then
      ok "Vault accepts ~/.vault-token"
    elif [[ -z "${VAULT_TOKEN:-}" ]]; then
      step "Paste a Vault token: the root token on a Vault just initialised or restored."
      ask_secret VAULT_TOKEN "Vault token:"
      [[ -n "${VAULT_TOKEN:-}" ]] || die "no Vault token given"
      export VAULT_TOKEN
    fi
  fi
  vault token lookup >/dev/null 2>&1 </dev/null || die "Vault rejected the token"
  ok "signed in to Vault as $(vault token lookup -format=json </dev/null \
    | jq -r '"\(.data.display_name), policies \(.data.policies | join(","))"')"
  vault_check_blueshell
}

# vault_check_blueshell stops when the Vault reached signs in through anyone
# but the site. A Vault with no OIDC yet cannot be told apart, and says so.
vault_check_blueshell() {
  local oidc
  [[ -n "$LOCAL_VAULT" ]] && return 0
  oidc=$(vault read -field=oidc_discovery_url auth/oidc/config 2>/dev/null </dev/null || true)
  if [[ -z "$oidc" ]]; then
    note "this Vault has no OIDC sign-in yet, so it cannot prove it is Blueshell's"
    return 0
  fi
  [[ "$oidc" == "$BLUESHELL_OIDC" ]] || die "this Vault signs in through $oidc, not $BLUESHELL_OIDC: it is not Blueshell's"
  ok "this is Blueshell's Vault: it signs in through $BLUESHELL_OIDC"
}

# vault_connect reaches an open Vault and signs in. A script started by another
# inherits its VAULT_ADDR and VAULT_TOKEN instead.
vault_connect() {
  require_tools vault curl jq
  if [[ -n "${_BLUESHELL_VAULT_INHERITED:-}" ]]; then
    vault token lookup >/dev/null 2>&1 </dev/null || die "the inherited Vault token does not work"
    ok "using the Vault connection of the script that started this one"
    return 0
  fi
  vault_forward
  vault_require_open
  vault_signin
}

# kv_load PATH prints the document's keys as JSON, or {} when Vault holds
# nothing there. Any other failure stops: "could not read" must never pass for
# "absent", which would mint new values over live ones. Call it only as a plain
# assignment (doc=$(kv_load p)), never with `local`, so that stopping stops.
kv_load() {
  local out
  if out=$(vault kv get -mount=secret -format=json "$1" 2>&1 </dev/null); then
    jq -c '.data.data // {}' <<<"$out"
  elif grep -q 'No value found' <<<"$out"; then
    printf '{}'
  else
    die "could not read secret/$1: $(head -n 1 <<<"$out")"
  fi
}

kv_field() { jq -r --arg k "$2" '.[$k] // empty' <<<"$1"; }

# kv_write PATH writes the JSON object on stdin into PATH in one version,
# patching an existing document and creating a missing one. A create is checked
# against the version it saw, so a write that races another is refused rather
# than lost. Values arrive on stdin, never as an argument.
kv_write() {
  local path="$1" input out cas
  input=$(cat)
  [[ "$(jq -r 'length' <<<"$input")" -gt 0 ]] || die "refusing an empty write to secret/$path"
  if out=$(printf '%s' "$input" | vault kv patch -method=rw -mount=secret "$path" - 2>&1); then
    return 0
  fi
  grep -q -e 'Code: 404' -e 'No value found' <<<"$out" \
    || die "writing secret/$path failed: $(head -n 1 <<<"$out")"
  cas=$(vault kv metadata get -mount=secret -format=json "$path" 2>/dev/null </dev/null \
    | jq -r '.data.current_version // 0' || true)
  printf '%s' "$input" | vault kv put -mount=secret -cas="${cas:-0}" "$path" - >/dev/null \
    || die "creating secret/$path failed"
}

# kv_set PATH KEY writes the value on stdin as KEY of PATH.
kv_set() {
  local value
  value=$(cat)
  [[ -n "$value" ]] || die "refusing to write an empty $2 to secret/$1"
  V="$value" jq -n --arg k "$2" '{($k): env.V}' | kv_write "$1"
}

# vss_nudge NAMESPACE NAME asks VSO to sync a VaultStaticSecret now rather
# than at its next hourly refresh.
vss_nudge() {
  [[ -n "$LOCAL_VAULT" ]] && return 0
  k -n "$1" annotate vaultstaticsecret "$2" "bootstrap.esa-blueshell.nl/refreshed=$(date +%s)" --overwrite >/dev/null 2>&1 || true
}

# secret_value NAMESPACE SECRET KEY prints one key of a Kubernetes Secret, or
# nothing.
secret_value() {
  k -n "$1" get secret "$2" -o json 2>/dev/null | jq -r --arg k "$3" '.data[$k] // empty | @base64d' 2>/dev/null || true
}

# vss_wait NAMESPACE SECRET KEY VALUE waits up to two minutes for VSO's Secret
# to hold VALUE under KEY. Compared by fingerprint.
vss_wait() {
  local ns="$1" name="$2" key="$3" want _
  [[ -n "$LOCAL_VAULT" ]] && return 0
  want=$(fp "$4")
  for _ in $(seq 1 24); do
    [[ "$(fp "$(secret_value "$ns" "$name" "$key")")" == "$want" ]] && { ok "$ns/$name holds the new $key"; return 0; }
    sleep 5
  done
  return 1
}

# rerun_bootstrap_job runs the Vault bootstrap Job again. A finished Job whose
# spec has not changed stays finished on a reconcile, so it is deleted first and
# Flux makes it anew.
rerun_bootstrap_job() {
  [[ -n "$LOCAL_VAULT" ]] && { note "BLUESHELL_VAULT_ADDR: not running the bootstrap Job"; return 0; }
  require_tools flux
  k -n "$VAULT_NS" delete job "$BOOTSTRAP_JOB" --ignore-not-found >/dev/null
  flux --context "$CONTEXT" -n flux-system reconcile kustomization apps-data --timeout=5m >/dev/null \
    || die "Flux could not reconcile apps-data"
  for _ in $(seq 1 30); do k -n "$VAULT_NS" get job "$BOOTSTRAP_JOB" >/dev/null 2>&1 && break; sleep 2; done
  if k -n "$VAULT_NS" wait --for=condition=complete "job/$BOOTSTRAP_JOB" --timeout=300s >/dev/null 2>&1; then
    ok "the Vault bootstrap Job ran"
  else
    k -n "$VAULT_NS" logs "job/$BOOTSTRAP_JOB" --tail=15 2>&1 | sed 's/^/    /'
    die "the Vault bootstrap Job did not complete"
  fi
}
