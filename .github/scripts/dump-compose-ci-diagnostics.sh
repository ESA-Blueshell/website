#!/usr/bin/env bash
#
# On system-test failure, dump everything useful about the running
# docker-compose stack: container state, logs per service, and the
# tail of the compose-wide journal. Output goes to stdout so the
# GitHub Actions log captures it.

set -Euo pipefail

compose_args=(-f docker-compose.yml -f docker-compose.ci.yml)

echo "==== docker compose ps ===="
docker compose "${compose_args[@]}" ps || true

echo "==== docker compose logs (tail 400 per service) ===="
docker compose "${compose_args[@]}" logs --tail=400 --no-color || true

echo "==== docker ps -a ===="
docker ps -a || true
