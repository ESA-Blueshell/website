#!/usr/bin/env bash
#
# On system-test failure, dump everything useful about the running
# docker-compose stack: container state, logs per service, and the
# tail of the compose-wide journal. Output goes to stdout so the
# GitHub Actions log captures it.
#
# These logs belong to the whole shard, not to the test that failed. Every test in
# the job drove the same api, so a refusal here was answered to whichever test was
# running at the time — which is not necessarily the one that failed. #1194 was
# raised on exactly that reading.

set -Euo pipefail

compose_args=(-f docker-compose.yml -f docker-compose.ci.yml)

echo "==== docker compose ps ===="
docker compose "${compose_args[@]}" ps || true

echo "==== docker compose logs (tail 400 per service) ===="
echo "NOTE: stack-wide, covering every test this shard ran. A line here is not"
echo "      evidence about the failing test unless its own failure message says so:"
echo "      the assertion prints failed=[...] for what that test's browser saw, and"
echo "      failed=[] means that browser was refused nothing."
docker compose "${compose_args[@]}" logs --tail=400 --no-color || true

echo "==== docker ps -a ===="
docker ps -a || true
