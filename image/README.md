# Image & VPS Provisioning

This folder contains everything needed to provision and operate a production
server (Contabo VPS) for the ESA Blueshell website.

## Overview

The provisioning flow has two paths — pick the one that fits your situation:

| Path | When to use | Files involved |
|------|-------------|----------------|
| **Standalone cloud-init** (recommended) | New Contabo VPS, no pre-baked image needed | `cloud-init/`, `ops/create-vps.sh` |
| **Packer image** (optional) | Pre-bake a QEMU image to speed up future VPS installs | `packer/`, `cloud-init/` |

---

## Directory Structure

```
image/
├── .env.example              Configuration template (copy to .env)
├── cloud-init/
│   ├── cloud-config.template.yaml            Packer flow template
│   ├── cloud-config-standalone.template.yaml Contabo cloud-init-only template
│   └── render.sh             Renders a template → rendered YAML with real values
├── ops/
│   ├── create-vps.sh         Provision a new Contabo VPS via cntb CLI
│   ├── backup.sh             Pull a full backup from the server to ./backup/
│   └── restore.sh            Push a backup from ./backup/ to the server
├── packer/
│   ├── image.pkr.hcl         Packer build definition (optional, QEMU)
│   └── seed/                 Packer cloud-init seed (meta-data + user-data)
└── scripts/
    └── provision.sh          System provisioning script (baked into image/cloud-init)
```

---

## Quick Start: Provision a New VPS

### 1. Configure

```bash
cd image
cp .env.example .env
# Edit .env with your values — see .env.example for documentation
```

Required variables:
- `BLUESHELL_PASSWORD`, `ROOT_PASSWORD`, `WEBSITE_PASSWORD` — console/portal login (SSH is keys-only)
- `GHCR_USERNAME`, `GHCR_TOKEN` — GitHub PAT (classic, `read:packages` scope only)
- `CLIENT_ID`, `CLIENT_SECRET`, `CONTABO_API_USER`, `CONTABO_API_PASSWORD` — Contabo OAuth2
- `CONTABO_PRODUCT_ID` — e.g. `V22` (run `cntb get products` to list options)
- `CONTABO_REGION` — `EUROPE` | `US_CENTRAL` | `US_EAST` | `US_WEST` | `ASIA` | `AUSTRALIA`
- `CONTABO_SSH_KEY_ID` — integer ID of your uploaded public key (see step 2)
- `REMOTE_HOST` — IP/hostname of the server (fill in after VPS is created)

### 2. Upload SSH key (first time only)

`render.sh` generates two ed25519 key pairs in `~/.ssh/`:
- `~/.ssh/blueshell-admin` — for the `blueshell` admin user
- `~/.ssh/blueshell-website` — for the `website` application user

Run `render.sh` once to generate the keys, then upload the admin key:
```bash
./cloud-init/render.sh --standalone  # generates keys if absent
cntb create secret \
  --oauth2ClientId     "${CLIENT_ID}" \
  --oauth2ClientSecret "${CLIENT_SECRET}" \
  --oauth2User         "${CONTABO_API_USER}" \
  --oauth2Password     "${CONTABO_API_PASSWORD}" \
  --name               blueshell-admin \
  --value              "$(cat ~/.ssh/blueshell-admin.pub)" \
  --secretType         ssh
# Note the returned ID and set CONTABO_SSH_KEY_ID in .env
cntb get secrets --secretType ssh
```

### 3. Render the cloud-init config

```bash
./cloud-init/render.sh --standalone
# Produces: cloud-init/cloud-config-standalone.yaml
```

### 4. Create the VPS

```bash
./ops/create-vps.sh
# Reads from .env and cloud-init/cloud-config-standalone.yaml
# Calls: cntb create instance --imageId debian-12 --userDataFile ...
```

The VPS will take **8–12 minutes** to fully provision. Cloud-init:
1. Installs packages (Docker CE, cron, MariaDB client, etc.)
2. Hardens SSH (port 2222, keys only, no password auth)
3. Configures UFW (allow 80, 443, 2222)
4. Sets up the `website` user with rootless Docker
5. Clones the repo to `/src/website`
6. Logs into GHCR for pulling private images

### 5. Set REMOTE_HOST and verify

Once the VPS is created, find the assigned IP in the Contabo portal and set it
in `.env`:
```bash
REMOTE_HOST=<assigned-IP>
```

Then verify cloud-init completed:
```bash
ssh -p 2222 -i ~/.ssh/blueshell-admin blueshell@<IP>
sudo cat /var/log/cloud-init-output.log | tail -30
```

### 6. Place env files and deploy

Either restore from an existing backup (recommended when migrating):
```bash
./ops/restore.sh          # reads REMOTE_HOST from .env
```

Or create fresh env files on the server (SSH in as the `website` user):
```bash
ssh -p 2222 -i ~/.ssh/blueshell-website website@<IP>
nano /src/website/services/api/.db.env
nano /src/website/services/api/.api.env
nano /src/website/services/listmonk/.listmonk.env
```

Then deploy:
```bash
website up
```

See [deployment/README.md](../deployment/README.md) for full deployment
documentation including SSL certificate setup.

---

## Backup & Restore

### Pull a backup from the server

```bash
./ops/backup.sh [remote_host]
```

This triggers a fresh server-side backup, then downloads everything to
`./backup/`:
- `backup/db/db-backup-*.sql.gz` — MariaDB dumps (7-day retention on server)
- `backup/db/listmonk-backup-*.sql.gz` — Listmonk PostgreSQL dumps
- `backup/env/services/{api,listmonk,mailserver}/` — env files (contains secrets — handle carefully)
- `backup/storage/` — file uploads (incremental rsync)
- `backup/mailserver/` — mail data + config (if mailserver is running)

### Push a backup to a server

```bash
./ops/restore.sh [remote_host]
```

Pushes the most recent local backup to the server:
- Copies all DB dumps to `/src/backups/db/`
- Copies env files to their service directories
- Syncs storage and mailserver data (incremental)
- Places the latest MariaDB dump at `services/api/1_dump.sql` so MariaDB
  auto-imports it on first container start

> **Listmonk PostgreSQL**: not automatically restored. After `website up`,
> restore manually:
> ```bash
> gunzip -c backup/db/listmonk-backup-YYYYMMDD-HHMMSS.sql.gz | \
>   docker exec -i $(docker ps -q -f name=website_listmonk-db) \
>   psql -U listmonk listmonk
> ```

### Automated backups

The server runs a daily backup at 03:00 via cron (`/etc/cron.d/db-backup`).
The backup script (`/usr/local/bin/db-backup`) backs up:
- MariaDB (mysqldump, gzipped)
- Listmonk PostgreSQL (pg_dump, gzipped, if running)
- Env files snapshot
- File storage (incremental rsync)
- Mailserver data (incremental rsync, if present)

Old DB dumps are pruned after 7 days. All backup files are owned by
`root:backup` with `0640` permissions.

---

## `provision.sh` — What It Does

The provisioning script is baked into the cloud-init config and runs on first
boot. It sets up the server image:

1. **Docker CE** — installs Docker + docker-compose-plugin via the official APT repo
2. **System packages** — openssh, cron, mariadb-client, rsync, ufw, etc.
3. **Users & groups:**
   - `website` (no sudo, rootless Docker, home at `/src/website`)
   - `blueshell` (admin, sudo with password, full `docker` group access)
   - `backup` group (owns backup dirs)
4. **SSH hardening** — port 2222, keys-only, no password auth
5. **UFW firewall** — deny incoming by default; allow 2222, 80, 443
6. **Unprivileged ports** — `net.ipv4.ip_unprivileged_port_start=80` so rootless Docker can bind port 80/443
7. **Directory structure** — `/src/website`, `/src/backups/{db,env,storage,mailserver}`
8. **`db-backup` script** — `/usr/local/bin/db-backup` (see Backup section)
9. **`website` CLI** — `/usr/local/bin/website` (see below)
10. **Cron job** — daily backup at 03:00

---

## `website` CLI

A convenience wrapper at `/usr/local/bin/website`, designed to run **as the
`website` user**:

```
website status  [env]           Show Compose service status
website up      [env]           Deploy (or redeploy) the project via deployment/deploy.sh
website down    [env]           Stop and remove the project
website logs    [env] <service> Tail service logs  (e.g. website logs api)
website pull                    git pull + redeploy production  (used by CI)
website backup                  Run DB + storage backup (root only)
website shell                   Open a bash shell in /src/website
website services [env]          Alias for status
website help                    Show help
```

Environment defaults to `production`; pass `staging` or `development` to target
other Compose projects on the same host.

Run directly as the `website` user:
```bash
website up
website up staging
```

Admin users (`blueshell`) delegate to the website user via:
```bash
su -l website -c "website up"
su -l website -c "website up staging"
```

Docker commands use the `website` user's rootless socket at
`/run/user/<uid>/docker.sock` (set in the user's shell profile).

---

## Security Notes

- SSH is **keys-only** on port **2222**; password authentication is disabled.
- The `website` user runs Docker in **rootless** mode — the Docker daemon itself
  runs without root privileges.
- Backup files are owned by `root:backup` with `chmod 2770` on directories;
  the `website` user cannot read or delete backups.
- The `blueshell` admin user requires a **password for sudo** (not passwordless).
- GHCR credentials are embedded in the cloud-init config for the initial image
  pull; they are stored in the `website` user's Docker credential store
  (not a plaintext file).

---

## Packer (Optional)

If you want to pre-bake a QEMU image to speed up future server provisioning:

```bash
cd image
source .env
packer build packer/image.pkr.hcl
./cloud-init/render.sh    # renders cloud-config.yaml (Packer flow)
```

The Packer flow bakes the OS-level setup (Docker, users, SSH hardening) into a
snapshot image. Instance-specific items (SSH keys, passwords, GHCR login) are
still injected via cloud-init at first boot.

See `packer/image.pkr.hcl` for build configuration.
