#!/usr/bin/env bash
# Keep the pull request report current while a Validate run is still going.
#
# Renders as soon as the run has named its pull request, so the change counts
# show within a minute, then again each time a coverage artifact lands. An
# artifact is listed and downloadable as soon as its job uploads it, before
# the run ends. Stops once the run completes: the `completed` report owns the
# final comment, and a render after it would overwrite it with less.
#
# Reads GH_TOKEN, REPO, RUN_ID, RUN_SHA, COVERAGE_ARTIFACTS, SUITE_ARTIFACTS,
# BASE_REF, BASELINE_PATH, POLL_SECONDS and DEADLINE_SECONDS.
set -euo pipefail

poll=${POLL_SECONDS:-20}
deadline=$((SECONDS + ${DEADLINE_SECONDS:-3000}))
rendered=none

run_status() {
  gh api "repos/$REPO/actions/runs/$RUN_ID" --jq '.status'
}

while [ "$SECONDS" -lt "$deadline" ]; do
  if [ "$(run_status)" = completed ]; then
    echo "The run finished; the completed report takes over."
    exit 0
  fi

  present=" $(gh api "repos/$REPO/actions/runs/$RUN_ID/artifacts?per_page=100" \
    --jq '[.artifacts[].name] | sort | join(" ")') "
  # The pull request number lives in an artifact: a fork's run names none.
  if [[ "$present" != *" pr-report-meta "* ]]; then
    sleep "$poll"
    continue
  fi

  landed=""
  for artifact in $SUITE_ARTIFACTS; do
    [[ "$present" == *" $artifact "* ]] && landed="$landed $artifact"
  done

  if [ "$landed" != "$rendered" ]; then
    rm -rf coverage-artifacts
    RUN_ID="$RUN_ID" .github/scripts/download_coverage.sh

    outputs=$(mktemp)
    GITHUB_OUTPUT="$outputs" .github/scripts/resolve_report_pr.sh
    number=$(sed -n 's/^number=//p' "$outputs")
    if [ -z "$number" ]; then
      exit 0
    fi

    # Checked again after the downloads, which take a while.
    if [ "$(run_status)" = completed ]; then
      echo "The run finished; the completed report takes over."
      exit 0
    fi
    IN_PROGRESS=1 PR_NUMBER="$number" COVERAGE_DIR=coverage-artifacts \
      python3 .github/scripts/pr_report.py render
    rendered=$landed

    missing=0
    for artifact in $SUITE_ARTIFACTS; do
      [[ "$landed" == *" $artifact"* ]] || missing=1
    done
    if [ "$missing" = 0 ]; then
      echo "Every suite has reported."
      exit 0
    fi
  fi

  sleep "$poll"
done

echo "Gave up waiting; the completed report still comments when the run ends."
