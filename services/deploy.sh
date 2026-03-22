#!/usr/bin/env bash
# =============================================================================
# deploy.sh — deploy the website application as a Docker Swarm stack.
#
# Usage (run as the website user or via `website up` / `website pull`):
#   bash services/deploy.sh [stack-name]
#
# Environment variables (all optional, with defaults):
#   IMAGE_TAG    Docker image tag to deploy  (default: latest)
#               Use "staging" or "dev" for non-production environments.
#   STACK_NAME   Swarm stack name (auto-derived from IMAGE_TAG if unset)
#
# Tag → stack / domain mapping (BASE_DOMAIN defaults to v2.esa-blueshell.nl):
#   latest  → website          / <BASE_DOMAIN>
#   staging → website-staging  / staging.<BASE_DOMAIN>
#   dev     → website-dev      / dev.<BASE_DOMAIN>
#
# The script:
#   1. Resolves IMAGE_TAG, STACK_NAME, and APP_DOMAIN.
#   2. Loads credentials from the matching service env files.
#   3. Ensures required overlay networks exist.
#   4. Deploys (or updates) the Swarm stack from services/docker-stack.yml.
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

# ── Resolve IMAGE_TAG, STACK_NAME, BASE_DOMAIN, and APP_DOMAIN ───────────────
BASE_DOMAIN="${BASE_DOMAIN:-v2.esa-blueshell.nl}"
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
  latest)  APP_DOMAIN="${BASE_DOMAIN}" ;;
  staging) APP_DOMAIN="staging.${BASE_DOMAIN}" ;;
  dev)     APP_DOMAIN="dev.${BASE_DOMAIN}" ;;
  *)       APP_DOMAIN="${IMAGE_TAG}.${BASE_DOMAIN}" ;;
esac

STACK_FILE="${SCRIPT_DIR}/docker-stack.yml"

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

# ── Load secrets ──────────────────────────────────────────────────────────────
INFISICAL_ENV="$([ "${IMAGE_TAG}" = "latest" ] && echo "prod" || echo "${IMAGE_TAG}")"
echo "==> Loading secrets..."

# Always load local env files first (works on first boot, before Infisical)
load_env "${REPO_ROOT}/services/api/.db.env"
load_env "${REPO_ROOT}/services/api/.api.env"
load_env "${REPO_ROOT}/services/listmonk/.listmonk.env"

# Overlay from Infisical if configured and reachable
if [[ -f "${REPO_ROOT}/.server.env" ]]; then
  load_env "${REPO_ROOT}/.server.env"
  if [[ -n "${INFISICAL_TOKEN:-}" && -n "${INFISICAL_PROJECT_ID:-}" ]]; then
    INFISICAL_DOMAIN="${INFISICAL_API_URL:-http://localhost:8080}"
    if infisical export \
         --token="${INFISICAL_TOKEN}" \
         --projectId="${INFISICAL_PROJECT_ID}" \
         --domain="${INFISICAL_DOMAIN}" \
         --env="${INFISICAL_ENV}" \
         --format=dotenv-export > /tmp/.infisical-export 2>/dev/null; then
      echo "  loaded: Infisical (${INFISICAL_ENV})"
      eval "$(cat /tmp/.infisical-export)"
      rm -f /tmp/.infisical-export
    else
      echo "  WARNING: Infisical unreachable — using local env files only" >&2
    fi
  fi
fi

# Export variables consumed by the stack file's variable substitution
export BASE_DOMAIN
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
