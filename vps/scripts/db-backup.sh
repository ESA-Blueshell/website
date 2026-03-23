#!/usr/bin/env bash
set -euo pipefail

cd /src/website
DB_ENV="/src/website/services/api/.db.env"
if [[ ! -f "$DB_ENV" ]]; then
  echo "Error: $DB_ENV not found; create it with DB credentials." >&2
  exit 1
fi

# Load sanitized env vars from .db.env
set -a
while IFS='=' read -r key val; do
  [[ -z "${key}" || "${key}" =~ ^[[:space:]]*# ]] && continue
  val="${val%$'\r'}"
  export "${key}=${val}"
done < <(grep -E '^[A-Za-z_][A-Za-z0-9_]*=' "$DB_ENV" | sed 's/\r$//')
set +a

DB_NAME="${MARIADB_DATABASE:-${MYSQL_DATABASE:-}}"
DB_USER="${MARIADB_USER:-${MYSQL_USER:-}}"
DB_PASS="${MARIADB_PASSWORD:-${MYSQL_PASSWORD:-}}"
if [[ -z "$DB_NAME" || -z "$DB_USER" || -z "$DB_PASS" ]]; then
  echo "Error: DB vars not set in $DB_ENV (need *_DATABASE, *_USER, *_PASSWORD)." >&2
  exit 1
fi

# Find the first running container whose name matches a pattern
find_container() {
  docker ps --filter "name=${1}" --format "{{.ID}}" | head -1
}

DB_CID="$(find_container "website_db")"
if [[ -z "${DB_CID}" ]]; then
  echo "Error: website_db container not found in Swarm stack." >&2
  exit 1
fi

TIMESTAMP="$(date +'%Y%m%d-%H%M%S')"
mkdir -p /src/backups/db

# 1a) MariaDB dump
MARIADB_OUT="/src/backups/db/db-backup-${TIMESTAMP}.sql.gz"
docker exec -i "${DB_CID}" \
  mysqldump --single-transaction --quick --routines --events \
    --databases "${DB_NAME}" -u"${DB_USER}" -p"${DB_PASS}" \
  | gzip -c > "${MARIADB_OUT}"

if [[ ! -s "${MARIADB_OUT}" ]]; then
  echo "Error: MariaDB backup output is empty: ${MARIADB_OUT}" >&2
  exit 1
fi
chmod 640 "${MARIADB_OUT}"
chown root:backup "${MARIADB_OUT}"

# 1b) Listmonk PostgreSQL dump (best-effort)
LISTMONK_OUT="(skipped)"
LISTMONK_DB_CID="$(find_container "website_listmonk-db" 2>/dev/null || true)"
if [[ -n "${LISTMONK_DB_CID}" ]]; then
  LISTMONK_OUT="/src/backups/db/listmonk-backup-${TIMESTAMP}.sql.gz"
  docker exec -i "${LISTMONK_DB_CID}" \
    pg_dump -U listmonk listmonk \
    | gzip -c > "${LISTMONK_OUT}"
  if [[ -s "${LISTMONK_OUT}" ]]; then
    chmod 640 "${LISTMONK_OUT}"
    chown root:backup "${LISTMONK_OUT}"
  else
    echo "Warning: Listmonk backup output is empty, skipping." >&2
    rm -f "${LISTMONK_OUT}"
    LISTMONK_OUT="(empty)"
  fi
else
  echo "Warning: website_listmonk-db not running; skipping Listmonk backup." >&2
fi

# 2) Environment files snapshot (mirrors each service's secrets)
mkdir -p /src/backups/env/services/api \
         /src/backups/env/services/listmonk \
         /src/backups/env/services/mailserver
for f in /src/website/services/api/.db.env \
         /src/website/services/api/.api.env; do
  [[ -f "$f" ]] && rsync -a "$f" /src/backups/env/services/api/ || true
done
[[ -f /src/website/services/listmonk/.listmonk.env ]] && \
  rsync -a /src/website/services/listmonk/.listmonk.env \
    /src/backups/env/services/listmonk/ || true
[[ -f /src/website/services/mailserver/mailserver.env ]] && \
  rsync -a /src/website/services/mailserver/mailserver.env \
    /src/backups/env/services/mailserver/ || true
chown -R root:backup /src/backups/env
chmod -R 640 /src/backups/env
find /src/backups/env -type d -exec chmod 2750 {} +

# 3) Storage mirror (incremental, no duplication of large files)
rsync -a --delete /src/website/storage/ /src/backups/storage/
chown -R root:backup /src/backups/storage
find /src/backups/storage -type d -exec chmod 2750 {} +
find /src/backups/storage -type f -exec chmod 640 {} +

# 4) Mailserver data mirror (mail-data + config; excludes ClamAV DBs which auto-update)
MAILSERVER_DATA="/src/website/services/mailserver/docker-data/dms"
if [[ -d "${MAILSERVER_DATA}" ]]; then
  mkdir -p /src/backups/mailserver/mail-data /src/backups/mailserver/config
  rsync -a --delete "${MAILSERVER_DATA}/mail-data/" /src/backups/mailserver/mail-data/
  rsync -a --delete "${MAILSERVER_DATA}/config/"    /src/backups/mailserver/config/
  chown -R root:backup /src/backups/mailserver
  find /src/backups/mailserver -type d -exec chmod 2750 {} +
  find /src/backups/mailserver -type f -exec chmod 640 {} +
fi

# Prune old DB dumps (keep 7 days)
find "/src/backups/db" -maxdepth 1 -type f -name 'db-backup-*.sql.gz' -mtime +7 -delete
find "/src/backups/db" -maxdepth 1 -type f -name 'listmonk-backup-*.sql.gz' -mtime +7 -delete

echo "Backup complete:"
echo "  MariaDB dump:   ${MARIADB_OUT}"
echo "  Listmonk dump:  ${LISTMONK_OUT}"
echo "  Env:            /src/backups/env/"
echo "  Storage:        /src/backups/storage/ (mirrored)"
echo "  Mailserver:     /src/backups/mailserver/ (mirrored, if present)"
