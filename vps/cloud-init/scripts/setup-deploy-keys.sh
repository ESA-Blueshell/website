#!/usr/bin/env bash
# =============================================================================
# setup-deploy-keys.sh — distribute the GitHub deploy key to all service users.
#
# Copies /etc/deploy-keys/github-deploy-key into each user's ~/.ssh/ and writes
# an SSH config that routes github.com through ssh.github.com on port 443.
#
# Run by cloud-init runcmd on first boot.
# =============================================================================
set -euxo pipefail

DEPLOY_KEY="/etc/deploy-keys/github-deploy-key"

if [[ ! -f "${DEPLOY_KEY}" ]]; then
  echo "Error: ${DEPLOY_KEY} not found." >&2
  exit 1
fi

for user in website admin; do
  home="$(getent passwd "${user}" | cut -d: -f6)"
  install -d -m 0700 -o "${user}" -g "${user}" "${home}/.ssh"
  cp "${DEPLOY_KEY}" "${home}/.ssh/github-deploy-key"
  cat > "${home}/.ssh/config" <<SSHEOF
Host github.com
  HostName ssh.github.com
  Port 443
  User git
  IdentityFile ${home}/.ssh/github-deploy-key
  IdentitiesOnly yes
  StrictHostKeyChecking accept-new
SSHEOF
  chown -R "${user}:${user}" "${home}/.ssh"
  chmod 700 "${home}/.ssh"
  find "${home}/.ssh" -type f -exec chmod 600 {} +
done
