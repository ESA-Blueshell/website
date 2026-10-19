#!/usr/bin/env bash
# Unseals Blueshell's Vault after a restart, through a port-forward to vault-v2-0 and the local vault
# CLI. Asks for shares until Vault opens; any three of the five do it. The vault CLI reads each share
# on a hidden prompt, so no share reaches a command line or disk.
#
#   scripts/unseal-vault.sh                          the blueshell context
#   KUBE_CONTEXT=<context> scripts/unseal-vault.sh   another name for Blueshell's cluster
set -euo pipefail

NS=data-system
POD=vault-v2-0

die() { echo "$*" >&2; exit 1; }
[[ -t 0 ]] || die "Run this in a terminal: it asks for each share on a hidden prompt."
for tool in kubectl vault jq; do
  command -v "$tool" >/dev/null || die "$tool is needed."
done

# Never kubectl's current context: every cluster may have a data-system Vault.
context=${KUBE_CONTEXT:-blueshell}
k() { kubectl --context "$context" -n "$NS" "$@"; }
kubectl config get-contexts -o name | grep -qx "$context" ||
  die "kubectl has no context \"$context\". Name Blueshell's with KUBE_CONTEXT=<name> $0"
server=$(kubectl config view -o jsonpath="{.clusters[?(@.name==\"$(kubectl config view -o jsonpath="{.contexts[?(@.name==\"$context\")].context.cluster}")\")].cluster.server}")
read -rp "kubectl context \"$context\" ($server). Is this Blueshell's cluster? Type yes: " answer ||
  die "No answer given."
[[ $answer == yes ]] || die "Not confirmed. Pick the cluster with KUBE_CONTEXT=<name> $0"

echo "Waiting for $POD to run..."
k wait --for=jsonpath='{.status.phase}'=Running "pod/$POD" --timeout=300s >/dev/null ||
  die "$POD is not running."

# The pod, not svc/vault-v2: a sealed Vault is not ready, and the port-forward must reach it anyway.
log=$(mktemp)
k port-forward "pod/$POD" :8200 >"$log" 2>&1 &
forward_pid=$!
trap 'kill "$forward_pid" 2>/dev/null || true; rm -f "$log"' EXIT
port=""
for _ in $(seq 1 30); do
  port=$(sed -n 's/^Forwarding from 127\.0\.0\.1:\([0-9]*\) .*/\1/p' "$log" | head -1)
  [[ -n $port ]] && break
  kill -0 "$forward_pid" 2>/dev/null || break
  sleep 0.5
done
[[ -n $port ]] || die "No port-forward to $POD: $(tail -1 "$log")"
export VAULT_ADDR=http://127.0.0.1:$port
unset VAULT_TOKEN VAULT_NAMESPACE

while true; do
  # vault status exits 2 while sealed, so its JSON is read regardless of the exit code.
  s=$(vault status -format=json 2>/dev/null || true)
  [[ -n $s ]] || die "Vault gives no answer on $VAULT_ADDR."
  [[ $(jq -r .initialized <<<"$s") == true ]] || die "Vault is not initialised. See platform/docs/vault-bootstrap.md."
  [[ $(jq -r .sealed <<<"$s") == false ]] && break
  echo
  echo "Vault is sealed: $(jq -r .progress <<<"$s") of $(jq -r .t <<<"$s") shares in."
  vault operator unseal || echo "Vault refused that share."
done

echo "Vault is unsealed. Waiting for $POD to become ready..."
k wait --for=condition=Ready "pod/$POD" --timeout=180s >/dev/null ||
  die "$POD is unsealed but not ready. Check: kubectl --context $context -n $NS logs $POD"
echo "Vault is ready. Pods that depend on it recover on their next restart or retry."
