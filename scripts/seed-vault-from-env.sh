#!/usr/bin/env bash
# Load one or more dotenv-style files, map the known keys into the
# website's Vault paths, and optionally write them.
#
# Trusted-input script: env files are operator-controlled migration
# artefacts, not untrusted user uploads.

set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  scripts/seed-vault-from-env.sh [--apply] [--sync-api] [env-file ...]

Examples:
  scripts/seed-vault-from-env.sh \
    ../blueshell-website-old/.env \
    services/api/.db.env \
    services/api/.api.env \
    services/listmonk/.listmonk.env

  scripts/seed-vault-from-env.sh --apply services/api/.db.env services/api/.api.env

  scripts/seed-vault-from-env.sh --apply --sync-api ../legacy/.env

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
    secret/listmonk)
      LISTMONK_ARGS+=("$field=$value")
      LISTMONK_FIELDS+=("$field")
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
    secret/listmonk)
      if (( ${#LISTMONK_ARGS[@]} > 0 )); then
        args=("${LISTMONK_ARGS[@]}")
        fields=("${LISTMONK_FIELDS[@]}")
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
  local verify_field="$1"
  local expected_value="$2"
  local current_value

  export KUBECONFIG="${KUBECONFIG:-$HOME/.kube/blueshell.yaml}"

  echo "Forcing VSO refresh on vaultstaticsecret/api-secrets..."
  kubectl -n default annotate vaultstaticsecret api-secrets \
    vso.secrets.hashicorp.com/force-refresh="$(date +%s)" --overwrite >/dev/null

  echo "Waiting for api-secrets.$verify_field to update (up to 60s)..."
  local synced=0
  for i in $(seq 1 12); do
    current_value="$(
      kubectl -n default get secret api-secrets \
        -o jsonpath="{.data.$verify_field}" 2>/dev/null \
        | base64 -d 2>/dev/null || true
    )"
    if [[ "$current_value" == "$expected_value" ]]; then
      echo "  VSO synced (attempt $i)."
      synced=1
      break
    fi
    sleep 5
  done

  # Don't roll the api pod on a stale Secret — rolling forward with old
  # values is the failure mode that dragged out the last cutover by
  # hours. If VSO didn't confirm, exit loud; the operator can fix VSO
  # (usually vault-auth permissions) and re-run with --sync-api.
  if [[ "$synced" -ne 1 ]]; then
    echo "  VSO did not confirm fresh value within 60s. Not restarting the" >&2
    echo "  api pod — doing so now would roll it onto a stale Secret." >&2
    echo "  Check: kubectl -n default describe vaultstaticsecret api-secrets" >&2
    exit 1
  fi

  echo "Deleting api pod so it reads the refreshed /vault/secrets/api.env..."
  kubectl -n default delete pod -l app.kubernetes.io/name=api --wait=false >/dev/null
  echo "Watch rollout: kubectl -n default get pod -l app.kubernetes.io/name=api -w"
}

build_google_sa_json() {
  local direct pk project

  if direct="$(first_value GOOGLE_CALENDAR_SA_JSON 2>/dev/null)"; then
    printf '%s' "$direct"
    return 0
  fi

  local client_id client_email private_key_id private_key_pkcs8
  client_id="$(first_value GOOGLE_CALENDAR_CLIENT_ID CALENDAR_CLIENT_ID 2>/dev/null || true)"
  client_email="$(first_value GOOGLE_CALENDAR_CLIENT_EMAIL CALENDAR_CLIENT_EMAIL 2>/dev/null || true)"
  private_key_id="$(first_value GOOGLE_CALENDAR_PRIVATE_KEY_ID CALENDAR_PRIVATEKEY_ID 2>/dev/null || true)"
  private_key_pkcs8="$(first_value GOOGLE_CALENDAR_PRIVATE_KEY_PKCS8 CALENDAR_PRIVATEKEY_PKCS8 2>/dev/null || true)"

  if [[ -z "$client_id" || -z "$client_email" || -z "$private_key_id" || -z "$private_key_pkcs8" ]]; then
    return 1
  fi

  pk="$(printf '%b' "$private_key_pkcs8")"
  project="$(printf '%s' "$client_email" | awk -F'[@.]' '{print $2}')"
  [[ -n "$project" ]] || {
    echo "Could not derive Google project_id from $client_email" >&2
    return 1
  }

  jq -n -c \
    --arg cid "$client_id" \
    --arg cem "$client_email" \
    --arg pki "$private_key_id" \
    --arg pk "$pk" \
    --arg prj "$project" \
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
    }'
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

for cmd in jq vault awk; do
  command -v "$cmd" >/dev/null 2>&1 || { echo "missing command: $cmd" >&2; exit 1; }
done
if [[ "$SYNC_API" -eq 1 ]]; then
  for cmd in kubectl base64; do
    command -v "$cmd" >/dev/null 2>&1 || { echo "missing command: $cmd" >&2; exit 1; }
  done
fi

for file in "${FILES[@]}"; do
  [[ -f "$file" ]] || { echo "env file not found: $file" >&2; exit 1; }
  load_env_file "$file"
done

API_ARGS=()
API_FIELDS=()
MARIADB_ARGS=()
MARIADB_FIELDS=()
LISTMONK_ARGS=()
LISTMONK_FIELDS=()
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
append_field secret/api brevo-api-key "$(first_value BREVO_API_KEY BREVO_APIKEY 2>/dev/null || true)"
append_field secret/api brevo-folder-contribution-periods-id "$(first_value BREVO_FOLDER_CONTRIBUTION_PERIODS_ID 2>/dev/null || true)"
append_field secret/api mollie-api-key "$(first_value MOLLIE_API_KEY 2>/dev/null || true)"
append_field secret/api google-calendar-id "$(first_value GOOGLE_CALENDAR_ID CALENDAR_ID 2>/dev/null || true)"
append_field secret/api google-calendar-sa-json "$(build_google_sa_json || true)"
append_field secret/api facebook-page-id "$(first_value FACEBOOK_PAGE_ID 2>/dev/null || true)"
append_field secret/api facebook-access-token "$(first_value FACEBOOK_ACCESS_TOKEN 2>/dev/null || true)"
append_field secret/api x-api-key "$(first_value X_API_KEY 2>/dev/null || true)"
append_field secret/api x-api-secret "$(first_value X_API_SECRET 2>/dev/null || true)"
append_field secret/api x-access-token "$(first_value X_ACCESS_TOKEN 2>/dev/null || true)"
append_field secret/api x-access-secret "$(first_value X_ACCESS_SECRET 2>/dev/null || true)"
append_field secret/api discord-bot-token "$(first_value DISCORD_BOT_TOKEN 2>/dev/null || true)"
append_field secret/api discord-guild-id "$(first_value DISCORD_GUILD_ID 2>/dev/null || true)"
append_field secret/api vault-oidc-client-secret "$(first_value VAULT_OIDC_CLIENT_SECRET 2>/dev/null || true)"

mariadb_root_password="$(first_value MYSQL_ROOT_PASSWORD MARIADB_ROOT_PASSWORD 2>/dev/null || true)"
mariadb_user="$(first_value MYSQL_USER MARIADB_USER 2>/dev/null || true)"
mariadb_password="$(first_value MYSQL_PASSWORD MARIADB_PASSWORD 2>/dev/null || true)"
mariadb_admin_user="$(first_value MARIADB_ADMIN_USER MYSQL_ADMIN_USER 2>/dev/null || true)"
mariadb_admin_password="$(first_value MARIADB_ADMIN_PASSWORD MYSQL_ADMIN_PASSWORD 2>/dev/null || true)"

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
append_field secret/platform/mariadb legacy-user "$(first_value DATABASE_USERNAME 2>/dev/null || true)"
append_field secret/platform/mariadb legacy-password "$(first_value DATABASE_PASSWORD 2>/dev/null || true)"

append_field secret/listmonk db-admin-password "$(first_value LISTMONK_DB_ADMIN_PASSWORD 2>/dev/null || first_value LISTMONK_DB_PASSWORD 2>/dev/null || true)"
append_field secret/listmonk db-password "$(first_value LISTMONK_DB_PASSWORD 2>/dev/null || true)"
append_field secret/listmonk admin-user "$(first_value LISTMONK_ADMIN_USERNAME 2>/dev/null || true)"
append_field secret/listmonk admin-password "$(first_value LISTMONK_ADMIN_PASSWORD 2>/dev/null || true)"
append_field secret/listmonk admin-email "$(first_value LISTMONK_ADMIN_EMAIL 2>/dev/null || true)"
append_field secret/listmonk api-user "$(first_value LISTMONK_ADMIN_API_USER 2>/dev/null || true)"
append_field secret/listmonk smtp-password "$(first_value LISTMONK_SMTP_PASSWORD 2>/dev/null || true)"

append_field secret/platform/mail admin-user "$(first_value STALWART_ADMIN_USER 2>/dev/null || true)"
append_field secret/platform/mail admin-password "$(first_value STALWART_ADMIN_PASSWORD 2>/dev/null || true)"
append_field secret/platform/mail dkim-private-key "$(first_value DKIM_PRIVATE_KEY 2>/dev/null || true)"
append_field secret/platform/mail bounce-mailbox-user "$(first_value LISTMONK_BOUNCE_MAILBOX_USERNAME BOUNCE_MAILBOX_USER 2>/dev/null || true)"
append_field secret/platform/mail bounce-mailbox-password "$(first_value LISTMONK_BOUNCE_MAILBOX_PASSWORD BOUNCE_MAILBOX_PASSWORD 2>/dev/null || true)"

append_field secret/platform/edge cloudflare.dns_api_token "$(first_value CLOUDFLARE_DNS_API_TOKEN CF_DNS_API_TOKEN 2>/dev/null || true)"
append_field secret/platform/ghcr username "$(first_value GHCR_USERNAME GITHUB_PACKAGE_USERNAME 2>/dev/null || true)"
append_field secret/platform/ghcr token "$(first_value GHCR_TOKEN GITHUB_PACKAGE_TOKEN 2>/dev/null || true)"

if [[ ${#API_ARGS[@]} -eq 0 && ${#MARIADB_ARGS[@]} -eq 0 && ${#LISTMONK_ARGS[@]} -eq 0 && ${#MAIL_ARGS[@]} -eq 0 && ${#EDGE_ARGS[@]} -eq 0 && ${#GHCR_ARGS[@]} -eq 0 ]]; then
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
write_path secret/listmonk
write_path secret/platform/mail
write_path secret/platform/edge
write_path secret/platform/ghcr

if [[ "$SYNC_API" -eq 1 ]]; then
  if (( ${#API_ARGS[@]} == 0 )); then
    echo
    echo "Skipping --sync-api because no secret/api fields were written."
  else
    api_verify_field="${API_FIELDS[0]}"
    api_expected_value="${API_ARGS[0]#*=}"
    sync_api_secret "$api_verify_field" "$api_expected_value"
  fi
fi
