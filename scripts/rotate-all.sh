#!/usr/bin/env bash
# =============================================================================
# scripts/rotate-all.sh — Run all credential rotation scripts in sequence.
#
# Usage:
#   bash scripts/rotate-all.sh [stack-name]
#   stack-name: website (default, prod) | website-staging | website-dev
#
# Required environment variables:
#   INFISICAL_TOKEN       — machine identity access token
#   INFISICAL_PROJECT_ID  — Infisical project ID
#   OLD_ROOT_PASSWORD     — MariaDB root password (for rotate-db-passwords.sh)
# =============================================================================
set -euo pipefail

STACK="${1:-website}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "==> Starting full credential rotation for stack: ${STACK}"
echo ""

bash "${SCRIPT_DIR}/rotate-db-passwords.sh" "${STACK}"
echo ""

bash "${SCRIPT_DIR}/rotate-jwt-secret.sh" "${STACK}"
echo ""

bash "${SCRIPT_DIR}/rotate-listmonk-credentials.sh" "${STACK}"
echo ""

echo "==> All credentials rotated successfully."
