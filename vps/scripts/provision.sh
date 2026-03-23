#!/usr/bin/env bash
# =============================================================================
# System provisioning script — run by cloud-init on first boot.
#
# Only contains imperative work that cloud-init cannot handle declaratively:
#   - Infisical CLI install (piped curl)
#   - UFW firewall configuration
#   - Docker daemon enablement
#   - Directory structure creation
#   - Systemd service registration
#   - APT cleanup
#
# Everything else is handled by cloud-init natively:
#   - Package installation (packages: + apt: sources)
#   - User/group creation (users: + groups:)
#   - SSH config, cron, logrotate, systemd unit (write_files:)
#   - Backup script and website CLI (write_files:)
# =============================================================================
set -euxo pipefail

export DEBIAN_FRONTEND=noninteractive

# ── Infisical CLI (piped setup script — cannot be done via cloud-init apt:) ──
curl -1sLf 'https://dl.cloudsmith.io/public/infisical/infisical-cli/setup.deb.sh' | bash
apt-get update && apt-get install -y infisical

# ── UFW firewall ─────────────────────────────────────────────────────────────
ufw default deny incoming
ufw default allow outgoing
ufw allow 2222/tcp
ufw allow 80/tcp
ufw allow 443/tcp
yes | ufw enable || true

# ── Ensure rootful Docker daemon is enabled and running ──────────────────────
systemctl enable --now docker.service

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

# ── Website user profile ────────────────────────────────────────────────────
touch /src/website/.profile
chown website:website /src/website/.profile

# ── Enable systemd services (units placed by cloud-init write_files) ────────
systemctl daemon-reload
systemctl enable website-deploy.service

# ── Cleanup ──────────────────────────────────────────────────────────────────
apt-get autoremove -y
apt-get clean
rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*
