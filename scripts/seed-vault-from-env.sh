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
`--sync-api` restarts the api after `secret/api` changes land in Vault,
since it reads them at start.
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

env_value() {
  printf '%s' "${!1-}"
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

restart_api() {
  export KUBECONFIG="${KUBECONFIG:-$HOME/.kube/blueshell.yaml}"
  # The api reads secret/api at start, so a restart is what makes it see the write.
  # api-primary serves; restarting the Canary's target would start a release.
  kubectl -n default rollout restart deployment/api-primary >/dev/null
  echo "Restarted the api. Watch: kubectl -n default rollout status deployment/api-primary"
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

command -v vault >/dev/null 2>&1 || { echo "missing command: vault" >&2; exit 1; }
if [[ "$SYNC_API" -eq 1 ]]; then
  command -v kubectl >/dev/null 2>&1 || { echo "missing command: kubectl" >&2; exit 1; }
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

jwt_secret="$(env_value JWT_SECRET)"
if [[ "$jwt_secret" =~ ^[0-9A-Fa-f]{64}$ ]]; then
  echo "Warning: JWT_SECRET looks like a 32-byte hex string. Production expects Base64 that decodes to at least 64 bytes." >&2
fi

append_field secret/api app.jwt.secret "$jwt_secret"
append_field secret/api app.two-factor.key "$(env_value TWO_FACTOR_ENCRYPTION_KEY)"
append_field secret/api brevo.apiKey "$(env_value BREVO_API_KEY)"
append_field secret/api brevo.folders.contributionPeriodsId "$(env_value BREVO_FOLDER_CONTRIBUTION_PERIODS_ID)"
append_field secret/api google.calendar.id "$(env_value GOOGLE_CALENDAR_ID)"
append_field secret/api google.calendar.serviceAccountJson "$(env_value GOOGLE_CALENDAR_SA_JSON)"
append_field secret/api discord.botToken "$(env_value DISCORD_BOT_TOKEN)"
append_field secret/api discord.guildId "$(env_value DISCORD_GUILD_ID)"
append_field secret/api auth.clients.vault.secret "$(env_value VAULT_OIDC_CLIENT_SECRET)"

mariadb_root_password="$(env_value MYSQL_ROOT_PASSWORD)"
mariadb_user="$(env_value MYSQL_USER)"
mariadb_password="$(env_value MYSQL_PASSWORD)"

mariadb_admin_user="$(env_value MARIADB_ADMIN_USER)"
mariadb_admin_password="$(env_value MARIADB_ADMIN_PASSWORD)"

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

append_field secret/platform/mail admin-user "$(env_value STALWART_ADMIN_USER)"
append_field secret/platform/mail admin-password "$(env_value STALWART_ADMIN_PASSWORD)"
append_field secret/platform/mail bounce-mailbox-user "$(env_value EMAIL_BOUNCE_IMAP_USERNAME)"
append_field secret/platform/mail bounce-mailbox-password "$(env_value EMAIL_BOUNCE_IMAP_PASSWORD)"

append_field secret/platform/edge cloudflare.dns_api_token "$(env_value CF_DNS_API_TOKEN)"
append_field secret/platform/ghcr username "$(env_value GHCR_USERNAME)"
append_field secret/platform/ghcr token "$(env_value GHCR_TOKEN)"

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
    restart_api
  fi
fi
