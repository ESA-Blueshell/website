#!/usr/bin/env bash
# shellcheck source-path=SCRIPTDIR
#
# Brings a fresh Blueshell cluster from "k3s runs" to "Flux, Vault and every
# synced Secret are in place", and re-checks a running one. Each stage checks
# first, skips what is already done, and asks before it changes anything. Safe to
# re-run at any point; after a reboot only the unseal stage has work to do.
#
#   platform/bootstrap/cluster.sh                     every stage
#   platform/bootstrap/cluster.sh --from-env FILE     every stage; secrets.sh takes missing values from FILE
#   platform/bootstrap/cluster.sh --unseal            only unseal Vault, after a node restart
#
# Stages: the cluster, Flux and its read-only deploy key, Vault init (the shares
# are shown once, one at a time, and never written to disk), unseal, the first
# bootstrap Job run with the root token, secrets.sh, the second Job run, the VSO
# sync check, revoking the root token, then mail.sh.
#
# KUBE_CONTEXT names Blueshell's kubectl context (default blueshell).
# BLUESHELL_VAULT_ADDR=http://127.0.0.1:8200 runs the Vault stages against that
# Vault and skips every cluster stage: a dry run against a local `vault server`.

here="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=lib/wizard.sh
source "$here/lib/wizard.sh"
# shellcheck source=lib/blueshell.sh
source "$here/lib/blueshell.sh"
require_bash4

FLUX_DIR="$REPO_ROOT/platform/cluster/flux/clusters/production/flux-system"
FLUX_KEY_TITLE="flux-system (read)"

usage() { sed -n '/^#   platform\/bootstrap/,/--unseal /p' "$0" | sed 's/^# \{0,1\}//'; }

UNSEAL_ONLY=""
SECRETS_ARGS=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    --unseal) UNSEAL_ONLY=yes ;;
    --from-env) shift; [[ -f "${1:-}" ]] || die "--from-env needs a file"; SECRETS_ARGS+=(--from-env "$1") ;;
    -h|--help) usage; exit 0 ;;
    *) usage; die "unknown argument: $1" ;;
  esac
  shift
done

WORK=$(mktemp -d)
INIT_JSON=""   # vault operator init's answer, held in memory for this run only
ROOT_TOKEN=""
trap blueshell_cleanup EXIT
live() { [[ -z "$LOCAL_VAULT" ]]; }

# ── stages ────────────────────────────────────────────────────────────────

stage_cluster() {
  stage "Cluster: reach it and confirm it is Blueshell's"
  if ! live; then note "BLUESHELL_VAULT_ADDR: no cluster"; return 0; fi
  require_cluster
  k get nodes -o json | jq -r '.items[] | "\(.metadata.name) \(.status.conditions[] | select(.type == "Ready") | .status)"' \
    | while read -r node ready; do
        if [[ "$ready" == True ]]; then ok "node $node is Ready"; else warn "node $node is not Ready"; fi
      done
}

# flux_key_on_github PUBLIC_KEY prints "id read_only" of the deploy key that matches.
flux_key_on_github() {
  local key
  key=$(awk '{print $1" "$2}' <<<"$1")
  gh api "repos/$REPO/keys" --paginate --jq ".[] | select(.key == \"$key\") | \"\(.id) \(.read_only)\"" 2>/dev/null | head -n 1
}

stage_flux() {
  local pub found known
  stage "Flux: controllers, the read-only deploy key and the first syncs"
  if ! live; then note "BLUESHELL_VAULT_ADDR: no Flux"; return 0; fi
  if k -n flux-system get deploy source-controller >/dev/null 2>&1; then
    ok "Flux's controllers are installed"
  else
    say "Flux is not installed. This applies $FLUX_DIR: the controllers and the sync from main."
    confirm "Install Flux?" || die "Flux is needed for every later stage"
    k apply -k "$FLUX_DIR" >/dev/null
    k -n flux-system rollout status deploy/source-controller --timeout=300s >/dev/null || die "source-controller did not start"
    ok "installed Flux"
  fi

  pub=$(secret_value flux-system flux-system identity.pub)
  found=""
  [[ -n "$pub" ]] && found=$(flux_key_on_github "$pub")
  if [[ -n "$found" ]]; then
    ok "flux-system's key is deploy key ${found%% *} on $REPO (read-only: ${found#* })"
  else
    if [[ -n "$pub" ]]; then warn "flux-system's key is not a deploy key on $REPO"; else say "Flux has no key to read $REPO with."; fi
    confirm "Generate a key, add it to $REPO as a read-only deploy key and store it as flux-system/flux-system?" \
      || die "Flux cannot read the repository without it"
    ssh-keygen -q -t ed25519 -N '' -C "flux-system" -f "$WORK/flux" </dev/null
    known=$(gh api meta --jq '.ssh_keys[] | "github.com " + .') || die "could not read GitHub's SSH host keys (gh api meta)"
    printf '%s\n' "$known" >"$WORK/known_hosts"
    gh api -X POST "repos/$REPO/keys" -f title="$FLUX_KEY_TITLE" -f key="$(cat "$WORK/flux.pub")" -F read_only=true >/dev/null \
      || die "GitHub refused the deploy key: adding one needs admin on $REPO"
    k -n flux-system delete secret flux-system --ignore-not-found >/dev/null
    k -n flux-system create secret generic flux-system --from-file=identity="$WORK/flux" \
      --from-file=identity.pub="$WORK/flux.pub" --from-file=known_hosts="$WORK/known_hosts" >/dev/null
    rm -f "$WORK/flux" "$WORK/flux.pub"
    ok "flux-system/flux-system holds a new read-only deploy key"
    note "an old Flux deploy key on $REPO can go once the sync below is Ready (GitHub, Settings, Deploy keys)"
  fi
  flux --context "$CONTEXT" -n flux-system reconcile source git flux-system --timeout=3m >/dev/null 2>&1 || true
  k -n flux-system wait gitrepository/flux-system --for=condition=Ready --timeout=180s >/dev/null \
    || die "Flux cannot read $REPO: flux --context $CONTEXT get sources git"
  ok "Flux reads $REPO"
  say "Waiting for apps-core (cert-manager, external-dns, Traefik, VSO). The first time takes a few minutes."
  k -n flux-system wait kustomization/apps-core --for=condition=Ready --timeout=20m >/dev/null \
    || warn "apps-core is not Ready yet: flux --context $CONTEXT get kustomizations"
  for _ in $(seq 1 120); do k -n "$VAULT_NS" get pod "$VAULT_POD" >/dev/null 2>&1 && break; sleep 5; done
  k -n "$VAULT_NS" get pod "$VAULT_POD" >/dev/null 2>&1 || die "apps-data has not made $VAULT_POD after ten minutes"
  ok "$VAULT_POD exists"
}

# show_once LABEL VALUE puts one value on an otherwise empty screen until you
# say it is stored, then clears it.
show_once() {
  _clear
  printf '\n  %s%s%s\n\n      %s\n\n' "$BOLD" "$1" "$RESET" "$2"
  pause "Stored in the password manager? Press Enter to clear the screen"
  _clear
}

stage_vault_init() {
  local s i
  stage "Vault: initialise"
  vault_forward
  s=$(vault_status)
  [[ -n "$s" ]] || die "Vault does not answer at $VAULT_ADDR"
  if [[ "$(jq -r .initialized <<<"$s")" == true ]]; then
    ok "Vault is initialised"
    return 0
  fi
  say "Vault is not initialised. Initialising makes five unseal shares, any three of which"
  say "open it, and a root token. Each is shown once, alone on the screen, and never written"
  say "to disk. Store every share in the password manager before you continue; split them"
  say "across owners. The root token is revoked at the end of this run."
  [[ -t 1 ]] || warn "output is not a terminal, so the shares stay in its scrollback"
  confirm "Initialise Vault now?" || die "nothing else can run before Vault is initialised"
  INIT_JSON=$(vault operator init -key-shares=5 -key-threshold=3 -format=json </dev/null) || die "vault operator init failed"
  for i in 0 1 2 3 4; do
    show_once "Unseal share $((i + 1)) of 5" "$(jq -r ".unseal_keys_b64[$i]" <<<"$INIT_JSON")"
  done
  ROOT_TOKEN=$(jq -r .root_token <<<"$INIT_JSON")
  show_once "Root token (keep it until this run has revoked it)" "$ROOT_TOKEN"
  ok "Vault is initialised"
}

stage_unseal() {
  local s i
  stage "Vault: unseal"
  vault_forward
  s=$(vault_status)
  [[ "$(jq -r .initialized <<<"$s")" == true ]] || die "Vault is not initialised: run platform/bootstrap/cluster.sh"
  if [[ "$(jq -r .sealed <<<"$s")" == false ]]; then
    ok "Vault is unsealed"
  elif [[ -n "$INIT_JSON" ]]; then
    # The API rather than the CLI, which has no stdin form: the share stays off every command line.
    for i in 0 1 2; do
      jq -r ".unseal_keys_b64[$i]" <<<"$INIT_JSON" | jq -Rc '{key: .}' \
        | curl -sf -X PUT --data @- "$VAULT_ADDR/v1/sys/unseal" >/dev/null || die "Vault refused a share it just made"
    done
    [[ "$(jq -r .sealed <<<"$(vault_status)")" == false ]] || die "Vault is still sealed after three shares"
    ok "Vault is unsealed with three of the shares it just made"
  else
    vault_unseal_prompt
  fi
  if live; then
    k -n "$VAULT_NS" wait --for=condition=Ready "pod/$VAULT_POD" --timeout=180s >/dev/null \
      || die "$VAULT_POD is unsealed but not ready: kubectl --context $CONTEXT -n $VAULT_NS logs $VAULT_POD"
    ok "$VAULT_POD is ready"
  fi
}

stage_signin() {
  stage "Vault: sign in"
  if [[ -n "$ROOT_TOKEN" ]]; then
    export VAULT_TOKEN="$ROOT_TOKEN"
    ok "using the root token this run made"
  fi
  vault_signin
}

stage_bootstrap_first() {
  stage "Vault: the bootstrap Job's first run"
  if ! live; then
    vault secrets list -format=json </dev/null | jq -e '."secret/"' >/dev/null \
      || vault secrets enable -path=secret kv-v2 >/dev/null </dev/null
    note "BLUESHELL_VAULT_ADDR: no bootstrap Job; made the secret/ kv-v2 mount it would make"
    return 0
  fi
  if vault read auth/kubernetes/role/bootstrap >/dev/null 2>&1 </dev/null; then
    ok "the bootstrap role exists, so the Job signs in on its own"
  else
    say "The Job signs in through the bootstrap role, which its own script makes. On a fresh"
    say "Vault that script runs once by hand with your token, inside the Vault pod."
    confirm "Run bootstrap-auth.sh in $VAULT_POD now?" || die "the Job cannot sign in until it has run once"
    # The token goes in on stdin, never as an argument.
    { printf '%s\n' "$VAULT_TOKEN"; cat "$REPO_ROOT/platform/cluster/flux/apps/data/vault/bootstrap-auth.sh"; } \
      | k -n "$VAULT_NS" exec -i "$VAULT_POD" -c vault -- \
        sh -c 'IFS= read -r VAULT_TOKEN; export VAULT_TOKEN VAULT_ADDR=http://127.0.0.1:8200; exec sh -s' 2>&1 \
      | tail -n 8 | sed 's/^/    /'
    vault read auth/kubernetes/role/bootstrap >/dev/null 2>&1 </dev/null || die "bootstrap-auth.sh did not make the bootstrap role"
    ok "bootstrap-auth.sh made the bootstrap role"
  fi
  rerun_bootstrap_job
}

stage_secrets() {
  stage "Vault: the secrets people put in it (secrets.sh)"
  _BLUESHELL_VAULT_INHERITED=yes "$BASH" "$here/secrets.sh" "${SECRETS_ARGS[@]}" || die "secrets.sh stopped"
}

stage_bootstrap_second() {
  local oidc
  stage "Vault: the bootstrap Job again, with the seeded values"
  if ! live; then note "BLUESHELL_VAULT_ADDR: no bootstrap Job"; return 0; fi
  rerun_bootstrap_job
  oidc=$(vault read -field=oidc_discovery_url auth/oidc/config 2>/dev/null </dev/null || true)
  if [[ "$oidc" == "$BLUESHELL_OIDC" ]]; then
    ok "Vault signs in through $BLUESHELL_OIDC"
  else
    later "Vault has no OIDC sign-in yet: it needs the api up at $BLUESHELL_OIDC; run this script again once it is"
  fi
}

stage_vso() {
  local entry ns name missing=0
  stage "VSO: the Secrets synced from Vault"
  if ! live; then note "BLUESHELL_VAULT_ADDR: no VSO"; return 0; fi
  flux --context "$CONTEXT" -n flux-system reconcile kustomization apps-vso-secrets --timeout=3m >/dev/null 2>&1 || true
  for entry in cert-manager/cloudflare-api-token external-dns/cloudflare-api-token mail-system/cloudflare-api-token \
    data-system/mariadb-credentials mail-system/stalwart-secrets default/ghcr-pull-secret mail-system/ghcr-pull-secret \
    flux-system/ghcr-pull-secret flux-system/flux-image-automation utility-system/alerting-discord; do
    ns=${entry%%/*}; name=${entry#*/}
    vss_nudge "$ns" "$name"
  done
  sleep 10
  for entry in cert-manager/cloudflare-api-token external-dns/cloudflare-api-token mail-system/cloudflare-api-token \
    data-system/mariadb-credentials mail-system/stalwart-secrets default/ghcr-pull-secret mail-system/ghcr-pull-secret \
    flux-system/ghcr-pull-secret flux-system/flux-image-automation utility-system/alerting-discord; do
    ns=${entry%%/*}; name=${entry#*/}
    if k -n "$ns" get secret "$name" >/dev/null 2>&1; then
      ok "$entry"
    else
      warn "$entry is missing: kubectl --context $CONTEXT -n $ns describe vaultstaticsecret $name"
      missing=$((missing + 1))
    fi
  done
  (( missing == 0 )) || later "$missing synced Secret(s) are missing; alerting-discord is optional"
  flux --context "$CONTEXT" get kustomizations 2>/dev/null | awk 'NR == 1 || $0 !~ /True/' | sed 's/^/    /'
}

stage_root_token() {
  local policies
  stage "Vault: revoke the root token"
  policies=$(vault token lookup -format=json </dev/null | jq -r '.data.policies | join(",")')
  if [[ ",$policies," != *",root,"* ]]; then
    ok "this run did not sign in with a root token"
    return 0
  fi
  if ! _vault_oidc_ready && live; then
    later "keep the root token: Vault has no OIDC sign-in yet, so it is the only way in. Re-run once the api is up"
    return 0
  fi
  say "Nothing needs the root token once admins sign in through the site (vault login -method=oidc)."
  if confirm "Revoke the root token now?"; then
    vault token revoke -self >/dev/null </dev/null || die "Vault did not revoke the token"
    unset VAULT_TOKEN ROOT_TOKEN
    ok "revoked the root token; delete it from the password manager"
  else
    later "revoke the root token: VAULT_TOKEN=<root> vault token revoke -self"
  fi
}

stage_mail() {
  stage "Mail: deliverability (mail.sh)"
  if ! live; then note "BLUESHELL_VAULT_ADDR: no mail checks"; return 0; fi
  if confirm "Check mail DNS, reverse DNS and the Microsoft sender programs now?"; then
    "$BASH" "$here/mail.sh" --embedded || later "mail.sh stopped: run platform/bootstrap/mail.sh"
  else
    later "check mail deliverability: platform/bootstrap/mail.sh"
  fi
}

later() { warn "$1"; SKIPPED+=("$1"); }

# ── run ───────────────────────────────────────────────────────────────────

require_tools jq curl vault
live && require_tools kubectl flux gh ssh-keygen

if [[ -n "$UNSEAL_ONLY" ]]; then
  TOTAL_STAGES=2
  stage_cluster
  stage_unseal
  printf '\n  %sVault is open. Pods that wait on it recover on their next retry.%s\n\n' "$GREEN" "$RESET"
  exit 0
fi

TOTAL_STAGES=11
banner "Blueshell: bring the cluster up, or check it"
stage_cluster
stage_flux
stage_vault_init
stage_unseal
stage_signin
stage_bootstrap_first
export VAULT_ADDR VAULT_TOKEN _BLUESHELL_CLUSTER_CONFIRMED
stage_secrets
stage_bootstrap_second
stage_vso
stage_root_token
stage_mail

printf '\n%s%s  Done%s\n' "$BOLD" "$GREEN" "$RESET"
if (( ${#SKIPPED[@]} )); then
  printf '\n'; warn "still to do:"
  for s in "${SKIPPED[@]}"; do note "  - $s"; done
fi
printf '\n'
