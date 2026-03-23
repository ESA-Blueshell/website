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
cleanup_infisical_repo() {
  find /etc/apt/sources.list.d -maxdepth 1 -type f \
    \( -iname '*infisical*' -o -iname '*cloudsmith*' \) -delete || true
  find /etc/apt/keyrings /usr/share/keyrings -maxdepth 1 -type f \
    \( -iname '*infisical*' -o -iname '*cloudsmith*' \) -delete 2>/dev/null || true
}

install_infisical() {
  curl -1sLf 'https://dl.cloudsmith.io/public/infisical/infisical-cli/setup.deb.sh' | bash
  apt-get update
  apt-get install -y infisical
}

if ! install_infisical; then
  echo "WARNING: Infisical CLI install failed; continuing without it." >&2
  cleanup_infisical_repo
  apt-get update || true
fi

configure_docker_proxy() {
  local vars=(http_proxy https_proxy HTTP_PROXY HTTPS_PROXY no_proxy NO_PROXY)
  local has_proxy=0
  local var value

  for var in "${vars[@]}"; do
    value="${!var:-}"
    if [[ -n "${value}" ]]; then
      has_proxy=1
      break
    fi
  done

  if [[ "${has_proxy}" -eq 0 ]]; then
    return
  fi

  install -d -m 0755 /etc/systemd/system/docker.service.d
  {
    echo "[Service]"
    for var in "${vars[@]}"; do
      value="${!var:-}"
      if [[ -n "${value}" ]]; then
        printf 'Environment="%s=%s"\n' "${var}" "${value}"
      fi
    done
  } > /etc/systemd/system/docker.service.d/proxy.conf
}

# ── UFW firewall ─────────────────────────────────────────────────────────────
ufw default deny incoming
ufw default allow outgoing
ufw allow 2222/tcp
ufw allow 80/tcp
ufw allow 443/tcp
ufw --force enable

# ── Ensure rootful Docker daemon is enabled and running ──────────────────────
configure_docker_proxy
systemctl daemon-reload
systemctl enable --now docker.service

# ── Directory structure ──────────────────────────────────────────────────────
mkdir -p /src/website
chown website:website /src/website
chmod 750 /src/website

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
systemctl enable website-deploy.service

# ── Cleanup ──────────────────────────────────────────────────────────────────
apt-get autoremove -y
apt-get clean
rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*
