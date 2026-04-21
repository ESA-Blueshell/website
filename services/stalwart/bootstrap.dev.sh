#!/bin/sh
# Seed the dev bounce@dev.local account once per volume lifetime.
# Runs on every container start; short-circuits if the account already exists.
set -eu

STATE_DIR="/opt/stalwart/data"
MARKER="${STATE_DIR}/.dev-bootstrap-complete"

if [ -f "${MARKER}" ]; then
  exit 0
fi

# Wait for the admin HTTP endpoint before provisioning.
for _ in $(seq 1 60); do
  if wget -q --spider http://localhost:8080/healthz/ready 2>/dev/null; then
    break
  fi
  sleep 1
done

ADMIN_USER="${STALWART_FALLBACK_ADMIN_USER:-admin}"
ADMIN_SECRET="${STALWART_FALLBACK_ADMIN_SECRET:-admin}"

curl -fsSL -u "${ADMIN_USER}:${ADMIN_SECRET}" \
  -H 'Content-Type: application/json' \
  -X POST http://localhost:8080/api/principal \
  -d '{
    "type": "individual",
    "name": "bounce@dev.local",
    "secrets": ["bounce"],
    "emails": ["bounce@dev.local"],
    "description": "Dev bounce inbox for Listmonk"
  }' || true

touch "${MARKER}"
