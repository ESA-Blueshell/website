#!/bin/bash
# Stalwart keeps every setting but the datastore pointer in its datastore, and
# binds a new listener only when it starts. So the first start applies each
# plan to a server running in the background and stops it, and the marker in
# the volume keeps a later start from seeding again.
set -euo pipefail

export STALWART_URL=http://127.0.0.1:8080
# stalwart-cli writes its state under HOME, and the image's home is read-only.
export HOME=/tmp
export STALWART_USER="${STALWART_RECOVERY_ADMIN%%:*}"
export STALWART_PASSWORD="${STALWART_RECOVERY_ADMIN#*:}"

config=/etc/stalwart/config.json
plans=/opt/stalwart-dev
seeded=/var/lib/stalwart/seeded

# Runs its arguments against a server started for them, then stops it.
with_server() {
  /usr/local/bin/stalwart --config "$config" &
  local pid=$!
  local tries=0
  until stalwart-cli get SystemSettings --json >/dev/null 2>&1; do
    tries=$((tries + 1))
    if [ "$tries" -gt 60 ]; then
      echo "stalwart-dev: the server never answered" >&2
      exit 1
    fi
    sleep 1
  done
  "$@"
  kill "$pid"
  wait "$pid" || true
}

# The seed mails every account at once, past what one IP may send.
settings() {
  stalwart-cli apply --file "$plans/settings.ndjson"
  local throttle
  throttle=$(stalwart-cli query MtaInboundThrottle --json | grep '"Sender IP throttle"' | grep -o '"id":"[^"]*"' | cut -d'"' -f4)
  printf '{"@type":"update","object":"MtaInboundThrottle","id":"%s","value":{"enable":false}}\n' "$throttle" \
    | stalwart-cli apply --file /dev/stdin
}

if [ ! -e "$seeded" ]; then
  # The password policy the accounts need applies only from the next start.
  with_server settings
  with_server stalwart-cli apply --file "$plans/seed.ndjson"
  touch "$seeded"
fi

exec /usr/local/bin/stalwart --config "$config"
