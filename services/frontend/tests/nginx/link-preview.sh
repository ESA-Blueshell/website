#!/usr/bin/env bash
# Serves the built frontend image against a stub api (the same image, own config) and checks which link preview each address gets.
# Usage: link-preview.sh <frontend image>
set -Eeuo pipefail

image=${1:?frontend image}
run=link-preview-$$
port=${PORT:-3999}

cleanup() {
  docker rm -f "$run-api" "$run-frontend" >/dev/null 2>&1 || true
  docker network rm "$run" >/dev/null 2>&1 || true
}
trap cleanup EXIT

stub=$(mktemp)
cat >"$stub" <<'EOF'
server {
  listen 8080;
  location = /events/1/link-preview { default_type text/html; return 200 '<title>Event 1</title>'; }
  location = /events/2/link-preview { return 401 '{"title":"Unauthorized"}'; }
  # Nothing answers there, so the frontend's own timeout is what ends the wait.
  location = /events/3/link-preview { proxy_pass http://10.255.255.1; proxy_connect_timeout 30s; }
  location / { return 404; }
}
EOF
chmod 644 "$stub"

docker network create "$run" >/dev/null
docker run -d --name "$run-api" --network "$run" --network-alias api \
  -v "$stub:/etc/nginx/conf.d/default.conf:ro" --entrypoint nginx "$image" -g "daemon off;" >/dev/null
docker run -d --name "$run-frontend" --network "$run" -p "127.0.0.1:$port:3000" "$image" >/dev/null

for _ in $(seq 30); do curl -sf -o /dev/null "http://127.0.0.1:$port/healthz" && break; sleep 1; done

failed=0
expect() {
  local path=$1 title=$2 started body took
  started=$(date +%s)
  body=$(curl -sf --max-time 5 "http://127.0.0.1:$port$path")
  took=$(($(date +%s) - started))
  if [[ $body != *"<title>$title</title>"* ]] || ((took > 2)); then
    echo "FAIL $path: wanted <title>$title</title> within 2s, took ${took}s"
    failed=1
  else
    echo "ok   $path"
  fi
}

site="Blueshell Esports - Student esports association"
expect /events/1 "Event 1"
expect "/events/1?tab=signups" "Event 1"
expect "/events?event=1" "Event 1"
expect "/events?from=x&event=1" "Event 1"
expect / "$site"
expect /events "$site"
expect /events/1/edit "$site"
expect /events/2 "$site"
expect /events/3 "$site"
expect /events/9 "$site"

if [[ $(curl -s "http://127.0.0.1:$port/events/1" | grep -c "og:title") != 0 ]]; then
  echo "FAIL /events/1: generic tags served beside the event's"
  failed=1
fi

docker stop "$run-api" >/dev/null
expect /events/1 "$site"

exit $failed
