#!/usr/bin/env bash
# =============================================================================
# deploy.sh — deploy the application with Docker Compose.
#
# Usage (run as the website user or via `website up` / `website pull`):
#   bash deployment/deploy.sh [project-name]
#
# Environment variables (all optional, with defaults):
#   IMAGE_TAG     Docker image tag to deploy  (default: latest)
#                 Use "staging" or "dev" for non-production environments.
#   PROJECT_NAME  Docker Compose project name (auto-derived from IMAGE_TAG if unset)
#
# Tag → project / domain mapping:
#   latest  → website          / esa-blueshell.nl
#   staging → website-staging  / staging.esa-blueshell.nl
#   dev     → website-dev      / dev.esa-blueshell.nl
#
# The script:
#   1. Resolves IMAGE_TAG, PROJECT_NAME, and APP_DOMAIN.
#   2. Loads credentials from the matching service env files.
#   3. Ensures the Traefik infra project is running.
#   4. Exports IMAGE_TAG, APP_DOMAIN, COMPOSE_PROJECT_NAME so Compose
#      substitutes them in service files (image tags, labels, env vars).
#   5. Runs docker compose up -d (pulls images, recreates changed containers).
#
# Env file sources:
#   services/api/.db.env               MariaDB credentials
#   services/api/.api.env              Application secrets
#   services/listmonk/.listmonk.env    Listmonk config
#
#   For staging/development, the same files are used by default.
#   Place environment-specific overrides alongside the defaults if needed:
#     services/api/.staging.db.env  etc.
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# ── Resolve IMAGE_TAG, PROJECT_NAME, and APP_DOMAIN ──────────────────────────
IMAGE_TAG="${IMAGE_TAG:-latest}"

if [[ -n "${1:-}" ]]; then
  PROJECT_NAME="$1"
else
  case "${IMAGE_TAG}" in
    latest)  PROJECT_NAME="website" ;;
    staging) PROJECT_NAME="website-staging" ;;
    dev)     PROJECT_NAME="website-dev" ;;
    *)       PROJECT_NAME="website-${IMAGE_TAG}" ;;
  esac
fi

case "${IMAGE_TAG}" in
  latest)  APP_DOMAIN="esa-blueshell.nl" ;;
  staging) APP_DOMAIN="staging.esa-blueshell.nl" ;;
  dev)     APP_DOMAIN="dev.esa-blueshell.nl" ;;
  *)       APP_DOMAIN="${IMAGE_TAG}.esa-blueshell.nl" ;;
esac

COMPOSE_FILE="${REPO_ROOT}/docker-compose.yml"
INFRA_COMPOSE="${REPO_ROOT}/infra/docker-compose.yml"

echo "==> Deploying environment: IMAGE_TAG=${IMAGE_TAG}  PROJECT=${PROJECT_NAME}  DOMAIN=${APP_DOMAIN}"

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

# ── Load environment ──────────────────────────────────────────────────────────
echo "==> Loading env files..."
load_env "${REPO_ROOT}/services/api/.db.env"
load_env "${REPO_ROOT}/services/api/.api.env"
load_env "${REPO_ROOT}/services/listmonk/.listmonk.env"

# Optional per-environment overrides (e.g. .staging.db.env):
[[ "${IMAGE_TAG}" != "latest" ]] && {
  load_env "${REPO_ROOT}/services/api/.${IMAGE_TAG}.db.env"            2>/dev/null || true
  load_env "${REPO_ROOT}/services/api/.${IMAGE_TAG}.api.env"           2>/dev/null || true
  load_env "${REPO_ROOT}/services/listmonk/.${IMAGE_TAG}.listmonk.env" 2>/dev/null || true
}

# Export variables consumed by Docker Compose label/image/env interpolation
export IMAGE_TAG
export APP_DOMAIN
export COMPOSE_PROJECT_NAME="${PROJECT_NAME}"
# Rootless Docker socket path (falls back to the standard path for root Docker)
export DOCKER_SOCKET="${XDG_RUNTIME_DIR:-/run/user/$(id -u)}/docker.sock"

# ── Ensure Traefik (infra) is running ─────────────────────────────────────────
echo "==> Ensuring Traefik (infra) is up..."
docker compose \
  -f "${INFRA_COMPOSE}" \
  --project-name infra \
  up -d

# ── Deploy application project ────────────────────────────────────────────────
echo "==> Deploying project '${PROJECT_NAME}' (IMAGE_TAG=${IMAGE_TAG}, DOMAIN=${APP_DOMAIN})..."
docker compose \
  -f "${COMPOSE_FILE}" \
  --project-name "${PROJECT_NAME}" \
  up -d --pull always --remove-orphans

echo ""
echo "Project '${PROJECT_NAME}' deployed  (IMAGE_TAG=${IMAGE_TAG}, DOMAIN=${APP_DOMAIN})."
echo ""
echo "Useful commands:"
echo "  docker compose --project-name ${PROJECT_NAME} ps              — service overview"
echo "  docker compose --project-name ${PROJECT_NAME} logs -f api     — API logs"
echo "  docker compose --project-name ${PROJECT_NAME} logs -f nginx   — Nginx logs"
