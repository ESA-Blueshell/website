#!/usr/bin/env bash
# =============================================================================
# setup-directories.sh — create the directory structure for the website and
#                         backup storage on first boot.
#
# Directories:
#   /src/website/          owned by website:website (application root)
#   /src/backups/{db,env,storage,mailserver/}  owned by root:backup (2770)
#
# Run by provision.sh on first boot.
# =============================================================================
set -euxo pipefail

# ── Application root ──────────────────────────────────────────────────────────
mkdir -p /src/website
chown website:website /src/website
chmod 750 /src/website

touch /src/website/.profile
chown website:website /src/website/.profile

# ── Backup storage ────────────────────────────────────────────────────────────
mkdir -p /src/backups/db
mkdir -p /src/backups/env
mkdir -p /src/backups/storage
mkdir -p /src/backups/mailserver/mail-data
mkdir -p /src/backups/mailserver/config
chown -R root:backup /src/backups
chmod -R 2770 /src/backups

# ── Backup log ────────────────────────────────────────────────────────────────
touch /var/log/db-backup.log
chown root:backup /var/log/db-backup.log
chmod 664 /var/log/db-backup.log
