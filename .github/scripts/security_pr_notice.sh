#!/usr/bin/env bash
# Posts and clears the Discord notices about pull requests that fix a known
# vulnerability. Run by security-pr-notify.yml and release.yml; PR text arrives
# only through the environment.
#
# MODE: opened, ci-failed, closed or released. A Renovate security PR merges
# itself when its checks pass, so it gets no "opened" notice: only a failed run
# or the merge. Closing a PR deletes its "CI failed" messages; merging it posts
# "release available", which `released` deletes once that release is cut.
#
# A webhook cannot list what it posted, so the message ids live in a hidden
# comment on the PR, and the `security: awaiting release` label marks the PRs
# whose "release available" message is still up.
set -euo pipefail

MARKER='<!-- security-pr-notice'
PENDING_LABEL='security: awaiting release'
webhook=${DISCORD_WEBHOOK_URL:-}
webhook_base=${webhook%%\?*}

# Prints "<comment id>" then one "<kind> <message id>" line per notice.
read_state() {
  gh api "repos/$REPO/issues/$1/comments" --paginate \
    --jq ".[] | select(.body | startswith(\"$MARKER\")) | \"\(.id)\n\(.body)\"" \
    | awk 'NR == 1 { print; next } /^(ci-failed|release) [0-9]+$/' | head -n 100
}

# Writes the notice lines on stdin back to PR $1, reusing comment $2 if set.
write_state() {
  local lines text
  lines=$(cat)
  text="$MARKER"$'\n'"$lines"$'\n'"-->"$'\n'"Discord notices about this security fix. They are deleted once settled."
  if [ -n "$2" ]; then
    gh api -X PATCH "repos/$REPO/issues/comments/$2" -f body="$text" >/dev/null
  elif [ -n "$lines" ]; then
    gh api "repos/$REPO/issues/$1/comments" -f body="$text" >/dev/null
  fi
}

# Deletes every message of kind $2 recorded on PR $1.
delete_notices() {
  local state comment id code
  state=$(read_state "$1")
  comment=$(head -n 1 <<<"$state")
  [ -n "$comment" ] || return 0
  for id in $(tail -n +2 <<<"$state" | awk -v kind="$2" '$1 == kind { print $2 }'); do
    code=$(curl -sS -o /dev/null -w '%{http_code}' -X DELETE "$webhook_base/messages/$id")
    case "$code" in
      204 | 404) echo "Deleted $2 message $id from #$1." ;;
      *) echo "::warning::Deleting $2 message $id from #$1 returned HTTP $code." ;;
    esac
  done
  tail -n +2 <<<"$state" | awk -v kind="$2" '$1 != kind' | write_state "$1" "$comment"
}

# Posts the embed built from $heading and $desc and prints its message id.
post() {
  jq -n --arg title "$heading" --arg url "$URL" --arg desc "$desc" \
    '{username: "Blueshell security", embeds: [{title: $title, url: $url, description: $desc, color: 15158332}]}' \
    | curl -fsS -H 'Content-Type: application/json' -d @- "$webhook_base?wait=true" | jq -r .id
}

# Posts the embed and records its message id on PR $NUMBER as kind $1.
post_notice() {
  local id state
  id=$(post)
  state=$(read_state "$NUMBER")
  { tail -n +2 <<<"$state"; echo "$1 $id"; } | sed '/^$/d' | write_state "$NUMBER" "$(head -n 1 <<<"$state")"
}

if [ -z "$webhook" ]; then
  echo "::warning::No Discord webhook secret is set; skipping the notification."
  exit 0
fi

if [ "$MODE" = released ]; then
  for pr in $(gh pr list -R "$REPO" --state merged --label "$PENDING_LABEL" --limit 100 --json number --jq '.[].number'); do
    delete_notices "$pr" release
    gh pr edit "$pr" -R "$REPO" --remove-label "$PENDING_LABEL" >/dev/null
  done
  exit 0
fi

if [ "$MODE" = closed ]; then
  delete_notices "$NUMBER" ci-failed
  if [ "${MERGED:-false}" != true ]; then
    exit 0
  fi
fi

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
    post >/dev/null
    ;;
  closed)
    heading="Release with security fixes available"
    release=$(gh pr list -R "$REPO" --state open --label 'autorelease: pending' --json url --jq '.[0].url // empty')
    desc="Merged #$NUMBER: $desc"$'\n'"Release pull request: ${release:-opens within minutes}"
    gh pr edit "$NUMBER" -R "$REPO" --add-label "$PENDING_LABEL" >/dev/null
    post_notice release
    ;;
  ci-failed)
    heading="Security fix CI failed: #$NUMBER"
    desc="$desc"$'\n'"It will not merge itself until this is fixed: ${RUN_URL:-}"
    post_notice ci-failed
    ;;
  *)
    echo "Unknown MODE: $MODE" >&2
    exit 1
    ;;
esac
