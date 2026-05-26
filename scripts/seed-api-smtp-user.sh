#!/usr/bin/env bash
# seed-api-smtp-user.sh — patch `secret/platform/mail` with the dedicated
# SMTP principal the api uses to submit transactional mail to Stalwart.
#
# Two keys are written, both new:
#   api-user      = api@esa-blueshell.nl
#   api-password  = generated (or $API_SMTP_PASSWORD if set)
#
# The corresponding Stalwart principal must already exist (or be created
# right after running this script) and must be authorised to send envelope
# `MAIL FROM: no-reply@esa-blueshell.nl`. See `platform/docs/vault-bootstrap.md`
# for the full sender-address list.
#
# After write:
#   • Both VaultStaticSecret CRs (`default/stalwart-secrets`,
#     `mail-system/stalwart-secrets`) get a force-refresh annotation.
#   • The script waits up to 60s for the `stalwart-secrets` Secret in
#     `default` to carry the new `api-user` field, mirroring the safety
#     check `seed-vault-from-env.sh --sync-api` uses.
#   • Optional `--restart-api` rolls the api pod so it re-reads
#     /vault/secrets/api.env with the new values.
#
# Trusted-input script: this is an operator-run seeding tool, not
# user-facing automation. Run from a developer machine with `vault` and
# `kubectl` on $PATH.

set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  scripts/seed-api-smtp-user.sh [--restart-api]

Environment:
  VAULT_ADDR                Vault address. If unset, the script port-forwards
                            to svc/vault in data-system and uses
                            http://127.0.0.1:8200.
  VAULT_TOKEN               Vault token. If unset, `vault login` is invoked
                            interactively before the write.
  KUBECONFIG                Defaults to $HOME/.kube/blueshell.yaml.
  API_SMTP_USER             Override the user value (default api@esa-blueshell.nl).
  API_SMTP_PASSWORD         Use this exact password instead of generating one.

Examples:
  # Fully interactive: prompts for token, generates a password, prints it.
  scripts/seed-api-smtp-user.sh

  # Reuse a password you already configured in Stalwart, restart api.
  API_SMTP_PASSWORD='<paste>' scripts/seed-api-smtp-user.sh --restart-api
EOF
}

RESTART_API=0
for arg in "$@"; do
  case "$arg" in
    --restart-api) RESTART_API=1 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown argument: $arg" >&2; usage >&2; exit 2 ;;
  esac
done

API_SMTP_USER="${API_SMTP_USER:-api@esa-blueshell.nl}"
KUBECONFIG="${KUBECONFIG:-$HOME/.kube/blueshell.yaml}"
export KUBECONFIG

command -v vault   >/dev/null || { echo "vault CLI not found on PATH"   >&2; exit 1; }
command -v kubectl >/dev/null || { echo "kubectl not found on PATH"     >&2; exit 1; }
command -v jq      >/dev/null || { echo "jq not found on PATH"          >&2; exit 1; }
command -v openssl >/dev/null || { echo "openssl not found on PATH"     >&2; exit 1; }

# Generate a 32-char URL-safe-ish password if the operator didn't pin one.
# Stripping characters that confuse env-file parsers and SMTP libs.
if [[ -z "${API_SMTP_PASSWORD:-}" ]]; then
  API_SMTP_PASSWORD="$(openssl rand -base64 36 | tr -d '/+=\n' | cut -c1-32)"
  GENERATED=1
else
  GENERATED=0
fi

# ── Port-forward to Vault if VAULT_ADDR isn't set ────────────────────────────
PF_PID=""
cleanup() {
  if [[ -n "$PF_PID" ]]; then
    kill "$PF_PID" 2>/dev/null || true
    wait "$PF_PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT

if [[ -z "${VAULT_ADDR:-}" ]]; then
  echo "VAULT_ADDR not set — starting port-forward to svc/vault in data-system..."
  kubectl -n data-system port-forward svc/vault 8200:8200 >/dev/null 2>&1 &
  PF_PID=$!
  # Wait briefly for the forwarder to bind.
  for _ in 1 2 3 4 5 6 7 8 9 10; do
    sleep 0.5
    if curl -fsS -o /dev/null http://127.0.0.1:8200/v1/sys/health 2>/dev/null \
        || curl -fsS -o /dev/null "http://127.0.0.1:8200/v1/sys/seal-status" 2>/dev/null; then
      break
    fi
  done
  export VAULT_ADDR=http://127.0.0.1:8200
fi
echo "VAULT_ADDR=$VAULT_ADDR"

# ── Authenticate ─────────────────────────────────────────────────────────────
if [[ -z "${VAULT_TOKEN:-}" ]]; then
  echo "VAULT_TOKEN not set — running 'vault login' (paste the root token):"
  vault login >/dev/null
fi

# Sanity: confirm we can read the mount before writing.
if ! vault kv get -format=json secret/platform/mail >/dev/null 2>&1; then
  echo "Cannot read secret/platform/mail — token lacks permission or path missing." >&2
  exit 1
fi

# ── Patch the two keys, preserving everything else ───────────────────────────
echo "Patching secret/platform/mail (api-user, api-password)..."
vault kv patch secret/platform/mail \
  api-user="$API_SMTP_USER" \
  api-password="$API_SMTP_PASSWORD" >/dev/null

# ── Force VSO refresh on both mirrored Secrets ───────────────────────────────
for ns in default mail-system; do
  echo "Forcing VSO refresh on $ns/stalwart-secrets..."
  kubectl -n "$ns" annotate vaultstaticsecret stalwart-secrets \
    vso.secrets.hashicorp.com/force-refresh="$(date +%s)" \
    --overwrite >/dev/null
done

# ── Wait for the api-user key to materialise in default/stalwart-secrets ─────
echo "Waiting for default/stalwart-secrets to carry api-user (up to 60s)..."
synced=0
for i in $(seq 1 12); do
  current="$(
    kubectl -n default get secret stalwart-secrets \
      -o jsonpath="{.data.api-user}" 2>/dev/null \
      | base64 -d 2>/dev/null || true
  )"
  if [[ "$current" == "$API_SMTP_USER" ]]; then
    echo "  VSO synced (attempt $i)."
    synced=1
    break
  fi
  sleep 5
done
if (( synced != 1 )); then
  echo "VSO did not surface api-user within 60s." >&2
  echo "Check: kubectl -n default describe vaultstaticsecret stalwart-secrets" >&2
  exit 1
fi

# ── Optional: roll the api pod so Vault Agent renders the new env ────────────
if (( RESTART_API == 1 )); then
  echo "Rolling api pod..."
  kubectl -n default rollout restart deployment/api >/dev/null
  echo "Watch: kubectl -n default rollout status deployment/api"
fi

# ── Final report ─────────────────────────────────────────────────────────────
echo
echo "─── Stalwart principal to create ────────────────────────────────────────"
echo "  login:    $API_SMTP_USER"
if (( GENERATED == 1 )); then
  echo "  password: $API_SMTP_PASSWORD     (generated; save this — Vault now holds it)"
else
  echo "  password: (already known to you; matches the value passed in)"
fi
echo
echo "The principal must be authorised to send envelope-from:"
echo "  no-reply@esa-blueshell.nl"
echo
echo "Full address policy and rationale: platform/docs/vault-bootstrap.md"
