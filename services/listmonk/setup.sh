#!/bin/sh
# Idempotent first-time Listmonk admin setup.
# Runs before the main application starts (listmonk-setup init container).
#
# When no users exist, Listmonk renders a setup form at GET /admin/login that
# includes a `name="password2"` field. We detect this and POST the admin
# credentials. On subsequent runs (user already exists) this is a no-op.
#
# Required environment variables:
#   LISTMONK_ADMIN_USERNAME  - admin username (e.g. "listmonk")
#   LISTMONK_ADMIN_PASSWORD  - admin password (min. 8 chars)
#   LISTMONK_ADMIN_EMAIL     - admin e-mail address
set -e

LISTMONK_URL="${LISTMONK_URL:-http://listmonk:9000}"

echo "Checking Listmonk setup status..."

if curl -sf "${LISTMONK_URL}/admin/login" | grep -q 'name="password2"'; then
  echo "Performing first-time Listmonk admin setup (username: ${LISTMONK_ADMIN_USERNAME})..."

  http_status=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST "${LISTMONK_URL}/admin/login" \
    --data-urlencode "email=${LISTMONK_ADMIN_EMAIL}" \
    --data-urlencode "username=${LISTMONK_ADMIN_USERNAME}" \
    --data-urlencode "password=${LISTMONK_ADMIN_PASSWORD}" \
    --data-urlencode "password2=${LISTMONK_ADMIN_PASSWORD}")

  if [ "${http_status}" = "302" ]; then
    echo "Listmonk admin setup complete."
  else
    echo "Setup failed: HTTP ${http_status}" >&2
    exit 1
  fi
else
  echo "Listmonk already configured, skipping."
fi
