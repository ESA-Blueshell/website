#!/usr/bin/env bash
# =============================================================================
# Packer provisioner: bake everything that is NOT instance-specific into the
# image.  Instance-specific config (keys, secrets, GHCR login) is handled by
# cloud-init at first boot on Contabo.
# =============================================================================
set -euxo pipefail

export DEBIAN_FRONTEND=noninteractive

# ── Docker APT repo ──────────────────────────────────────────────────────────
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/debian/gpg \
  | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
chmod 644 /etc/apt/keyrings/docker.gpg

cat > /etc/apt/sources.list.d/docker.list <<'REPO'
deb [arch=amd64 signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/debian bookworm stable
REPO

# ── Packages ─────────────────────────────────────────────────────────────────
apt-get update
apt-get upgrade -y
apt-get install -y \
  ca-certificates curl gnupg git \
  openssh-client openssh-server cron \
  mariadb-client whois rsync \
  docker-ce docker-ce-cli containerd.io \
  docker-buildx-plugin docker-compose-plugin \
  dbus-user-session uidmap slirp4netns fuse-overlayfs \
  docker-ce-rootless-extras \
  ufw sudo cloud-init

# ── Groups ───────────────────────────────────────────────────────────────────
groupadd -f website
groupadd -f backup

# ── Users ────────────────────────────────────────────────────────────────────
# blueshell: admin, sudo WITH password required
useradd -m -s /bin/bash -G sudo,docker,website,backup blueshell || true

# website: application user, no sudo, rootless docker
useradd -m -s /bin/bash -d /src/website -g website website || true

# ── SSH hardening ────────────────────────────────────────────────────────────
cat > /etc/ssh/sshd_config.d/10-keys-only.conf <<'SSH'
 PasswordAuthentication no
KbdInteractiveAuthentication no
ChallengeResponseAuthentication no
PubkeyAuthentication yes
AuthenticationMethods publickey
PermitRootLogin prohibit-password
SSH

cat > /etc/ssh/sshd_config.d/port-2222.conf <<'SSH'
Port 2222
SSH

# ── UFW firewall ─────────────────────────────────────────────────────────────
ufw default deny incoming
ufw default allow outgoing
ufw allow 2222/tcp
ufw allow 80/tcp
ufw allow 443/tcp
yes | ufw enable || true

# ── Sysctl: allow unprivileged ports from 80 (for rootless docker) ───────────
cat > /etc/sysctl.d/50-unprivileged-ports.conf <<'SYSCTL'
net.ipv4.ip_unprivileged_port_start=80
SYSCTL

# ── Disable rootful Docker (we use rootless) ─────────────────────────────────
systemctl disable --now docker.service docker.socket || true

# ── Directory structure ──────────────────────────────────────────────────────
mkdir -p /src/website
chown website:website /src/website
chmod 770 /src/website

mkdir -p /src/backups/db
mkdir -p /src/backups/env
mkdir -p /src/backups/storage
mkdir -p /src/backups/mailserver/mail-data
mkdir -p /src/backups/mailserver/config
chown -R root:backup /src/backups
chmod -R 2770 /src/backups

touch /var/log/db-backup.log
chown root:backup /var/log/db-backup.log
chmod 664 /var/log/db-backup.log

# ── Website user profile (rootless docker socket) ────────────────────────────
cat > /src/website/.profile <<'PROFILE'
export DOCKER_HOST="unix:///run/user/$(id -u)/docker.sock"
PROFILE
chown website:website /src/website/.profile

cat > /etc/profile.d/website-docker.sh <<'PROFILE'
if [ "$(id -un)" = "website" ]; then
  export DOCKER_HOST=unix:///run/user/$(id -u)/docker.sock
fi
PROFILE

# ── Rootless docker socket override ─────────────────────────────────────────
mkdir -p /home/website/.config/systemd/user/docker.socket.d
cat > /home/website/.config/systemd/user/docker.socket.d/override.conf <<'UNIT'
[Socket]
SocketMode=0660
UNIT
# Can't chown to website yet since it may not have a home dir at /home/website
# Cloud-init handles final ownership

# ── Backup script (backs up DB, env, and storage mirror) ─────────────────────
cat > /usr/local/bin/db-backup <<'BACKUP'
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

WEBSITE_UID="$(id -u website)"
ROOTLESS_SOCK="/run/user/${WEBSITE_UID}/docker.sock"
export XDG_RUNTIME_DIR="/run/user/${WEBSITE_UID}"

rcompose() {
  sudo -u website -H env XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR}" DOCKER_HOST="unix://${ROOTLESS_SOCK}" bash -lc "docker compose -f 'docker-compose.yml' $*"
}

# Ensure the user service is up (in case of reboot race)
sudo -u website -H bash -lc 'systemctl --user start docker' || true
sleep 1

if ! rcompose ps db >/dev/null 2>&1; then
  echo "Bringing up compose stack to access db service..."
  rcompose up -d
fi

DB_CIDS="$(rcompose ps -q db || true)"
DB_CID_COUNT="$(printf '%s\n' "$DB_CIDS" | sed '/^$/d' | wc -l | tr -d ' ')"
if [[ "$DB_CID_COUNT" -lt 1 ]]; then
  echo "Error: db container not found."
  exit 1
fi

TIMESTAMP="$(date +'%Y%m%d-%H%M%S')"
mkdir -p /src/backups/db

# 1a) MariaDB dump
MARIADB_OUT="/src/backups/db/db-backup-${TIMESTAMP}.sql.gz"
rcompose exec -T db mysqldump \
  --single-transaction --quick --routines --events \
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
LISTMONK_DB_CIDS="$(rcompose ps -q listmonk-db 2>/dev/null || true)"
LISTMONK_DB_COUNT="$(printf '%s\n' "$LISTMONK_DB_CIDS" | sed '/^$/d' | wc -l | tr -d ' ')"
if [[ "$LISTMONK_DB_COUNT" -ge 1 ]]; then
  LISTMONK_OUT="/src/backups/db/listmonk-backup-${TIMESTAMP}.sql.gz"
  rcompose exec -T listmonk-db pg_dump -U listmonk listmonk \
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
  echo "Warning: listmonk-db not running; skipping Listmonk backup." >&2
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
BACKUP
chmod 0750 /usr/local/bin/db-backup
chown root:backup /usr/local/bin/db-backup

# ── Website management CLI ───────────────────────────────────────────────────
cat > /usr/local/bin/website <<'CLI'
#!/usr/bin/env bash
set -euo pipefail
usage() { cat <<EOF
website - manage the website stack as website (rootless)

Usage:
  website status            Show compose services and their status
  website up                Bring the stack up (detached)
  website down              Stop the stack
  website logs [service]    Tail logs (all or specific service)
  website backup            Run DB backup now
  website pull              git fetch/reset/pull + compose pull + recreate
  website shell             Open a shell as website user in repo dir
  website help              Show this help
EOF
}
as_website() {
    local sock="/run/user/$(id -u website)/docker.sock"
    if [[ "$(id -un)" == "website" ]]; then
      DOCKER_HOST="unix://${sock}" \
      XDG_RUNTIME_DIR="/run/user/$(id -u)" \
      bash -lc "$*"
    else
      sudo -u website -H env \
      DOCKER_HOST="unix://${sock}" \
      XDG_RUNTIME_DIR="/run/user/$(id -u website)" \
      bash -lc "$*"
    fi
}
cmd="${1:-help}"
case "${cmd}" in
  status) as_website "cd /src/website && docker compose -f 'docker-compose.yml' ps" ;;
  up)     as_website "cd /src/website && docker compose -f 'docker-compose.yml' up -d" ;;
  down)   as_website "cd /src/website && docker compose -f 'docker-compose.yml' down" ;;
  logs)   shift || true; svc="${1:-}"; if [[ -n "${svc}" ]]; then
            as_website "cd /src/website && docker compose -f 'docker-compose.yml' logs -f --tail=200 '${svc}'"
          else
            as_website "cd /src/website && docker compose -f 'docker-compose.yml' logs -f --tail=200"
          fi ;;
  backup) /usr/local/bin/db-backup ;;
  pull)
    as_website "cd /src/website && git fetch --prune || true"
    as_website "cd /src/website && git reset --hard origin/main || true"
    as_website "cd /src/website && git pull --ff-only || true"
    as_website "cd /src/website && docker compose -f 'docker-compose.yml' pull"
    as_website "cd /src/website && docker compose -f 'docker-compose.yml' up -d --remove-orphans"
    ;;
  shell)  as_website "cd /src/website && exec bash" ;;
  help|*) usage ;;
esac
CLI
chmod 0755 /usr/local/bin/website

# ── Cron: daily backup at 3 AM ──────────────────────────────────────────────
cat > /etc/cron.d/db-backup <<'CRON'
SHELL=/bin/bash
PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
# Full backup (DB + env + storage mirror) daily at 3 AM
0 3 * * * root /usr/local/bin/db-backup >> /var/log/db-backup.log 2>&1
CRON
chmod 0644 /etc/cron.d/db-backup

# ── Cleanup ──────────────────────────────────────────────────────────────────
apt-get autoremove -y
apt-get clean
rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*
