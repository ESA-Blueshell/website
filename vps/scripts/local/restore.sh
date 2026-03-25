#!/usr/bin/env bash
# =============================================================================
# restore.sh — push a local backup to the remote server.
#
# Restores:
#   - Most recent MariaDB dump -> /src/website/services/api/1_dump.sql
#     (picked up by docker-entrypoint-initdb.d on first db container start)
#   - All DB dumps             -> /src/backups/db/
#   - Environment files        -> /src/website/services/{api,listmonk,mailserver}/
#   - File storage             -> /src/website/storage/ (incremental via rsync)
#   - Mailserver data          -> /src/website/services/mailserver/docker-data/dms/
#
# NOTE: Listmonk PostgreSQL data is NOT automatically restored.
#   After 'website up', restore manually via:
#     docker exec -i $(docker ps -q -f name=website_listmonk-db) psql -U listmonk listmonk < dump.sql
#
# Usage:
#   ./restore.sh [remote_host]
#
# If remote_host is omitted, REMOTE_HOST below is used.
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VPS_DIR="${SCRIPT_DIR}/../.."

# Source .env from the vps root if present
ENV_FILE="${VPS_DIR}/.env"
if [[ -f "${ENV_FILE}" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "${ENV_FILE}"
  set +a
fi

REMOTE_USER="admin"
REMOTE_HOST="${1:-${REMOTE_HOST:?Set REMOTE_HOST in vps/.env or pass as first argument}}"
SSH_PORT=2222
SSH_KEY="${HOME}/.ssh/blueshell-admin"

LOCAL_BACKUP_DIR="${VPS_DIR}/../../backup"
LOCAL_DB_DIR="${LOCAL_BACKUP_DIR}/db"
LOCAL_ENV_DIR="${LOCAL_BACKUP_DIR}/env"
LOCAL_STORAGE_DIR="${LOCAL_BACKUP_DIR}/storage"
LOCAL_MAILSERVER_DIR="${LOCAL_BACKUP_DIR}/mailserver"

SSH_OPTS="-p ${SSH_PORT} -i ${SSH_KEY} -o StrictHostKeyChecking=accept-new"
SSH_CMD="ssh ${SSH_OPTS} ${REMOTE_USER}@${REMOTE_HOST}"

echo "==> Restoring to ${REMOTE_USER}@${REMOTE_HOST}:${SSH_PORT}"

# 1) Locate the latest local MariaDB dump
LATEST_DUMP="$(ls -1 "${LOCAL_DB_DIR}"/db-backup-*.sql.gz 2>/dev/null | sort | tail -n 1)"
if [[ -z "${LATEST_DUMP}" ]]; then
  echo "Error: no db-backup-*.sql.gz files found in ${LOCAL_DB_DIR}" >&2
  exit 1
fi
echo "--> Latest MariaDB dump: ${LATEST_DUMP}"

# 2) Decompress to a temp file for seeding
TMP_DUMP="$(mktemp)"
trap 'rm -f "${TMP_DUMP}"' EXIT
gunzip -c "${LATEST_DUMP}" > "${TMP_DUMP}"

# 3) Push all DB dumps to the remote backup directory
echo "--> Pushing DB dumps..."
${SSH_CMD} "mkdir -p /src/backups/db"
scp ${SSH_OPTS} "${LOCAL_DB_DIR}"/db-backup-*.sql.gz \
  "${REMOTE_USER}@${REMOTE_HOST}:/src/backups/db/" || true
if ls "${LOCAL_DB_DIR}"/listmonk-backup-*.sql.gz >/dev/null 2>&1; then
  scp ${SSH_OPTS} "${LOCAL_DB_DIR}"/listmonk-backup-*.sql.gz \
    "${REMOTE_USER}@${REMOTE_HOST}:/src/backups/db/" || true
fi

# 4) Push environment files to service-specific locations
echo "--> Pushing env files..."
${SSH_CMD} "mkdir -p /src/website/services/api /src/website/services/listmonk /src/website/services/mailserver"
if [[ -d "${LOCAL_ENV_DIR}/services/api" ]]; then
  rsync -avz \
    -e "ssh ${SSH_OPTS}" \
    "${LOCAL_ENV_DIR}/services/api/" \
    "${REMOTE_USER}@${REMOTE_HOST}:/src/website/services/api/"
fi
if [[ -d "${LOCAL_ENV_DIR}/services/listmonk" ]]; then
  rsync -avz \
    -e "ssh ${SSH_OPTS}" \
    "${LOCAL_ENV_DIR}/services/listmonk/" \
    "${REMOTE_USER}@${REMOTE_HOST}:/src/website/services/listmonk/"
fi
if [[ -d "${LOCAL_ENV_DIR}/services/mailserver" ]]; then
  rsync -avz \
    -e "ssh ${SSH_OPTS}" \
    "${LOCAL_ENV_DIR}/services/mailserver/" \
    "${REMOTE_USER}@${REMOTE_HOST}:/src/website/services/mailserver/"
fi

# 5) Push storage (incremental — avoids re-uploading unchanged files)
echo "--> Pushing storage (incremental)..."
${SSH_CMD} "mkdir -p /src/website/storage"
rsync -avz --delete \
  -e "ssh ${SSH_OPTS}" \
  "${LOCAL_STORAGE_DIR}/" \
  "${REMOTE_USER}@${REMOTE_HOST}:/src/website/storage/"

# 6) Seed the MariaDB init: place decompressed dump where docker-compose reads it on first start
echo "--> Seeding services/api/1_dump.sql from ${LATEST_DUMP}..."
scp ${SSH_OPTS} "${TMP_DUMP}" "${REMOTE_USER}@${REMOTE_HOST}:/src/website/services/api/1_dump.sql"

# 7) Push mailserver data (mail-data + config)
if [[ -d "${LOCAL_MAILSERVER_DIR}" ]]; then
  echo "--> Pushing mailserver data..."
  REMOTE_DMS="/src/website/services/mailserver/docker-data/dms"
  ${SSH_CMD} "mkdir -p ${REMOTE_DMS}/mail-data ${REMOTE_DMS}/config"
  if [[ -d "${LOCAL_MAILSERVER_DIR}/mail-data" ]]; then
    rsync -avz --delete \
      -e "ssh ${SSH_OPTS}" \
      "${LOCAL_MAILSERVER_DIR}/mail-data/" \
      "${REMOTE_USER}@${REMOTE_HOST}:${REMOTE_DMS}/mail-data/"
  fi
  if [[ -d "${LOCAL_MAILSERVER_DIR}/config" ]]; then
    rsync -avz --delete \
      -e "ssh ${SSH_OPTS}" \
      "${LOCAL_MAILSERVER_DIR}/config/" \
      "${REMOTE_USER}@${REMOTE_HOST}:${REMOTE_DMS}/config/"
  fi
fi

# 8) Fix remote ownership
${SSH_CMD} "sudo chown -R website:website /src/website/services /src/website/storage"

echo ""
echo "Restore complete from: ${LATEST_DUMP}"
echo ""
echo "NOTE: Listmonk PostgreSQL data must be restored manually if needed."
echo "  Gunzip a listmonk-backup-*.sql.gz from backup/db/ and pipe to:"
echo "    docker exec -i \$(docker ps -q -f name=website_listmonk-db) psql -U listmonk listmonk < dump.sql"
