#!/usr/bin/env bash
# =============================================================================
# deploy.sh — deploy the website application as a Docker Swarm stack.
#
# Usage (run as the website user or via `website up` / `website pull`):
#   bash deployment/deploy.sh [stack-name]
#
# Environment variables (all optional, with defaults):
#   IMAGE_TAG    Docker image tag to deploy  (default: latest)
#               Use "staging" or "dev" for non-production environments.
#   STACK_NAME   Swarm stack name (auto-derived from IMAGE_TAG if unset)
#
# Tag → stack / domain mapping:
#   latest  → website          / esa-blueshell.nl
#   staging → website-staging  / staging.esa-blueshell.nl
#   dev     → website-dev      / dev.esa-blueshell.nl
#
# The script:
#   1. Resolves IMAGE_TAG, STACK_NAME, and APP_DOMAIN.
#   2. Loads credentials from the matching service env files.
#   3. Ensures required overlay networks exist.
#   4. Deploys (or updates) the Swarm stack from deployment/docker-stack.yml.
#
# Env file sources:
#   services/api/.db.env               MariaDB credentials
#   services/api/.api.env              Application secrets
#   services/listmonk/.listmonk.env    Listmonk config
#
#   For staging/dev, the same files are used by default.
#   Place environment-specific overrides alongside the defaults if needed:
#     services/api/.staging.db.env  etc.
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# ── Resolve IMAGE_TAG, STACK_NAME, and APP_DOMAIN ────────────────────────────
IMAGE_TAG="${IMAGE_TAG:-latest}"

if [[ -n "${1:-}" ]]; then
  STACK_NAME="$1"
else
  case "${IMAGE_TAG}" in
    latest)  STACK_NAME="website" ;;
    staging) STACK_NAME="website-staging" ;;
    dev)     STACK_NAME="website-dev" ;;
    *)       STACK_NAME="website-${IMAGE_TAG}" ;;
  esac
fi

case "${IMAGE_TAG}" in
  latest)  APP_DOMAIN="esa-blueshell.nl" ;;
  staging) APP_DOMAIN="staging.esa-blueshell.nl" ;;
  dev)     APP_DOMAIN="dev.esa-blueshell.nl" ;;
  *)       APP_DOMAIN="${IMAGE_TAG}.esa-blueshell.nl" ;;
esac

STACK_FILE="${REPO_ROOT}/deployment/docker-stack.yml"

echo "==> Deploying environment: IMAGE_TAG=${IMAGE_TAG}  STACK=${STACK_NAME}  DOMAIN=${APP_DOMAIN}"

# ── Helpers ───────────────────────────────────────────────────────────────────

# Source KEY=VALUE lines from an env file into the current shell.
load_env() {
  local file="$1"
  if [[ -f "${file}" ]]; then
    set -a
    # shellcheck source=/dev/null
    source <(grep -E '^[A-Za-z_][A-Za-z0-9_]*=' "${file}" | sed 's/\r$//')
    set +a
    echo "  loaded: ${file}"
  else
    echo "  WARNING: ${file} not found — some services may fail to start" >&2
  fi
}

# Ensure an overlay network exists (idempotent).
ensure_network() {
  local name="$1"
  if ! docker network inspect "${name}" >/dev/null 2>&1; then
    echo "  creating overlay network: ${name}"
    docker network create --driver overlay --attachable "${name}"
  fi
}

# ── Fetch secrets from Infisical ─────────────────────────────────────────────
INFISICAL_ENV="$([ "${IMAGE_TAG}" = "latest" ] && echo "prod" || echo "${IMAGE_TAG}")"
echo "==> Fetching secrets from Infisical (env: ${INFISICAL_ENV})..."

# Load the minimal server bootstrap env (contains only INFISICAL_TOKEN + INFISICAL_PROJECT_ID)
load_env "${REPO_ROOT}/.server.env"

eval "$(infisical export \
  --token="${INFISICAL_TOKEN:?INFISICAL_TOKEN required — set in .server.env}" \
  --projectId="${INFISICAL_PROJECT_ID:?INFISICAL_PROJECT_ID required — set in .server.env}" \
  --env="${INFISICAL_ENV}" \
  --format=dotenv-export)"

# Export variables consumed by the stack file's variable substitution
export IMAGE_TAG
export STACK_NAME
export APP_DOMAIN

# ── Ensure shared overlay networks exist ─────────────────────────────────────
echo "==> Ensuring overlay networks..."
ensure_network traefik-public
ensure_network monitoring

# ── Deploy Swarm stack ────────────────────────────────────────────────────────
echo "==> Deploying Swarm stack '${STACK_NAME}' (IMAGE_TAG=${IMAGE_TAG}, DOMAIN=${APP_DOMAIN})..."
docker stack deploy \
  --with-registry-auth \
  --prune \
  -c "${STACK_FILE}" \
  "${STACK_NAME}"

echo ""
echo "Stack '${STACK_NAME}' deployed  (IMAGE_TAG=${IMAGE_TAG}, DOMAIN=${APP_DOMAIN})."
echo ""
echo "Useful commands:"
echo "  docker stack services ${STACK_NAME}                — service overview"
echo "  docker service logs -f ${STACK_NAME}_api           — API logs"
echo "  docker service logs -f ${STACK_NAME}_frontend      — frontend logs"
