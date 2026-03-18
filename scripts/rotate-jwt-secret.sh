#!/usr/bin/env bash
# =============================================================================
# scripts/rotate-jwt-secret.sh — Rotate JWT_SECRET.
#
# WARNING: Invalidates all active sessions — users must re-login after restart.
#
# Usage:
#   bash scripts/rotate-jwt-secret.sh [stack-name]
#   stack-name: website (default, prod) | website-staging | website-dev
#
# Required environment variables:
#   INFISICAL_TOKEN       — machine identity access token
#   INFISICAL_PROJECT_ID  — Infisical project ID
# =============================================================================
set -euo pipefail

STACK="${1:-website}"
ENV="$([ "$STACK" = "website" ] && echo "prod" || echo "${STACK#website-}")"

NEW_SECRET="$(openssl rand -base64 64)"

echo "==> Rotating JWT_SECRET for stack ${STACK} (env: ${ENV})..."

infisical secrets set "JWT_SECRET=${NEW_SECRET}" \
  --token="${INFISICAL_TOKEN:?INFISICAL_TOKEN required}" \
  --projectId="${INFISICAL_PROJECT_ID:?INFISICAL_PROJECT_ID required}" \
  --env="${ENV}"

docker service update --force "${STACK}_api"
echo "==> JWT_SECRET rotated. All sessions invalidated; users must re-login."
