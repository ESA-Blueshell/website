#!/usr/bin/env bash
#
# Bring up the docker-compose stack used by the system-tests job.
# Composes the dev stack with docker-compose.ci.yml so the api and
# frontend services run from the CI-built `:ci`-tagged images that
# .github/actions/prepare-ci-host loaded onto the runner.

set -Eeuo pipefail

compose_args=(-f docker-compose.yml -f docker-compose.ci.yml)

echo "Starting long-lived system test services..."
docker compose "${compose_args[@]}" up -d --no-build --wait --timeout 300
