#!/usr/bin/env bash
# =============================================================================
# infra/deploy.sh — deploy the shared infrastructure stack (Traefik +
# monitoring).  Run once per server; run again to update.
#
# Usage (run as the website user):
#   bash infra/deploy.sh
#
# Or via the website CLI:
#   website infra up
#
# Required environment variables (set in /src/website/infra/.infra.env):
#   GRAFANA_ADMIN_PASSWORD       Grafana admin account password
#   GRAFANA_DISCORD_WEBHOOK_URL  Discord webhook URL for alert notifications
#
# Optional:
#   INFRA_DOMAIN          Base domain for infra services (default: v2.esa-blueshell.nl)
#   ACME_EMAIL            Email for Let's Encrypt ACME (default: board@blueshell.utwente.nl)
#   GRAFANA_SMTP_HOST     SMTP host:port  (e.g. smtp.example.com:587)
#   GRAFANA_SMTP_USER     SMTP username
#   GRAFANA_SMTP_PASSWORD SMTP password
#   GRAFANA_SMTP_FROM     From address for alert emails
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

STACK_FILE="${SCRIPT_DIR}/docker-stack.yml"
INFRA_ENV="${SCRIPT_DIR}/.infra.env"

# ── Helpers ───────────────────────────────────────────────────────────────────

load_env() {
  local file="$1"
  if [[ -f "${file}" ]]; then
    set -a
    # shellcheck source=/dev/null
    source <(grep -E '^[A-Za-z_][A-Za-z0-9_]*=' "${file}" | sed 's/\r$//')
    set +a
    echo "  loaded: ${file}"
  else
    echo "  WARNING: ${file} not found" >&2
  fi
}

ensure_network() {
  local name="$1"
  if ! docker network inspect "${name}" >/dev/null 2>&1; then
    echo "  creating overlay network: ${name}"
    docker network create --driver overlay --attachable "${name}"
  fi
}

# ── Load secrets ──────────────────────────────────────────────────────────────
echo "==> Loading secrets..."

# Always load local env file first (works on first boot, before Infisical)
load_env "${INFRA_ENV}"

# Overlay from Infisical if configured and reachable
if [[ -f "${REPO_ROOT}/.server.env" ]]; then
  load_env "${REPO_ROOT}/.server.env"
  if [[ -n "${INFISICAL_TOKEN:-}" && -n "${INFISICAL_PROJECT_ID:-}" ]]; then
    INFISICAL_DOMAIN="${INFISICAL_API_URL:-http://localhost:8080}"
    if infisical export \
         --token="${INFISICAL_TOKEN}" \
         --projectId="${INFISICAL_PROJECT_ID}" \
         --domain="${INFISICAL_DOMAIN}" \
         --env="prod" \
         --format=dotenv-export > /tmp/.infisical-export 2>/dev/null; then
      echo "  loaded: Infisical (prod)"
      eval "$(cat /tmp/.infisical-export)"
      rm -f /tmp/.infisical-export
    else
      echo "  WARNING: Infisical unreachable — using local env files only" >&2
    fi
  fi
fi

# Rootless Docker socket
export DOCKER_SOCKET="${XDG_RUNTIME_DIR:-/run/user/$(id -u)}/docker.sock"

# Validate required vars
: "${GRAFANA_ADMIN_PASSWORD:?GRAFANA_ADMIN_PASSWORD required — set in infra/.infra.env}"

export INFRA_DOMAIN="${INFRA_DOMAIN:-v2.esa-blueshell.nl}"
export ACME_EMAIL="${ACME_EMAIL:-board@blueshell.utwente.nl}"
export GRAFANA_ADMIN_PASSWORD
export GRAFANA_DISCORD_WEBHOOK_URL="${GRAFANA_DISCORD_WEBHOOK_URL:-}"
export GRAFANA_SMTP_ENABLED="${GRAFANA_SMTP_ENABLED:-false}"
export GRAFANA_SMTP_HOST="${GRAFANA_SMTP_HOST:-}"
export GRAFANA_SMTP_USER="${GRAFANA_SMTP_USER:-}"
export GRAFANA_SMTP_PASSWORD="${GRAFANA_SMTP_PASSWORD:-}"
export GRAFANA_SMTP_FROM="${GRAFANA_SMTP_FROM:-}"

# ── Ensure overlay networks ───────────────────────────────────────────────────
echo "==> Ensuring overlay networks..."
ensure_network traefik-public
ensure_network monitoring

# ── Deploy infra stack ────────────────────────────────────────────────────────
echo "==> Deploying infra Swarm stack..."
docker stack deploy \
  --with-registry-auth \
  --prune \
  -c "${STACK_FILE}" \
  infra

echo ""
echo "Infra stack deployed."
echo ""
echo "Useful commands:"
echo "  docker stack services infra                  — service overview"
echo "  docker service logs -f infra_traefik         — Traefik logs"
echo "  docker service logs -f infra_grafana         — Grafana logs"
echo "  docker service logs -f infra_prometheus      — Prometheus logs"
