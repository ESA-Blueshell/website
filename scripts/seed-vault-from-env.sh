#!/usr/bin/env bash
# Load one or more dotenv-style files, map the known keys into the
# website's Vault paths, and optionally write them.
#
# Trusted-input script: env files are operator-controlled, not untrusted
# user uploads.

set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  scripts/seed-vault-from-env.sh [--apply] [--sync-api] [env-file ...]

Examples:
  scripts/seed-vault-from-env.sh services/api/.db.env services/api/.api.env

  scripts/seed-vault-from-env.sh --apply services/api/.db.env services/api/.api.env

  scripts/seed-vault-from-env.sh --apply --sync-api services/api/.api.env

If no env files are given, the current shell environment is used.
Without --apply the script prints the Vault paths/fields it would write.
`--sync-api` forces VSO to refresh `default/api-secrets` and restarts
the api pod after `secret/api` changes land in Vault.
EOF
}

trim() {
  local value="$1"
  value="${value#"${value%%[![:space:]]*}"}"
  value="${value%"${value##*[![:space:]]}"}"
  printf '%s' "$value"
}

load_env_file() {
  local file="$1"
  local raw line key value

  while IFS= read -r raw || [[ -n "$raw" ]]; do
    raw="${raw%$'\r'}"
    line="$(trim "$raw")"
    [[ -z "$line" ]] && continue
    [[ "${line#\#}" != "$line" ]] && continue
    [[ "$line" == export\ * ]] && line="${line#export }"
    [[ "$line" != *=* ]] && continue

    key="$(trim "${line%%=*}")"
    value="${line#*=}"
    value="$(trim "$value")"

    if [[ ! "$key" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
      echo "Skipping invalid env key '$key' from $file" >&2
      continue
    fi

    if [[ "$value" == \"*\" && "$value" == *\" && ${#value} -ge 2 ]]; then
      value="${value:1:${#value}-2}"
      value="$(printf '%b' "$value")"
    elif [[ "$value" == \'*\' && "$value" == *\' && ${#value} -ge 2 ]]; then
      value="${value:1:${#value}-2}"
    fi

    printf -v "$key" '%s' "$value"
    export "$key"
  done <"$file"
}

first_value() {
  local key
  local value
  for key in "$@"; do
    value="${!key-}"
    if [[ -n "$value" ]]; then
      printf '%s' "$value"
      return 0
    fi
  done
  return 1
}

append_field() {
  local path="$1"
  local field="$2"
  local value="$3"

  [[ -n "$value" ]] || return 0

  case "$path" in
    secret/api)
      API_ARGS+=("$field=$value")
      API_FIELDS+=("$field")
      ;;
    secret/platform/mariadb)
      MARIADB_ARGS+=("$field=$value")
      MARIADB_FIELDS+=("$field")
      ;;
    secret/platform/mail)
      MAIL_ARGS+=("$field=$value")
      MAIL_FIELDS+=("$field")
      ;;
    secret/platform/edge)
      EDGE_ARGS+=("$field=$value")
      EDGE_FIELDS+=("$field")
      ;;
    secret/platform/ghcr)
      GHCR_ARGS+=("$field=$value")
      GHCR_FIELDS+=("$field")
      ;;
    *)
      echo "Unknown Vault path '$path'" >&2
      exit 1
      ;;
  esac
}

write_path() {
  local path="$1"
  local args
  local fields
  args=()
  fields=()
  case "$path" in
    secret/api)
      if (( ${#API_ARGS[@]} > 0 )); then
        args=("${API_ARGS[@]}")
        fields=("${API_FIELDS[@]}")
      fi
      ;;
    secret/platform/mariadb)
      if (( ${#MARIADB_ARGS[@]} > 0 )); then
        args=("${MARIADB_ARGS[@]}")
        fields=("${MARIADB_FIELDS[@]}")
      fi
      ;;
    secret/platform/mail)
      if (( ${#MAIL_ARGS[@]} > 0 )); then
        args=("${MAIL_ARGS[@]}")
        fields=("${MAIL_FIELDS[@]}")
      fi
      ;;
    secret/platform/edge)
      if (( ${#EDGE_ARGS[@]} > 0 )); then
        args=("${EDGE_ARGS[@]}")
        fields=("${EDGE_FIELDS[@]}")
      fi
      ;;
    secret/platform/ghcr)
      if (( ${#GHCR_ARGS[@]} > 0 )); then
        args=("${GHCR_ARGS[@]}")
        fields=("${GHCR_FIELDS[@]}")
      fi
      ;;
    *) echo "Unknown Vault path '$path'" >&2; exit 1 ;;
  esac

  [[ ${#args[@]} -gt 0 ]] || return 0

  echo "$path:"
  printf '  - %s\n' "${fields[@]}"

  if [[ "$APPLY" -eq 1 ]]; then
    if vault kv get "$path" >/dev/null 2>&1; then
      vault kv patch "$path" "${args[@]}" >/dev/null
    else
      vault kv put "$path" "${args[@]}" >/dev/null
    fi
  fi
}

sync_api_secret() {
  local args=("$@")
  local pair field expected_value current_value
  local pending_fields=()

  export KUBECONFIG="${KUBECONFIG:-$HOME/.kube/blueshell.yaml}"

  echo "Forcing VSO refresh on vaultstaticsecret/api-secrets..."
  kubectl -n default annotate vaultstaticsecret api-secrets \
    vso.secrets.hashicorp.com/force-refresh="$(date +%s)" --overwrite >/dev/null

  echo "Waiting for api-secrets to refresh all written fields (up to 60s)..."
  local synced=0
  for i in $(seq 1 12); do
    pending_fields=()
    for pair in "${args[@]}"; do
      field="${pair%%=*}"
      expected_value="${pair#*=}"
      current_value="$(
        kubectl -n default get secret api-secrets \
          -o jsonpath="{.data.$field}" 2>/dev/null \
          | base64 -d 2>/dev/null || true
      )"
      if [[ "$current_value" != "$expected_value" ]]; then
        pending_fields+=("$field")
      fi
    done
    if (( ${#pending_fields[@]} == 0 )); then
      echo "  VSO synced all fields (attempt $i)."
      synced=1
      break
    fi
    echo "  Still waiting on: ${pending_fields[*]}"
    sleep 5
  done

  # Don't roll the api pod on a stale Secret — rolling forward with old
  # values is the failure mode that dragged out the last cutover by
  # hours. If VSO didn't confirm, exit loud; the operator can fix VSO
  # (usually vault-auth permissions) and re-run with --sync-api.
  if [[ "$synced" -ne 1 ]]; then
    echo "  VSO did not confirm fresh values for: ${pending_fields[*]}" >&2
    echo "  within 60s. Not restarting the" >&2
    echo "  api pod — doing so now would roll it onto a stale Secret." >&2
    echo "  Check: kubectl -n default describe vaultstaticsecret api-secrets" >&2
    exit 1
  fi

  echo "Deleting api pod so it reads the refreshed /vault/secrets/api.env..."
  kubectl -n default delete pod -l app.kubernetes.io/name=api --wait=false >/dev/null
  echo "Watch rollout: kubectl -n default get pod -l app.kubernetes.io/name=api -w"
}


APPLY=0
SYNC_API=0
FILES=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    --apply)
      APPLY=1
      ;;
    --sync-api)
      SYNC_API=1
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      FILES+=("$1")
      ;;
  esac
  shift
done

if [[ "$SYNC_API" -eq 1 && "$APPLY" -ne 1 ]]; then
  echo "--sync-api requires --apply" >&2
  exit 1
fi

for cmd in vault; do
  command -v "$cmd" >/dev/null 2>&1 || { echo "missing command: $cmd" >&2; exit 1; }
done
if [[ "$SYNC_API" -eq 1 ]]; then
  for cmd in kubectl base64; do
    command -v "$cmd" >/dev/null 2>&1 || { echo "missing command: $cmd" >&2; exit 1; }
  done
fi

if (( ${#FILES[@]} > 0 )); then
  for file in "${FILES[@]}"; do
    [[ -f "$file" ]] || { echo "env file not found: $file" >&2; exit 1; }
    load_env_file "$file"
  done
fi

API_ARGS=()
API_FIELDS=()
MARIADB_ARGS=()
MARIADB_FIELDS=()
MAIL_ARGS=()
MAIL_FIELDS=()
EDGE_ARGS=()
EDGE_FIELDS=()
GHCR_ARGS=()
GHCR_FIELDS=()

jwt_secret="$(first_value JWT_SECRET 2>/dev/null || true)"
if [[ "$jwt_secret" =~ ^[0-9A-Fa-f]{64}$ ]]; then
  echo "Warning: JWT_SECRET looks like a 32-byte hex string. Production expects Base64 that decodes to at least 64 bytes." >&2
fi

append_field secret/api jwt-secret "$jwt_secret"
append_field secret/api two-factor-encryption-key "$(first_value TWO_FACTOR_ENCRYPTION_KEY 2>/dev/null || true)"
append_field secret/api brevo-api-key "$(first_value BREVO_API_KEY 2>/dev/null || true)"
append_field secret/api brevo-folder-contribution-periods-id "$(first_value BREVO_FOLDER_CONTRIBUTION_PERIODS_ID 2>/dev/null || true)"
append_field secret/api google-calendar-id "$(first_value GOOGLE_CALENDAR_ID 2>/dev/null || true)"
append_field secret/api google-calendar-sa-json "$(first_value GOOGLE_CALENDAR_SA_JSON 2>/dev/null || true)"
append_field secret/api discord-bot-token "$(first_value DISCORD_BOT_TOKEN 2>/dev/null || true)"
append_field secret/api discord-guild-id "$(first_value DISCORD_GUILD_ID 2>/dev/null || true)"
append_field secret/api vault-oidc-client-secret "$(first_value VAULT_OIDC_CLIENT_SECRET 2>/dev/null || true)"

mariadb_root_password="$(first_value MYSQL_ROOT_PASSWORD 2>/dev/null || true)"
mariadb_user="$(first_value MYSQL_USER 2>/dev/null || true)"
mariadb_password="$(first_value MYSQL_PASSWORD 2>/dev/null || true)"

# Mirror the app DB user + password into secret/api so the Vault Agent
# template in apps/stateless/api/deployment.yaml can render them
# alongside the rest of the api config (no separate Vault path, no
# policy expansion). Drop these once Spring Cloud Vault dynamic
# creds (`spring.cloud.vault.database.enabled=true`) are working.
append_field secret/api mysql-user     "$mariadb_user"
append_field secret/api mysql-password "$mariadb_password"
mariadb_admin_user="$(first_value MARIADB_ADMIN_USER 2>/dev/null || true)"
mariadb_admin_password="$(first_value MARIADB_ADMIN_PASSWORD 2>/dev/null || true)"

if [[ -z "$mariadb_admin_user" && -n "$mariadb_root_password" ]]; then
  mariadb_admin_user="root"
fi
if [[ -z "$mariadb_admin_password" && -n "$mariadb_root_password" ]]; then
  mariadb_admin_password="$mariadb_root_password"
fi

append_field secret/platform/mariadb root-password "$mariadb_root_password"
append_field secret/platform/mariadb user "$mariadb_user"
append_field secret/platform/mariadb password "$mariadb_password"
append_field secret/platform/mariadb admin-user "$mariadb_admin_user"
append_field secret/platform/mariadb admin-password "$mariadb_admin_password"

append_field secret/platform/mail admin-user "$(first_value STALWART_ADMIN_USER 2>/dev/null || true)"
append_field secret/platform/mail admin-password "$(first_value STALWART_ADMIN_PASSWORD 2>/dev/null || true)"
append_field secret/platform/mail bounce-mailbox-user "$(first_value EMAIL_BOUNCE_IMAP_USERNAME 2>/dev/null || true)"
append_field secret/platform/mail bounce-mailbox-password "$(first_value EMAIL_BOUNCE_IMAP_PASSWORD 2>/dev/null || true)"

append_field secret/platform/edge cloudflare.dns_api_token "$(first_value CF_DNS_API_TOKEN 2>/dev/null || true)"
append_field secret/platform/ghcr username "$(first_value GHCR_USERNAME 2>/dev/null || true)"
append_field secret/platform/ghcr token "$(first_value GHCR_TOKEN 2>/dev/null || true)"

if [[ ${#API_ARGS[@]} -eq 0 && ${#MARIADB_ARGS[@]} -eq 0 && ${#MAIL_ARGS[@]} -eq 0 && ${#EDGE_ARGS[@]} -eq 0 && ${#GHCR_ARGS[@]} -eq 0 ]]; then
  echo "No mapped secret values were found in the provided environment." >&2
  exit 1
fi

if [[ "$APPLY" -eq 1 ]]; then
  echo "Applying Vault updates..."
else
  echo "Dry run. Re-run with --apply to write the values below to Vault."
fi
echo

write_path secret/api
write_path secret/platform/mariadb
write_path secret/platform/mail
write_path secret/platform/edge
write_path secret/platform/ghcr

if [[ "$SYNC_API" -eq 1 ]]; then
  if (( ${#API_ARGS[@]} == 0 )); then
    echo
    echo "Skipping --sync-api because no secret/api fields were written."
  else
    sync_api_secret "${API_ARGS[@]}"
  fi
fi
