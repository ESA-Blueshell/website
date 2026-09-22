#!/usr/bin/env bash
# Download the newest coverage baseline uploaded from the default branch into
# ./coverage-baseline. Reads GH_TOKEN, GITHUB_REPOSITORY, BASELINE_ARTIFACT and
# DEFAULT_BRANCH.
#
# An artifact rather than a cache: this repository holds more cache than its
# limit, so the buildkit blobs evict a 450-byte baseline by least-recent-use
# within hours and a pull request then reads an empty comparison. An artifact
# lives out its retention and nothing else can push it out.
#
# Missing is not fatal. A report naming the gap beats a job that fails.
set -euo pipefail

dir=coverage-baseline
mkdir -p "$dir"

# Newest first, and only uploads from the default branch: no other ref may
# decide what a pull request compares against.
id=$(gh api "repos/$GITHUB_REPOSITORY/actions/artifacts?name=$BASELINE_ARTIFACT&per_page=100" \
  --jq "[.artifacts[]
         | select(.expired == false)
         | select(.workflow_run.head_branch == \"$DEFAULT_BRANCH\")]
        | sort_by(.created_at) | reverse | .[0].id // \"\"")

if [ -z "$id" ]; then
  echo "No $BASELINE_ARTIFACT artifact on $DEFAULT_BRANCH yet; nothing to compare against."
  exit 0
fi

gh api "repos/$GITHUB_REPOSITORY/actions/artifacts/$id/zip" > "$dir/baseline.zip"
unzip -o -q "$dir/baseline.zip" -d "$dir"
rm -f "$dir/baseline.zip"
echo "Baseline artifact $id restored:"
cat "$dir/baseline.json"
