#!/usr/bin/env bash
# =============================================================================
# scripts/rotate-db-passwords.sh — Rotate MYSQL_PASSWORD and LISTMONK_DB_PASSWORD.
#
# Usage:
#   bash scripts/rotate-db-passwords.sh [stack-name]
#   stack-name: website (default, prod) | website-staging | website-dev
#
# Required environment variables:
#   INFISICAL_TOKEN       — machine identity access token
#   INFISICAL_PROJECT_ID  — Infisical project ID
#   OLD_ROOT_PASSWORD     — MariaDB root password (needed to ALTER USER)
# =============================================================================
set -euo pipefail

STACK="${1:-website}"
ENV="$([ "$STACK" = "website" ] && echo "prod" || echo "${STACK#website-}")"

rotate_mysql() {
  local NEW_PW; NEW_PW="$(openssl rand -base64 32)"
  local DB_SVC="${STACK}_db"
  local API_SVC="${STACK}_api"

  echo "==> Rotating MYSQL_PASSWORD for stack ${STACK}..."

  # Update password in MariaDB
  docker exec "$(docker ps -q -f name="${DB_SVC}")" \
    mysql -uroot -p"${OLD_ROOT_PASSWORD:?OLD_ROOT_PASSWORD required}" -e \
    "ALTER USER 'blueshell'@'%' IDENTIFIED BY '${NEW_PW}'; FLUSH PRIVILEGES;"

  # Update in Infisical
  infisical secrets set "MYSQL_PASSWORD=${NEW_PW}" \
    --token="${INFISICAL_TOKEN}" \
    --projectId="${INFISICAL_PROJECT_ID}" \
    --env="${ENV}"

  # Force rolling restart of api service so it picks up the new password
  docker service update --force "${API_SVC}"
  echo "==> MYSQL_PASSWORD rotated."
}

rotate_listmonk_db() {
  local NEW_PW; NEW_PW="$(openssl rand -base64 32)"
  local DB_SVC="${STACK}_listmonk-db"
  local LISTMONK_SVC="${STACK}_listmonk"
  local API_SVC="${STACK}_api"

  echo "==> Rotating LISTMONK_DB_PASSWORD for stack ${STACK}..."

  # Update password in PostgreSQL
  docker exec "$(docker ps -q -f name="${DB_SVC}")" \
    psql -U listmonk -c "ALTER USER listmonk WITH PASSWORD '${NEW_PW}';"

  # Update in Infisical
  infisical secrets set "LISTMONK_DB_PASSWORD=${NEW_PW}" \
    --token="${INFISICAL_TOKEN}" \
    --projectId="${INFISICAL_PROJECT_ID}" \
    --env="${ENV}"

  # Restart both listmonk (DB connection) and api (token depends on listmonk being up)
  docker service update --force "${LISTMONK_SVC}"
  docker service update --force "${API_SVC}"
  echo "==> LISTMONK_DB_PASSWORD rotated."
}

rotate_mysql
rotate_listmonk_db
