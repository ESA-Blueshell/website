#!/usr/bin/env bash
# =============================================================================
# setup-infisical.sh — install the Infisical CLI (best-effort).
#
# Downloads the setup script from Cloudsmith and installs the Infisical CLI
# package. If anything fails, cleans up and continues — Infisical is optional.
#
# Run by provision.sh on first boot.
# =============================================================================
set -euxo pipefail

export DEBIAN_FRONTEND=noninteractive

cleanup_infisical_repo() {
  find /etc/apt/sources.list.d -maxdepth 1 -type f \
    \( -iname '*infisical*' -o -iname '*cloudsmith*' \) -delete || true
  find /etc/apt/keyrings /usr/share/keyrings -maxdepth 1 -type f \
    \( -iname '*infisical*' -o -iname '*cloudsmith*' \) -delete 2>/dev/null || true
}

install_infisical() {
  local setup_sh
  setup_sh=$(curl -1sLf 'https://dl.cloudsmith.io/public/infisical/infisical-cli/setup.deb.sh') \
    || { echo "Failed to download Infisical setup script (curl error $?)" >&2; return 1; }
  echo "${setup_sh}" | bash || return 1
  apt-get install -y infisical
}

if ! install_infisical; then
  echo "WARNING: Infisical CLI install failed; continuing without it." >&2
  cleanup_infisical_repo
  apt-get update || true
fi
