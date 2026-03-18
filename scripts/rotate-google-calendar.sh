#!/usr/bin/env bash
# =============================================================================
# scripts/rotate-google-calendar.sh — Rotate Google Calendar service account key.
#
# Run this after downloading a new service account JSON key from Google Cloud
# Console (IAM & Admin → Service Accounts → Keys → Add Key → JSON).
#
# The script stores the new key in Infisical and restarts the API service.
# After verifying the new key works, delete the old key from Google Cloud Console.
#
# Usage:
#   GOOGLE_CALENDAR_SA_JSON_FILE=/path/to/new-sa-key.json \
#   bash scripts/rotate-google-calendar.sh [stack-name]
#
#   stack-name: website (default, prod) | website-staging | website-dev
#
# Required environment variables:
#   INFISICAL_TOKEN             — machine identity access token
#   INFISICAL_PROJECT_ID        — Infisical project ID
#   GOOGLE_CALENDAR_SA_JSON_FILE — path to the new service account JSON key file
# =============================================================================
set -euo pipefail

STACK="${1:-website}"
ENV="$([ "$STACK" = "website" ] && echo "prod" || echo "${STACK#website-}")"

SA_JSON_FILE="${GOOGLE_CALENDAR_SA_JSON_FILE:?GOOGLE_CALENDAR_SA_JSON_FILE required — path to the new service account JSON key file}"

if [ ! -f "${SA_JSON_FILE}" ]; then
  echo "Error: file not found: ${SA_JSON_FILE}" >&2
  exit 1
fi

NEW_SA_JSON="$(cat "${SA_JSON_FILE}")"
SA_EMAIL="$(echo "${NEW_SA_JSON}" | grep -o '"client_email": *"[^"]*"' | cut -d'"' -f4)"

echo "==> Rotating GOOGLE_CALENDAR_SA_JSON for stack ${STACK} (env: ${ENV})..."
echo "    Service account: ${SA_EMAIL}"

infisical secrets set "GOOGLE_CALENDAR_SA_JSON=${NEW_SA_JSON}" \
  --token="${INFISICAL_TOKEN:?INFISICAL_TOKEN required}" \
  --projectId="${INFISICAL_PROJECT_ID:?INFISICAL_PROJECT_ID required}" \
  --env="${ENV}"

docker service update --force "${STACK}_api"

echo ""
echo "==> GOOGLE_CALENDAR_SA_JSON rotated and API restarted."
echo ""
echo "    Next steps:"
echo "    1. Verify the API is working correctly (check calendar sync)"
echo "    2. Delete the OLD key from Google Cloud Console:"
echo "       IAM & Admin → Service Accounts → ${SA_EMAIL} → Keys"
