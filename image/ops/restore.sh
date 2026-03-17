#!/usr/bin/env bash
# =============================================================================
# restore.sh — push a local backup to the remote server.
#
# Restores:
#   - Most recent DB dump -> /src/website/env/1_dump.sql (used by docker-compose
#     on first start to seed the database)
#   - All DB dumps        -> /src/backups/
#   - Environment files   -> /src/website/env/
#   - File storage        -> /src/website/storage/ (incremental via rsync)
#
# Usage:
#   ./restore.sh [remote_host]
#
# If remote_host is omitted, REMOTE_HOST below is used.
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

REMOTE_USER="blueshell"
REMOTE_HOST="${1:-136.144.191.63}"
SSH_PORT=2222
SSH_KEY="${HOME}/.ssh/blueshell-admin"

LOCAL_BACKUP_DIR="${SCRIPT_DIR}/../../backup"
LOCAL_DB_DIR="${LOCAL_BACKUP_DIR}/db"
LOCAL_ENV_DIR="${LOCAL_BACKUP_DIR}/env"
LOCAL_STORAGE_DIR="${LOCAL_BACKUP_DIR}/storage"

SSH_OPTS="-p ${SSH_PORT} -i ${SSH_KEY} -o StrictHostKeyChecking=accept-new"
SSH_CMD="ssh ${SSH_OPTS} ${REMOTE_USER}@${REMOTE_HOST}"

echo "==> Restoring to ${REMOTE_USER}@${REMOTE_HOST}:${SSH_PORT}"

# 1) Locate the latest local DB dump
LATEST_DUMP="$(ls -1 "${LOCAL_DB_DIR}"/db-backup-*.sql.gz 2>/dev/null | sort | tail -n 1)"
if [[ -z "${LATEST_DUMP}" ]]; then
  echo "Error: no db-backup-*.sql.gz files found in ${LOCAL_DB_DIR}" >&2
  exit 1
fi
echo "--> Latest dump: ${LATEST_DUMP}"

# 2) Decompress to a temp file for seeding
TMP_DUMP="$(mktemp)"
trap 'rm -f "${TMP_DUMP}"' EXIT
gunzip -c "${LATEST_DUMP}" > "${TMP_DUMP}"

# 3) Push all DB dumps to the remote backup directory
echo "--> Pushing DB dumps..."
scp ${SSH_OPTS} "${LOCAL_DB_DIR}"/db-backup-*.sql.gz \
  "${REMOTE_USER}@${REMOTE_HOST}:/src/backups/" || true

# 4) Push environment files
echo "--> Pushing env files..."
rsync -avz --delete \
  -e "ssh ${SSH_OPTS}" \
  "${LOCAL_ENV_DIR}/" \
  "${REMOTE_USER}@${REMOTE_HOST}:/src/website/env/"

# 5) Push storage (incremental — avoids re-uploading unchanged files)
echo "--> Pushing storage (incremental)..."
rsync -avz --delete \
  -e "ssh ${SSH_OPTS}" \
  "${LOCAL_STORAGE_DIR}/" \
  "${REMOTE_USER}@${REMOTE_HOST}:/src/website/storage/"

# 6) Seed the DB: place the decompressed dump at the well-known path docker-compose reads on init
echo "--> Seeding 1_dump.sql from ${LATEST_DUMP}..."
scp ${SSH_OPTS} "${TMP_DUMP}" "${REMOTE_USER}@${REMOTE_HOST}:/src/website/env/1_dump.sql"

# 7) Fix remote ownership
${SSH_CMD} "sudo chown -R website:website /src/website/env /src/website/storage"

echo ""
echo "Restore complete from: ${LATEST_DUMP}"
