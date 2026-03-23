#!/usr/bin/env bash
# =============================================================================
# website — manage Docker Swarm stacks for all environments.
#
# Designed to run AS the 'website' user (member of the docker group).
#
# Admin users (admin) run website commands via:
#   su -l website -c "website up [env]"
#
# The 'backup' subcommand must be run as root (or via sudo by admin);
# it is also invoked automatically by the daily cron job.
# =============================================================================
set -euo pipefail
REPO="/src/website"

# Resolve environment name → IMAGE_TAG + STACK_NAME
_env_map() {
  case "${1:-production}" in
    prod|production)  echo "latest website" ;;
    stg|staging)      echo "staging website-staging" ;;
    dev|development)  echo "dev website-dev" ;;
    *) echo "ERROR: unknown environment '${1}' (use: production, staging, development)" >&2; exit 1 ;;
  esac
}

usage() { cat <<EOF
website — manage Docker Swarm stacks for all environments

Usage:
  website status  [env]            Show Swarm service status
  website up      [env]            Deploy (or update) the stack
  website down    [env]            Remove the stack
  website logs    [env] <service>  Tail service logs
  website pull                     git pull + redeploy production  (used by CI)
  website backup                   Run DB + storage backup (root only)
  website shell                    Open a bash shell in ${REPO}
  website services [env]           Alias for status

  website infra up                 Deploy (or update) the infra stack
  website infra down               Remove the infra stack
  website infra logs <service>     Tail infra service logs
  website infra status             Show infra Swarm service status

  website help                     Show this help

Environment (default: production):
  production  →  IMAGE_TAG=latest   STACK=website            DOMAIN=v2.esa-blueshell.nl
  staging     →  IMAGE_TAG=staging  STACK=website-staging    DOMAIN=staging.v2.esa-blueshell.nl
  development →  IMAGE_TAG=dev      STACK=website-dev        DOMAIN=dev.v2.esa-blueshell.nl

Examples (run as website user or via: su -l website -c "website up"):
  website up                     # redeploy production
  website up staging             # redeploy staging
  website logs staging api       # tail staging API logs
  website down development       # remove the dev stack
  website infra up               # deploy/update Traefik + monitoring
  website infra logs grafana     # tail Grafana logs
EOF
}

cmd="${1:-help}"
shift || true

case "${cmd}" in
  status|services)
    read -r _tag _stack <<< "$(_env_map "${1:-production}")"
    docker stack services "${_stack}" ;;

  up)
    read -r _tag _stack <<< "$(_env_map "${1:-production}")"
    cd "${REPO}"
    IMAGE_TAG="${_tag}" bash services/deploy.sh "${_stack}" ;;

  down)
    read -r _tag _stack <<< "$(_env_map "${1:-production}")"
    docker stack rm "${_stack}" ;;

  logs)
    read -r _tag _stack <<< "$(_env_map "${1:-production}")"
    shift || true
    svc="${1:-}"
    if [[ -z "${svc}" ]]; then
      echo "Usage: website logs [env] <service>" >&2
      echo "Services: api, frontend, db, listmonk, listmonk-db, listmonk-setup" >&2
      exit 1
    fi
    docker service logs -f --tail=200 "${_stack}_${svc}" ;;

  infra)
    subcmd="${1:-status}"
    shift || true
    case "${subcmd}" in
      up)
        cd "${REPO}"
        bash infra/deploy.sh ;;
      down)
        docker stack rm infra ;;
      status)
        docker stack services infra ;;
      logs)
        svc="${1:-traefik}"
        docker service logs -f --tail=200 "infra_${svc}" ;;
      *)
        echo "Usage: website infra <up|down|status|logs [service]>" >&2
        exit 1 ;;
    esac ;;

  backup)
    # Requires root — invoked by cron or: sudo website backup
    /usr/local/bin/db-backup ;;

  pull)
    # Production-only: git pull from main then redeploy
    # SSH config at ~/.ssh/config routes github.com through the deploy key
    cd "${REPO}"
    git fetch --prune origin
    git reset --hard origin/main
    IMAGE_TAG='latest' bash services/deploy.sh 'website' ;;

  shell)
    cd "${REPO}" && exec bash ;;

  help|*)
    usage ;;
esac
