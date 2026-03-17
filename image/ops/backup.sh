#!/usr/bin/env bash
# =============================================================================
# backup.sh — pull a full backup from the remote server to ./backup/ locally.
#
# Backs up:
#   - MariaDB dumps       -> ./backup/db/  (db-backup-*.sql.gz)
#   - Listmonk PG dumps   -> ./backup/db/  (listmonk-backup-*.sql.gz, if present)
#   - Env files           -> ./backup/env/services/{api,listmonk,mailserver}/
#   - File storage        -> ./backup/storage/
#   - Mailserver data     -> ./backup/mailserver/  (mail-data/ + config/)
#
# Usage:
#   ./backup.sh [remote_host]
#
# If remote_host is omitted, REMOTE_HOST below is used.
# Requires: ssh, scp, rsync, and a valid SSH key at SSH_KEY.
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

REMOTE_USER="blueshell"
REMOTE_HOST="${1:-136.144.191.63}"
SSH_PORT=2222
SSH_KEY="${HOME}/.ssh/blueshell-admin"

LOCAL_BACKUP_DIR="${SCRIPT_DIR}/../../backup"

SSH_OPTS="-p ${SSH_PORT} -i ${SSH_KEY} -o StrictHostKeyChecking=accept-new"
SSH_CMD="ssh ${SSH_OPTS} ${REMOTE_USER}@${REMOTE_HOST}"

echo "==> Backing up from ${REMOTE_USER}@${REMOTE_HOST}:${SSH_PORT}"

# 1) Trigger a fresh server-side backup (DB dumps + env snapshot + storage mirror)
echo "--> Running remote backup..."
${SSH_CMD} "sudo /usr/local/bin/db-backup" || true

# 2) Ensure storage files are group-readable (blueshell is in the website group)
${SSH_CMD} "sudo chmod -R g+rX /src/website/storage" || true

# 3) Fetch DB dumps (MariaDB + Listmonk)
echo "--> Fetching DB dumps..."
mkdir -p "${LOCAL_BACKUP_DIR}/db"
scp ${SSH_OPTS} "${REMOTE_USER}@${REMOTE_HOST}:/src/backups/db/db-backup-*.sql.gz" \
  "${LOCAL_BACKUP_DIR}/db/" || true
scp ${SSH_OPTS} "${REMOTE_USER}@${REMOTE_HOST}:/src/backups/db/listmonk-backup-*.sql.gz" \
  "${LOCAL_BACKUP_DIR}/db/" 2>/dev/null || true

# 4) Fetch environment files (contains secrets — keep this directory secure)
echo "--> Fetching env files..."
mkdir -p "${LOCAL_BACKUP_DIR}/env"
rsync -avz --delete \
  -e "ssh ${SSH_OPTS}" \
  "${REMOTE_USER}@${REMOTE_HOST}:/src/backups/env/" \
  "${LOCAL_BACKUP_DIR}/env/"

# 5) Fetch storage (incremental — avoids re-downloading unchanged files)
echo "--> Fetching storage (incremental)..."
mkdir -p "${LOCAL_BACKUP_DIR}/storage"
rsync -avz --delete \
  -e "ssh ${SSH_OPTS}" \
  "${REMOTE_USER}@${REMOTE_HOST}:/src/backups/storage/" \
  "${LOCAL_BACKUP_DIR}/storage/"

# 6) Fetch mailserver data (mail-data + config; incremental)
echo "--> Fetching mailserver data (incremental)..."
mkdir -p "${LOCAL_BACKUP_DIR}/mailserver"
rsync -avz --delete \
  -e "ssh ${SSH_OPTS}" \
  "${REMOTE_USER}@${REMOTE_HOST}:/src/backups/mailserver/" \
  "${LOCAL_BACKUP_DIR}/mailserver/" 2>/dev/null || true

echo ""
echo "Backup complete. Local copies at ${LOCAL_BACKUP_DIR}:"
echo "  DB dumps:    db/  (db-backup-*.sql.gz, listmonk-backup-*.sql.gz)"
echo "  Env files:   env/services/{api,listmonk,mailserver}/"
echo "  Storage:     storage/"
echo "  Mailserver:  mailserver/  (mail-data/ + config/)"
