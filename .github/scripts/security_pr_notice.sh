#!/usr/bin/env bash
# Posts a Discord notice about a pull request that fixes a known vulnerability.
# Run by security-pr-notify.yml; PR text arrives only through the environment.
#
# MODE: opened, merged or ci-failed. A Renovate security PR merges itself when
# its checks pass, so it gets no "opened" notice: only a failed run or the merge.
set -euo pipefail

ids=$(printf '%s' "$BODY" | grep -oE 'CVE-[0-9]{4}-[0-9]+|GHSA(-[0-9a-z]{4}){3}' | sort -u | head -5 | paste -sd ' ' - || true)
security=false
case "$TITLE" in *'[SECURITY]'*) security=true ;; esac
case " $LABELS " in *' security '*) security=true ;; esac
[ -n "$ids" ] && security=true

# A Dependabot security update names the package it bumps but cites no id.
# Fixed as well as open alerts: an alert closes as soon as the merge lands.
if [ "$security" = false ] && [ "$AUTHOR" = "dependabot[bot]" ]; then
  pkg=$(printf '%s' "$TITLE" | sed -nE 's/.*[Bb]ump ([^ ]+) from .*/\1/p')
  if [ -n "$pkg" ]; then
    hits=$(gh api "repos/$REPO/dependabot/alerts?state=open,fixed&per_page=100" \
      | jq -r --arg pkg "$pkg" '[.[] | select(.dependency.package.name == $pkg) | (.security_advisory.cve_id // .security_advisory.ghsa_id)] | unique | join(" ")' || true)
    if [ -n "$hits" ]; then
      security=true
      ids=$hits
    fi
  fi
fi

if [ "$security" = false ]; then
  echo "Not a security fix."
  exit 0
fi

desc="$TITLE"
[ -n "$ids" ] && desc="$desc"$'\n'"Fixes: $ids"
case "$MODE" in
  opened)
    if [ "$AUTHOR" = "renovate[bot]" ]; then
      echo "Renovate security PR: it merges itself, so only a failure or the merge is announced."
      exit 0
    fi
    heading="Security fix opened: #$NUMBER"
    ;;
  merged)
    heading="Release with security fixes available"
    release=$(gh pr list -R "$REPO" --state open --label 'autorelease: pending' --json url --jq '.[0].url // empty')
    desc="Merged #$NUMBER: $desc"$'\n'"Release pull request: ${release:-opens within minutes}"
    ;;
  ci-failed)
    heading="Security fix CI failed: #$NUMBER"
    desc="$desc"$'\n'"It will not merge itself until this is fixed: ${RUN_URL:-}"
    ;;
  *)
    echo "Unknown MODE: $MODE" >&2
    exit 1
    ;;
esac

if [ -z "${DISCORD_WEBHOOK_URL:-}" ]; then
  echo "::warning::No Discord webhook secret is set; skipping the notification."
  exit 0
fi

jq -n --arg title "$heading" --arg url "$URL" --arg desc "$desc" \
  '{username: "Blueshell security", embeds: [{title: $title, url: $url, description: $desc, color: 15158332}]}' \
  | curl -fsS -H 'Content-Type: application/json' -d @- "$DISCORD_WEBHOOK_URL"
