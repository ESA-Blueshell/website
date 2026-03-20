#!/usr/bin/env bash
# =============================================================================
# System provisioning script — run by cloud-init on first boot.
# Installs Docker, hardens SSH, configures UFW, sets up users, backup scripts,
# and the website CLI.  Instance-specific config (keys, secrets, GHCR login)
# is injected by cloud-init before this script runs.
# =============================================================================
set -euxo pipefail

export DEBIAN_FRONTEND=noninteractive

# ── Docker APT repo ──────────────────────────────────────────────────────────
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/debian/gpg \
  | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
chmod 644 /etc/apt/keyrings/docker.gpg

cat > /etc/apt/sources.list.d/docker.list <<'REPO'
deb [arch=amd64 signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/debian trixie stable
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

# ── Infisical CLI ──────────────────────────────────────────────────────────
curl -1sLf 'https://dl.cloudsmith.io/public/infisical/infisical-cli/setup.deb.sh' | bash
apt-get update && apt-get install -y infisical

# ── Groups ───────────────────────────────────────────────────────────────────
groupadd -f website
groupadd -f backup

# ── Users ────────────────────────────────────────────────────────────────────
# admin: sudo WITH password required
useradd -m -s /bin/bash -G sudo,docker,website,backup admin || true

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
mkdir -p /src/website/.config/systemd/user/docker.socket.d
cat > /src/website/.config/systemd/user/docker.socket.d/override.conf <<'UNIT'
[Socket]
SocketMode=0660
UNIT
chown -R website:website /src/website/.config

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

# Run a docker command via the website user's rootless socket (runs as root, accesses rootless)
rdocker() {
  sudo -u website -H env \
    XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR}" \
    DOCKER_HOST="unix://${ROOTLESS_SOCK}" \
    docker "$@"
}

# Find the first running container whose name matches a pattern
find_container() {
  rdocker ps --filter "name=${1}" --format "{{.ID}}" | head -1
}

# Ensure the user service is up (in case of reboot race)
sudo -u website -H bash -lc 'systemctl --user start docker' || true
sleep 1

DB_CID="$(find_container "website_db")"
if [[ -z "${DB_CID}" ]]; then
  echo "Error: website_db container not found in Swarm stack." >&2
  exit 1
fi

TIMESTAMP="$(date +'%Y%m%d-%H%M%S')"
mkdir -p /src/backups/db

# 1a) MariaDB dump
MARIADB_OUT="/src/backups/db/db-backup-${TIMESTAMP}.sql.gz"
rdocker exec -i "${DB_CID}" \
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
  rdocker exec -i "${LISTMONK_DB_CID}" \
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
BACKUP
chmod 0750 /usr/local/bin/db-backup
chown root:backup /usr/local/bin/db-backup

# ── Website management CLI ───────────────────────────────────────────────────
cat > /usr/local/bin/website <<'CLI'
#!/usr/bin/env bash
# =============================================================================
# website — manage Docker Swarm stacks for all environments.
#
# Designed to run AS the 'website' user, which has DOCKER_HOST set in its
# shell profile pointing at the rootless Docker socket.
#
# Admin users (admin) run website commands via:
#   su -l website -c "website up [env]"
#
# The 'backup' subcommand must be run as root (or via sudo by admin);
# it is also invoked automatically by the daily cron job.
# =============================================================================
set -euo pipefail
REPO="/src/website"

# Resolve environment name → IMAGE_TAG + STACK_NAME
_env_map() {
  case "${1:-production}" in
    prod|production)  echo "latest website" ;;
    stg|staging)      echo "staging website-staging" ;;
    dev|development)  echo "dev website-dev" ;;
    *) echo "ERROR: unknown environment '${1}' (use: production, staging, development)" >&2; exit 1 ;;
  esac
}

usage() { cat <<EOF
website — manage Docker Swarm stacks for all environments

Usage:
  website status  [env]            Show Swarm service status
  website up      [env]            Deploy (or update) the stack
  website down    [env]            Remove the stack
  website logs    [env] <service>  Tail service logs
  website pull                     git pull + redeploy production  (used by CI)
  website backup                   Run DB + storage backup (root only)
  website shell                    Open a bash shell in ${REPO}
  website services [env]           Alias for status

  website infra up                 Deploy (or update) the infra stack
  website infra down               Remove the infra stack
  website infra logs <service>     Tail infra service logs
  website infra status             Show infra Swarm service status

  website help                     Show this help

Environment (default: production):
  production  →  IMAGE_TAG=latest   STACK=website            DOMAIN=v2.esa-blueshell.nl
  staging     →  IMAGE_TAG=staging  STACK=website-staging    DOMAIN=staging.v2.esa-blueshell.nl
  development →  IMAGE_TAG=dev      STACK=website-dev        DOMAIN=dev.v2.esa-blueshell.nl

Examples (run as website user or via: su -l website -c "website up"):
  website up                     # redeploy production
  website up staging             # redeploy staging
  website logs staging api       # tail staging API logs
  website down development       # remove the dev stack
  website infra up               # deploy/update Traefik + monitoring
  website infra logs grafana     # tail Grafana logs
EOF
}

cmd="${1:-help}"
shift || true

case "${cmd}" in
  status|services)
    read -r _tag _stack <<< "$(_env_map "${1:-production}")"
    docker stack services "${_stack}" ;;

  up)
    read -r _tag _stack <<< "$(_env_map "${1:-production}")"
    cd "${REPO}"
    IMAGE_TAG="${_tag}" bash services/deploy.sh "${_stack}" ;;

  down)
    read -r _tag _stack <<< "$(_env_map "${1:-production}")"
    docker stack rm "${_stack}" ;;

  logs)
    read -r _tag _stack <<< "$(_env_map "${1:-production}")"
    shift || true
    svc="${1:-}"
    if [[ -z "${svc}" ]]; then
      echo "Usage: website logs [env] <service>" >&2
      echo "Services: api, frontend, db, listmonk, listmonk-db, listmonk-setup" >&2
      exit 1
    fi
    docker service logs -f --tail=200 "${_stack}_${svc}" ;;

  infra)
    subcmd="${1:-status}"
    shift || true
    case "${subcmd}" in
      up)
        cd "${REPO}"
        bash infra/deploy.sh ;;
      down)
        docker stack rm infra ;;
      status)
        docker stack services infra ;;
      logs)
        svc="${1:-traefik}"
        docker service logs -f --tail=200 "infra_${svc}" ;;
      *)
        echo "Usage: website infra <up|down|status|logs [service]>" >&2
        exit 1 ;;
    esac ;;

  backup)
    # Requires root — invoked by cron or: sudo website backup
    /usr/local/bin/db-backup ;;

  pull)
    # Production-only: git pull from main then redeploy
    cd "${REPO}"
    git fetch --prune || true
    git reset --hard origin/main || true
    git pull --ff-only || true
    IMAGE_TAG='latest' bash services/deploy.sh 'website' ;;

  shell)
    cd "${REPO}" && exec bash ;;

  help|*)
    usage ;;
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

# ── Logrotate: db-backup log ─────────────────────────────────────────────────
cat > /etc/logrotate.d/db-backup <<'LOGROTATE'
/var/log/db-backup.log {
    weekly
    rotate 8
    compress
    missingok
    notifempty
    create 664 root backup
}
LOGROTATE

# ── Auto-deploy on boot (safety net) ───────────────────────────────────────
# Docker Swarm stacks survive reboots natively (rootless Docker auto-starts
# via loginctl enable-linger). This systemd oneshot verifies stacks are
# running after boot and redeploys if needed.
WEBSITE_UID="$(id -u website)"
cat > /etc/systemd/system/website-deploy.service <<UNIT
[Unit]
Description=Ensure website Swarm stacks are deployed
After=network-online.target user@${WEBSITE_UID}.service
Wants=network-online.target

[Service]
Type=oneshot
User=website
ExecStartPre=/bin/bash -lc 'until docker info >/dev/null 2>&1; do sleep 2; done'
ExecStart=/bin/bash -lc 'cd /src/website && website infra up && website up'
RemainAfterExit=yes
Environment=HOME=/src/website

[Install]
WantedBy=multi-user.target
UNIT
systemctl daemon-reload
systemctl enable website-deploy.service

# ── Cleanup ──────────────────────────────────────────────────────────────────
apt-get autoremove -y
apt-get clean
rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*
