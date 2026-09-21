#!/usr/bin/env bash
# Download a Validate run's coverage artifacts into ./coverage-artifacts.
#
# Every artifact is optional: a shard that failed uploads nothing, and both
# callers would rather report the gap than fail beside an already-failing
# suite. Reads GH_TOKEN, RUN_ID and COVERAGE_ARTIFACTS.
set -euo pipefail

mkdir -p coverage-artifacts
for artifact in $COVERAGE_ARTIFACTS; do
  gh run download "$RUN_ID" --repo "$GITHUB_REPOSITORY" \
    --name "$artifact" --dir "coverage-artifacts/$artifact" \
    || echo "no $artifact on run $RUN_ID"
done
