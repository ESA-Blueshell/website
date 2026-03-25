#!/usr/bin/env bash
# =============================================================================
# setup-ghcr.sh — log the website user into GitHub Container Registry.
#
# Placeholders __GHCR_TOKEN__ and __GHCR_USERNAME__ are substituted by
# render.py before this script is embedded in the cloud-config.
#
# Run by cloud-init runcmd on first boot.
# =============================================================================
set -euxo pipefail

su -m -s /bin/bash website -c \
  "export HOME=/src/website; printf '__GHCR_TOKEN__' | docker login ghcr.io -u '__GHCR_USERNAME__' --password-stdin"
