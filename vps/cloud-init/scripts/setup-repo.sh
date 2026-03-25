#!/usr/bin/env bash
# =============================================================================
# setup-repo.sh — clone the website repository on first boot.
#
# Tries HTTPS first (works through proxies), falls back to SSH via deploy key.
# After cloning, sets the origin URL back to SSH for ongoing git operations.
#
# Placeholders __GHCR_TOKEN__ and __GIT_BRANCH__ are substituted by render.py
# before this script is embedded in the cloud-config.
#
# Run by cloud-init runcmd on first boot.
# =============================================================================
set -euxo pipefail

REPO_DIR="/src/website"

if [ -d "${REPO_DIR}/.git" ]; then
  echo "Repository already cloned at ${REPO_DIR}, skipping."
  exit 0
fi

CLONE_TARGET="/tmp/website-repo"
ORIGIN_SSH='git@github.com:ESA-Blueshell/website.git'
ORIGIN_HTTPS='https://x-access-token:__GHCR_TOKEN__@github.com/ESA-Blueshell/website.git'
GIT_BRANCH='__GIT_BRANCH__'
GIT_PROXY="${https_proxy:-${HTTPS_PROXY:-}}"

rm -rf "${CLONE_TARGET}"

clone_https() {
  local extra_args=()
  if [ -n "${GIT_PROXY}" ]; then
    extra_args+=(-c "http.proxy=${GIT_PROXY}" -c "https.proxy=${GIT_PROXY}")
  fi
  su -m -s /bin/bash website -c \
    "HOME=${REPO_DIR} GIT_TERMINAL_PROMPT=0 git ${extra_args[*]+"${extra_args[*]}"} clone --branch '${GIT_BRANCH}' '${ORIGIN_HTTPS}' '${CLONE_TARGET}'"
}

clone_ssh() {
  su -m -s /bin/bash website -c \
    "HOME=${REPO_DIR} GIT_SSH_COMMAND='ssh -o ConnectTimeout=10 -o StrictHostKeyChecking=accept-new' git clone --branch '${GIT_BRANCH}' '${ORIGIN_SSH}' '${CLONE_TARGET}'"
}

if ! clone_https; then
  clone_ssh
fi

# Restore SSH remote for ongoing operations via deploy key
su -m -s /bin/bash website -c \
  "HOME=${REPO_DIR} git -C '${CLONE_TARGET}' remote set-url origin '${ORIGIN_SSH}'"

cp -a "${CLONE_TARGET}/." "${REPO_DIR}/"
rm -rf "${CLONE_TARGET}"

chown -R website:website "${REPO_DIR}"
chmod 750 "${REPO_DIR}"
find "${REPO_DIR}" -name '*.env' -exec chmod 640 {} +
install -d -m 0700 -o website -g website "${REPO_DIR}/.ssh"
test ! -f "${REPO_DIR}/.ssh/authorized_keys" || chmod 600 "${REPO_DIR}/.ssh/authorized_keys"
test ! -f "${REPO_DIR}/.ssh/config" || chmod 600 "${REPO_DIR}/.ssh/config"
test ! -f "${REPO_DIR}/.ssh/github-deploy-key" || chmod 600 "${REPO_DIR}/.ssh/github-deploy-key"
