#!/usr/bin/env bash
# Seed Vault with passwords for every account declared in
# infra/stalwart/accounts.json. The apply sidecar reads each password
# from /etc/stalwart-accounts/<vaultKey> (a projection of the
# stalwart-secrets Secret); without these keys the corresponding
# Stalwart principal stays uncreated, and the sidecar logs
# "skipping <name>: …vaultKey… missing or empty" on every boot.
#
# Writes the following keys into secret/platform/mail:
#
#   account.api        ←  copied from existing api-password
#                         (the api also reads `api-password` for SMTP)
#   account.bounce     ←  copied from existing bounce-mailbox-password
#                         (the api also reads it for IMAP bounce poll)
#   account.board      ←  fresh 32-char password
#   account.secretary  ←  fresh 32-char password
#   account.treasurer  ←  fresh 32-char password
#   account.events     ←  fresh 32-char password
#
# Idempotent: re-running keeps existing role-account passwords unless
# --rotate is given.
#
# Prereqs:
#   - vault CLI with $VAULT_ADDR + $VAULT_TOKEN set (or `vault login`)
#   - kubectl with the blueshell context active

set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  scripts/seed-stalwart-accounts.sh [--dry-run] [--no-rollout] [--rotate <role>] [--rotate-all]

Default behaviour:
  1. mirror api-password → account.api  (idempotent)
     mirror bounce-mailbox-password → account.bounce (idempotent)
  2. for board / secretary / treasurer / events:
       - if the account.<role> key already exists in Vault: leave alone
       - if missing: generate a fresh 32-char password
  3. annotate the stalwart-secrets VaultStaticSecret so VSO refreshes,
  4. roll the stalwart Deployment so the apply sidecar reconciles.

Flags:
  --dry-run         print what would be written without touching Vault.
  --no-rollout      do Vault writes only; skip VSO refresh and pod roll.
  --rotate <role>   force-rotate that role-account password (one of
                    board, secretary, treasurer, events).
  --rotate-all      force-rotate all four role accounts.

The api / bounce passwords are NOT rotated by this script — those keep
their existing `api-password` / `bounce-mailbox-password` source of
truth in Vault, and account.api / account.bounce mirror whatever's
there. Re-run this script after rotating either of those upstream.
EOF
}

DRY_RUN=0
NO_ROLLOUT=0
ROTATE_ALL=0
ROTATE_LIST=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run)    DRY_RUN=1 ;;
    --no-rollout) NO_ROLLOUT=1 ;;
    --rotate-all) ROTATE_ALL=1 ;;
    --rotate)
      shift
      [[ $# -eq 0 ]] && { echo "--rotate needs a role name" >&2; exit 2; }
      ROTATE_LIST="${ROTATE_LIST}${1} "
      ;;
    -h|--help)    usage; exit 0 ;;
    *)            echo "Unknown flag: $1" >&2; usage; exit 2 ;;
  esac
  shift
done

for cmd in vault openssl jq; do
  command -v "$cmd" >/dev/null 2>&1 || { echo "Missing required command: $cmd" >&2; exit 1; }
done
if (( DRY_RUN == 0 )); then
  command -v kubectl >/dev/null 2>&1 || { echo "Missing required command: kubectl" >&2; exit 1; }
fi

# 32 base64 chars, no padding or slashes (Stalwart's Password
# credentials are byte-safe but the operator may paste these into
# mail-client UIs that don't like `/`).
gen_password() { openssl rand -base64 24 | tr -d '/=' | cut -c1-32; }

vault_get_field() {
  local path="$1" field="$2"
  vault kv get -field="$field" "$path" 2>/dev/null || echo ""
}

vault_has_field() {
  local path="$1" field="$2"
  vault kv get -format=json "$path" 2>/dev/null \
    | jq -e --arg f "$field" '.data.data | has($f)' >/dev/null
}

# --- 1. Discover existing api-password / bounce-mailbox-password ---
EXISTING_API_PW="$(vault_get_field secret/platform/mail api-password)"
EXISTING_BOUNCE_PW="$(vault_get_field secret/platform/mail bounce-mailbox-password)"

if [[ -z "$EXISTING_API_PW" ]]; then
  echo "WARNING: api-password is not present in secret/platform/mail." >&2
  echo "  Seed it (or run seed-api-smtp-user.sh) before running this script;" >&2
  echo "  account.api will be empty and the api@ Stalwart principal won't get created." >&2
fi
if [[ -z "$EXISTING_BOUNCE_PW" ]]; then
  echo "WARNING: bounce-mailbox-password is not present in secret/platform/mail." >&2
  echo "  Seed it first; account.bounce will be empty until then." >&2
fi

# --- 2. Decide what to write for each role account ---
ROLES=(board secretary treasurer events)
ROLE_PWS=()
ROLE_MARKERS=()
for role in "${ROLES[@]}"; do
  rotate_this=0
  if (( ROTATE_ALL == 1 )); then
    rotate_this=1
  elif [[ " $ROTATE_LIST" == *" $role "* ]]; then
    rotate_this=1
  fi

  if (( rotate_this == 1 )); then
    ROLE_PWS+=("$(gen_password)")
    ROLE_MARKERS+=("(ROTATED — new password)")
    continue
  fi

  if vault_has_field secret/platform/mail "account.${role}"; then
    # Already seeded; re-write the existing value so the patch is a
    # no-op for this key — keeps the script idempotent.
    ROLE_PWS+=("$(vault_get_field secret/platform/mail "account.${role}")")
    ROLE_MARKERS+=("(existing — unchanged)")
  else
    ROLE_PWS+=("$(gen_password)")
    ROLE_MARKERS+=("(generated — first time)")
  fi
done

# --- 3. Report what we're about to do ---
echo "=== Planned writes to secret/platform/mail ==="
[[ -n "$EXISTING_API_PW"    ]] && echo "  account.api      <-  mirrored from api-password"
[[ -n "$EXISTING_BOUNCE_PW" ]] && echo "  account.bounce   <-  mirrored from bounce-mailbox-password"
for i in "${!ROLES[@]}"; do
  printf '  account.%-9s %s\n' "${ROLES[$i]}" "${ROLE_MARKERS[$i]}"
done
echo

if (( DRY_RUN == 1 )); then
  echo "--dry-run: nothing written. Re-run without --dry-run to apply."
  exit 0
fi

# --- 4. Write to Vault ---
echo "Patching secret/platform/mail..."
KV_ARGS=()
[[ -n "$EXISTING_API_PW"    ]] && KV_ARGS+=("account.api=${EXISTING_API_PW}")
[[ -n "$EXISTING_BOUNCE_PW" ]] && KV_ARGS+=("account.bounce=${EXISTING_BOUNCE_PW}")
for i in "${!ROLES[@]}"; do
  KV_ARGS+=("account.${ROLES[$i]}=${ROLE_PWS[$i]}")
done

if (( ${#KV_ARGS[@]} == 0 )); then
  echo "  nothing to write."
  exit 0
fi
vault kv patch secret/platform/mail "${KV_ARGS[@]}" >/dev/null
echo "  done."

if (( NO_ROLLOUT == 1 )); then
  echo "--no-rollout: skipping VSO refresh + pod roll."
  echo "Run \`kubectl -n mail-system rollout restart deploy/stalwart\` when ready."
  exit 0
fi

# --- 5. Force VSO refresh + roll stalwart ---
echo "Forcing VSO refresh on stalwart-secrets..."
ts="$(date +%s)"
kubectl -n mail-system annotate vaultstaticsecret/stalwart-secrets \
  vso.hashicorp.com/refresh="$ts" --overwrite >/dev/null
kubectl -n default annotate vaultstaticsecret/stalwart-secrets \
  vso.hashicorp.com/refresh="$ts" --overwrite >/dev/null
echo "  done."

echo "Rolling stalwart Deployment so the apply sidecar reconciles..."
kubectl -n mail-system rollout restart deploy/stalwart >/dev/null
kubectl -n mail-system rollout status deploy/stalwart --timeout=180s
echo
echo "Done. Watch the new pod's apply sidecar logs to confirm the accounts"
echo "were created:"
echo "  kubectl -n mail-system logs deploy/stalwart -c stalwart-apply -f"
