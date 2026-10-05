#!/usr/bin/env bash
# Write the pull request a Validate run tested to $GITHUB_OUTPUT as `number`,
# or nothing when the run names no pull request or its commit is no longer
# that pull request's head. Reads GH_TOKEN, REPO and RUN_SHA.
#
# Validate runs are never cancelled, so a run on an older commit can finish
# after the newer one. Only the head may comment, so the newest report stands.
set -euo pipefail

# The number comes from the run's artifact: workflow_run carries no pull
# request for a fork.
number_file=coverage-artifacts/pr-report-meta/pr-number
if [ ! -f "$number_file" ]; then
  echo "The run carried no pull request number; nothing to comment on."
  exit 0
fi
number=$(tr -dc '0-9' < "$number_file")
if [ -z "$number" ]; then
  echo "Empty pull request number."
  exit 0
fi

head=$(gh api "repos/$REPO/pulls/$number" --jq '.head.sha')
if [ "$head" != "$RUN_SHA" ]; then
  echo "This run tested $RUN_SHA, but the head of #$number is $head. The head's own run reports."
  exit 0
fi

echo "number=$number" >> "$GITHUB_OUTPUT"
