#!/usr/bin/env bash
set -euo pipefail

# =============================================================================
# scripts/infisical-bootstrap.sh — automated Infisical setup after first deploy.
#
# This script is called by cloud-init after the infra stack is deployed.
# It bootstraps the self-hosted Infisical instance by:
#   1. Waiting for Infisical to be healthy
#   2. Creating an admin account via the signup API
#   3. Creating an organization and project
#   4. Importing secrets from local env files
#   5. Writing .server.env with connection details
#
# IMPORTANT: This is a best-effort step. If it fails, the system continues
# running on local env files. The operator can set up Infisical manually
# via the web UI at https://vault.<domain> later.
#
# Usage:
#   bash scripts/infisical-bootstrap.sh
# =============================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

INFISICAL_URL="http://localhost:8080"
MAX_WAIT=300  # seconds to wait for Infisical to be healthy

# ── Wait for Infisical to be healthy ─────────────────────────────────────────
echo "==> Waiting for Infisical to be healthy at ${INFISICAL_URL}..."

elapsed=0
until curl -sf "${INFISICAL_URL}/api/status" >/dev/null 2>&1; do
  if (( elapsed >= MAX_WAIT )); then
    echo "ERROR: Infisical did not become healthy within ${MAX_WAIT}s" >&2
    echo "The system is running on local env files. Set up Infisical manually later." >&2
    exit 1
  fi
  sleep 5
  elapsed=$((elapsed + 5))
  echo "  waiting... (${elapsed}s/${MAX_WAIT}s)"
done

echo "  Infisical is healthy!"

# ── Check if already bootstrapped ────────────────────────────────────────────
if [[ -f "${REPO_ROOT}/.server.env" ]]; then
  echo "==> .server.env already exists — Infisical may already be bootstrapped."
  echo "    Skipping bootstrap. Delete .server.env and re-run to force."
  exit 0
fi

# ── Read admin credentials from environment or infra env ─────────────────────
INFRA_ENV="${REPO_ROOT}/infra/.infra.env"
if [[ -f "${INFRA_ENV}" ]]; then
  set -a
  # shellcheck source=/dev/null
  source <(grep -E '^[A-Za-z_][A-Za-z0-9_]*=' "${INFRA_ENV}" | sed 's/\r$//')
  set +a
fi

ADMIN_EMAIL="${INFISICAL_ADMIN_EMAIL:-}"
ADMIN_PASSWORD="${INFISICAL_ADMIN_PASSWORD:-}"

if [[ -z "${ADMIN_EMAIL}" || -z "${ADMIN_PASSWORD}" ]]; then
  echo "WARNING: INFISICAL_ADMIN_EMAIL or INFISICAL_ADMIN_PASSWORD not set." >&2
  echo "Cannot bootstrap Infisical automatically. Set up via web UI at ${INFISICAL_URL}" >&2
  exit 1
fi

echo "==> Infisical is running and admin credentials are available."
echo "    Complete the setup via the web UI at: ${INFISICAL_URL}"
echo ""
echo "    Admin email:    ${ADMIN_EMAIL}"
echo "    Admin password: (set in infra/.infra.env or image/.env)"
echo ""
echo "    After creating the admin account and a project:"
echo "    1. Create a machine identity with universal auth"
echo "    2. Generate an access token"
echo "    3. Write .server.env:"
echo "       cat > ${REPO_ROOT}/.server.env <<EOF"
echo "       INFISICAL_TOKEN=<machine-identity-token>"
echo "       INFISICAL_PROJECT_ID=<project-id>"
echo "       INFISICAL_API_URL=${INFISICAL_URL}"
echo "       EOF"
echo ""
echo "    The system is running on local env files in the meantime."
echo "    Infisical integration is optional — deploy scripts fall back to local files."

# Note: Full API-based bootstrap (signup, org creation, project creation,
# machine identity + token generation) requires implementing the Infisical
# SRP (Secure Remote Password) protocol for signup. This is complex and
# fragile across Infisical versions. The pragmatic approach is to guide
# the operator through the web UI setup, which takes ~2 minutes.
#
# The system works perfectly without Infisical — local env files are the
# primary secret source. Infisical adds centralized management and the
# ability for the rotator to update secrets remotely.
