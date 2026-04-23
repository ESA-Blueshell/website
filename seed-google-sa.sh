#!/usr/bin/env bash
# Local-only. Do not commit real secrets.
#
# Compatibility wrapper around scripts/seed-vault-from-env.sh for the
# legacy split Google service-account variables. Reconstructs the JSON,
# writes it to Vault at secret/api.google-calendar-sa-json, then forces
# VSO + rolls the api pod so the new value is picked up immediately.

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SEED_SCRIPT="$ROOT_DIR/scripts/seed-vault-from-env.sh"

require() {
  if [[ -z "${!1:-}" ]]; then
    echo "Missing required env var: $1" >&2
    exit 1
  fi
}

for var in \
  GOOGLE_CALENDAR_CLIENT_ID \
  GOOGLE_CALENDAR_CLIENT_EMAIL \
  GOOGLE_CALENDAR_PRIVATE_KEY_ID \
  GOOGLE_CALENDAR_PRIVATE_KEY_PKCS8 \
  VAULT_ADDR \
  VAULT_TOKEN
do
  require "$var"
done

for cmd in jq vault kubectl awk base64; do
  command -v "$cmd" >/dev/null 2>&1 || { echo "missing command: $cmd" >&2; exit 1; }
done

export KUBECONFIG="${KUBECONFIG:-$HOME/.kube/blueshell.yaml}"

PK="$(printf '%b' "$GOOGLE_CALENDAR_PRIVATE_KEY_PKCS8")"
PROJECT="$(printf '%s' "$GOOGLE_CALENDAR_CLIENT_EMAIL" | awk -F'[@.]' '{print $2}')"
if [[ -z "$PROJECT" ]]; then
  echo "could not derive project_id from GOOGLE_CALENDAR_CLIENT_EMAIL" >&2
  exit 1
fi

SA_JSON="$(jq -n -c \
  --arg cid "$GOOGLE_CALENDAR_CLIENT_ID" \
  --arg cem "$GOOGLE_CALENDAR_CLIENT_EMAIL" \
  --arg pki "$GOOGLE_CALENDAR_PRIVATE_KEY_ID" \
  --arg pk  "$PK" \
  --arg prj "$PROJECT" \
  '{
    type:                        "service_account",
    project_id:                  $prj,
    private_key_id:              $pki,
    private_key:                 $pk,
    client_email:                $cem,
    client_id:                   $cid,
    auth_uri:                    "https://accounts.google.com/o/oauth2/auth",
    token_uri:                   "https://oauth2.googleapis.com/token",
    auth_provider_x509_cert_url: "https://www.googleapis.com/oauth2/v1/certs",
    client_x509_cert_url:        ("https://www.googleapis.com/robot/v1/metadata/x509/" + ($cem|@uri)),
    universe_domain:             "googleapis.com"
  }')"

echo "Constructed SA JSON (private_key masked):"
printf '%s' "$SA_JSON" | jq '{
  type,
  project_id,
  private_key_id,
  client_email,
  client_id,
  private_key_present: (.private_key != null and (.private_key | startswith("-----BEGIN PRIVATE KEY-----")))
}'
echo

tmp_env="$(mktemp)"
trap 'rm -f "$tmp_env"' EXIT

printf 'GOOGLE_CALENDAR_SA_JSON=%q\n' "$SA_JSON" >"$tmp_env"
if [[ -n "${GOOGLE_CALENDAR_ID:-}" ]]; then
  printf 'GOOGLE_CALENDAR_ID=%q\n' "$GOOGLE_CALENDAR_ID" >>"$tmp_env"
fi

"$SEED_SCRIPT" --apply "$tmp_env"

echo "Forcing VSO refresh on vaultstaticsecret/api-secrets..."
kubectl -n default annotate vaultstaticsecret api-secrets \
  vso.secrets.hashicorp.com/force-refresh="$(date +%s)" --overwrite >/dev/null

echo "Waiting for api-secrets.google-calendar-sa-json to update (up to 60s)..."
EXPECTED_PREFIX="$(printf '%s' "$SA_JSON" | head -c 20)"
for i in $(seq 1 12); do
  CUR="$(kubectl -n default get secret api-secrets \
           -o jsonpath='{.data.google-calendar-sa-json}' 2>/dev/null \
           | base64 -d 2>/dev/null | head -c 20 || true)"
  if [[ "$CUR" == "$EXPECTED_PREFIX" ]]; then
    echo "  VSO synced (attempt $i)."
    break
  fi
  sleep 5
done

echo "Deleting api pod so it reads the new /vault/secrets/api.env..."
kubectl -n default delete pod -l app.kubernetes.io/name=api --wait=false >/dev/null

echo
echo "Done. Watch the rollout:"
echo "  kubectl -n default get pod -l app.kubernetes.io/name=api -w"
echo
echo "Expected api log line once it's Ready:"
echo "  Initialized Google Calendar client for calendarId=<id>"
