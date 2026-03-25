#!/usr/bin/env bash
# =============================================================================
# setup-swarm.sh — initialize Docker Swarm and create overlay networks.
#
# Run by cloud-init runcmd on first boot.
# =============================================================================
set -euxo pipefail

docker swarm init --advertise-addr 127.0.0.1
docker network create --driver overlay --attachable traefik-public || true
docker network create --driver overlay --attachable monitoring || true
