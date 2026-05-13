#!/usr/bin/env bash
#
# Bring up the docker-compose stack used by the system-tests job.
# Composes the dev stack with docker-compose.ci.yml so the api and
# frontend services run from the CI-built `:ci`-tagged images that
# .github/actions/prepare-ci-host loaded onto the runner.

set -Eeuo pipefail

compose_args=(-f docker-compose.yml -f docker-compose.ci.yml)

# Only bring up the services the system-test JVM actually talks to.
# Listmonk + listmonk-db + stalwart + vault are part of the dev compose
# but the api runs with SPRING_PROFILES_ACTIVE=test, which routes mail
# through MockListmonkEmailClient — none of those upstream containers
# are touched. Naming services explicitly also stops `--wait` from
# blocking on health checks for containers we never started.
echo "Starting system test services (db, api, frontend)..."
docker compose "${compose_args[@]}" up -d --no-build --wait --timeout 300 db api frontend

# Warm up the api so the first test in each shard doesn't pay the
# JIT / classpath / first-request latency on `POST /auth`. The
# healthcheck only proves /health responds; auth is materially
# heavier and would otherwise blow past the 30 s playwright timeout
# on a cold first call.
echo "Warming up api endpoints..."
for path in /health /csrf; do
  for i in 1 2 3 4 5; do
    curl -fsS -o /dev/null "http://localhost:8080$path" && break
    sleep 1
  done
done
curl -fsS -o /dev/null -X POST -H 'Content-Type: application/json' \
  --data '{"username":"warmup","password":"warmup"}' \
  "http://localhost:8080/auth" || true
