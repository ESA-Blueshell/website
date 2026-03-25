#!/usr/bin/env bash
# =============================================================================
# setup-firewall.sh — configure UFW firewall rules.
#
# Opens only the required ports and denies everything else:
#   - 2222/tcp  SSH (non-standard port)
#   - 80/tcp    HTTP (Traefik ingress, redirects to HTTPS)
#   - 443/tcp   HTTPS (Traefik TLS termination)
#
# Run by provision.sh on first boot.
# =============================================================================
set -euxo pipefail

ufw default deny incoming
ufw default allow outgoing
ufw allow 2222/tcp
ufw allow 80/tcp
ufw allow 443/tcp
ufw --force enable
