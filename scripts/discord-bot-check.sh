#!/usr/bin/env bash
# Check that a Discord bot is ready for the website, and optionally register
# its token. See platform/docs/discord-bot.md for creating the bot itself.
#
# The token never reaches a process argument, where `ps` would show it: curl
# reads the Authorization header from a file descriptor, and Vault reads the
# value from stdin.

set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  scripts/discord-bot-check.sh [--guild <server-id>] [--dev | --vault]

The token comes from DISCORD_BOT_TOKEN when set, otherwise from stdin when it
is piped, otherwise from a hidden prompt. The server id comes from --guild,
then DISCORD_GUILD_ID, then services/api/.api.env.

Checks:
  - the token is a bot token Discord accepts
  - the Presence, Server Members and Message Content intents are on
  - the bot is in the server, with the invite link to add it where it is not
  - the bot's roles there grant every permission it needs

Once every check passes:
  --dev    write DISCORD_BOT_TOKEN and DISCORD_GUILD_ID into services/api/.api.env
  --vault  patch secret/api (discord-bot-token, discord-guild-id) and restart
           the api deployment
EOF
}

API="https://discord.com/api/v10"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$ROOT/services/api/.api.env"
# Create Invite (1 << 0), View Channels (1 << 10), Send Messages (1 << 11), Embed Links (1 << 14),
# Attach Files (1 << 15), Read Message History (1 << 16), Mention All Roles (1 << 17) and Create
# Events (1 << 44): reading the server, inviting to it, posting to it and listing events in it.
# Nothing that manages it.
PERMISSIONS=17592186293249
# The intent flags on an application: the limited bits are what an unverified bot has, the full
# bits what a verified one has.
PRESENCE_FLAGS=$(((1 << 12) | (1 << 13)))
MEMBERS_FLAGS=$(((1 << 14) | (1 << 15)))
CONTENT_FLAGS=$(((1 << 18) | (1 << 19)))

guild=""
write=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --guild) guild="${2:?--guild needs a server id}"; shift 2 ;;
    --dev) write="dev"; shift ;;
    --vault) write="vault"; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; usage >&2; exit 2 ;;
  esac
done

for cmd in curl jq; do
  command -v "$cmd" >/dev/null || { echo "Needs $cmd on the PATH." >&2; exit 2; }
done

env_value() {
  [[ -f "$ENV_FILE" ]] || return 0
  sed -n "s/^$1=//p" "$ENV_FILE" | tail -1
}

token="${DISCORD_BOT_TOKEN:-}"
if [[ -z "$token" ]]; then
  if [[ -t 0 ]]; then
    read -rsp "Bot token (hidden): " token
    echo
  else
    IFS= read -r token || true
  fi
fi
[[ -n "$token" ]] || { echo "No token given." >&2; exit 2; }

guild="${guild:-${DISCORD_GUILD_ID:-$(env_value DISCORD_GUILD_ID)}}"

failed=0
pass() { printf '  \033[32mpass\033[0m  %s\n' "$1"; }
fail() { printf '  \033[31mfail\033[0m  %s\n        %s\n' "$1" "$2"; failed=1; }

# GET a Discord path as the bot, into $body and $status. Not a command substitution: that runs in
# a subshell, and the status would be lost with it.
discord_get() {
  local out
  out="$(curl -sS -w '\n%{http_code}' -H @<(printf 'Authorization: Bot %s\n' "$token") "$API$1")"
  status="${out##*$'\n'}"
  body="${out%$'\n'*}"
}

echo "Checking the Discord bot:"

discord_get /users/@me
me="$body"
if [[ "$status" != 200 ]]; then
  fail "Discord accepts the token" "Discord answered $status. Reset the token on the Bot tab and copy it again."
  exit 1
fi
if [[ "$(jq -r '.bot // false' <<<"$me")" == true ]]; then
  pass "the token belongs to the bot $(jq -r '.username' <<<"$me")"
else
  fail "the token belongs to a bot" "It belongs to a user account. Use the token from the application's Bot tab."
fi

discord_get /oauth2/applications/@me
app="$body"
if [[ "$status" == 200 ]]; then
  app_id="$(jq -r '.id' <<<"$app")"
  flags="$(jq -r '.flags // 0' <<<"$app")"
  if (( flags & PRESENCE_FLAGS )); then
    pass "the Presence intent is on"
  else
    fail "the Presence intent is on" "Turn on Presence Intent under Privileged Gateway Intents on the Bot tab."
  fi
  if (( flags & MEMBERS_FLAGS )); then
    pass "the Server Members intent is on"
  else
    fail "the Server Members intent is on" "Turn on Server Members Intent under Privileged Gateway Intents on the Bot tab."
  fi
  if (( flags & CONTENT_FLAGS )); then
    pass "the Message Content intent is on"
  else
    fail "the Message Content intent is on" "Turn on Message Content Intent under Privileged Gateway Intents on the Bot tab."
  fi
  if [[ "$(jq -r '.bot_public // false' <<<"$app")" == true ]]; then
    fail "the bot is private" "Set the install link to None on the Installation tab, then turn off Public Bot on the Bot tab."
  else
    pass "the bot is private"
  fi
else
  fail "the application can be read" "Discord answered $status for /oauth2/applications/@me."
  app_id=""
fi

invite="https://discord.com/oauth2/authorize?client_id=${app_id:-<APPLICATION_ID>}&scope=bot&permissions=$PERMISSIONS"

# What the bot's roles in the server grant it, @everyone's included: each permission it needs,
# or all of them where a role makes it an administrator.
check_permissions() {
  discord_get "/guilds/$guild/members/$(jq -r '.id' <<<"$me")"
  if [[ "$status" != 200 ]]; then
    fail "the bot's roles can be read" "Discord answered $status for the bot's own member."
    return
  fi
  local held=0 permission
  while read -r permission; do
    held=$(( held | permission ))
  done < <(jq -r --argjson mine "$(jq '.roles' <<<"$body")" --arg everyone "$guild" \
    '.roles[] | select(.id == $everyone or (.id as $id | $mine | index($id))) | .permissions' <<<"$server")
  local name bit
  for name in "Create Invite:0" "View Channels:10" "Send Messages:11" "Embed Links:14" \
    "Attach Files:15" "Read Message History:16" "Mention All Roles:17" "Create Events:44"; do
    bit=$(( 1 << ${name##*:} ))
    if (( held & (1 << 3) || held & bit )); then
      pass "the bot may ${name%%:*}"
    else
      fail "the bot may ${name%%:*}" "Open the invite link again to grant it; Discord updates the bot's role in place: $invite"
    fi
  done
}
if [[ -z "$guild" ]]; then
  fail "a server is named" "Pass --guild <server-id>, or set DISCORD_GUILD_ID. Copy the id with Developer Mode on."
else
  discord_get "/guilds/$guild"
  server="$body"
  if [[ "$status" == 200 ]]; then
    pass "the bot is in the server $(jq -r '.name' <<<"$server")"
    check_permissions
  else
    fail "the bot is in the server $guild" "Discord answered $status. Somebody with Manage Server adds it here: $invite"
  fi
fi

if (( failed )); then
  echo "Not ready. Fix the failures above and run this again."
  exit 1
fi
echo "Ready."

case "$write" in
  dev)
    touch "$ENV_FILE"
    # Values go through the environment rather than awk's arguments, so the token stays out of `ps`.
    TOKEN="$token" GUILD="$guild" awk '
      /^DISCORD_BOT_TOKEN=/ { print "DISCORD_BOT_TOKEN=" ENVIRON["TOKEN"]; token = 1; next }
      /^DISCORD_GUILD_ID=/ { print "DISCORD_GUILD_ID=" ENVIRON["GUILD"]; guild = 1; next }
      { print }
      END {
        if (!token) print "DISCORD_BOT_TOKEN=" ENVIRON["TOKEN"]
        if (!guild) print "DISCORD_GUILD_ID=" ENVIRON["GUILD"]
      }' "$ENV_FILE" > "$ENV_FILE.tmp"
    chmod 600 "$ENV_FILE.tmp"
    mv "$ENV_FILE.tmp" "$ENV_FILE"
    echo "Wrote DISCORD_BOT_TOKEN and DISCORD_GUILD_ID to services/api/.api.env. Restart the api to pick them up."
    ;;
  vault)
    command -v vault >/dev/null || { echo "Needs the vault CLI, logged in." >&2; exit 2; }
    command -v kubectl >/dev/null || { echo "Needs kubectl, pointed at the cluster." >&2; exit 2; }
    # patch, never put: put replaces every field of secret/api.
    printf '%s' "$token" | vault kv patch secret/api discord-bot-token=- discord-guild-id="$guild" >/dev/null
    kubectl -n default rollout restart deployment/api
    echo "Patched secret/api and restarted the api."
    ;;
esac
