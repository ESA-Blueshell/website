# Deployment

This folder contains everything needed to deploy the ESA Blueshell website to
a production server using **Docker Compose** with **Traefik** as the edge proxy.

## Contents

| File | Purpose |
|------|---------|
| `deploy.sh` | Deploy script — sources env files, ensures Traefik is up, runs `docker compose up` |
| `../infra/` | Traefik edge proxy — deployed once, shared across all environments |

---

## Architecture

```
Internet
    │
   80/443
    │
 ┌──▼───────────────────────────────────────┐
 │  Traefik  (project: infra)               │  TLS termination + HTTP→HTTPS redirect
 │  reads Docker labels via socket          │  Let's Encrypt ACME (HTTP-01)
 └──┬───────────────────────────────────────┘
    │  traefik-public network
    ├─────────────────────────────────────────────────────────────────┐
    │ project: website (production)           │ project: website-staging│
    │   esa-blueshell.nl → frontend           │   staging.esa-blueshell.nl → frontend
    │   esa-blueshell.nl/api → api            │   staging.esa-blueshell.nl/api → api
    │   listmonk.esa-blueshell.nl → listmonk  │   listmonk.staging.esa-blueshell.nl
    └─────────────────────────────────────────┘─────────────────────────
```

Each environment is an independent Compose project with its own containers and
volumes. Traefik routes traffic to the correct project based on the hostname.

---

## Environments

Three environments can run **simultaneously** on the same server.

| Environment | Image tag | Project name | Domain |
|-------------|-----------|--------------|--------|
| **production** | `:latest` | `website` | `esa-blueshell.nl` |
| **staging** | `:staging` | `website-staging` | `staging.esa-blueshell.nl` |
| **development** | `:dev` | `website-dev` | `dev.esa-blueshell.nl` |

### Triggering a manual deploy

Go to **GitHub → Actions → Manual Deploy → Run workflow**:
1. Select the **branch** you want to deploy (can be any branch)
2. Select the **environment** (`development`, `staging`, or `production`)
3. Click **Run workflow**

The workflow will:
- Build the API and frontend images from that branch
- Push them to GHCR with the environment's tag (`dev`, `staging`, or `latest`)
- SSH to the server and run `website up <env>` (as the `website` user)

### GitHub Environment secrets

Each environment (Settings → Environments) needs:

| Secret | Description |
|--------|-------------|
| `SSH_KEY` | Private SSH key for the `website` user |
| `IP_ADDRESS` | Server IP or hostname |
| `PORT` | SSH port (`2222`) |
| `DEPLOY_USER` | `website` (the application user) |

Optional environment variable:
| Variable | Description |
|----------|-------------|
| `DEPLOY_URL` | URL shown in GitHub Deployments (e.g. `https://esa-blueshell.nl`) |

### On-server environment management

Run as the `website` user (SSH in, or `su -l website` as blueshell admin):

```bash
# Deploy specific environments
website up                    # redeploy production
website up staging            # redeploy staging
website up development        # redeploy development

# View status
website status                # production status
website status staging

# Tail logs
website logs api              # production API logs
website logs staging api
website logs development nginx

# Stop an environment
website down staging
website down development
```

Admin users (blueshell) run website commands via:
```bash
su -l website -c "website up staging"
su -l website -c "website logs api"
```

---

## Prerequisites

- A provisioned server (see [image/README.md](../image/README.md))
- DNS records pointing to the server:
  - `esa-blueshell.nl` → server IP
  - `staging.esa-blueshell.nl` → server IP
  - `dev.esa-blueshell.nl` → server IP
  - `listmonk.esa-blueshell.nl` → server IP
- Env files in place (see [Configuration](#configuration) below)
- Images pushed to GHCR (done by CI on every `main` push)

---

## Configuration

Before the first deploy, create the following env files on the server:

### `services/api/.db.env`
```shell
MYSQL_ROOT_PASSWORD=<strong-password>
MYSQL_DATABASE=blueshell
MYSQL_HOST=db
MYSQL_PORT=3306
MYSQL_USER=blueshell
MYSQL_PASSWORD=<strong-password>
```

### `services/api/.api.env`
```shell
# Application
JWT_SECRET=<long-random-secret>
STORAGE_LOCATION=/home/storage

# SMTP relay (optional — leave blank to disable)
SMTP_HOST=
SMTP_PORT=587
SMTP_USERNAME=
SMTP_PASSWORD=
SMTP_USE_SSL=false
SMTP_USE_TLS=true

# Brevo (email marketing)
BREVO_API_KEY=

# Google Calendar
GOOGLE_CALENDAR_ID=
GOOGLE_CALENDAR_CLIENT_ID=
GOOGLE_CALENDAR_CLIENT_EMAIL=
GOOGLE_CALENDAR_PRIVATE_KEY_PKCS8=
GOOGLE_CALENDAR_PRIVATE_KEY_ID=

# Payment (Mollie)
MOLLIE_API_KEY=

# Social media
FACEBOOK_PAGE_ID=
FACEBOOK_ACCESS_TOKEN=
X_API_KEY=
X_API_SECRET=
X_ACCESS_TOKEN=
X_ACCESS_SECRET=
```

### `services/listmonk/.listmonk.env`
```shell
LISTMONK_DB_PASSWORD=<strong-password>
LISTMONK_ADMIN_USERNAME=listmonk
LISTMONK_ADMIN_PASSWORD=<strong-password>
LISTMONK_ADMIN_EMAIL=admin@esa-blueshell.nl
LISTMONK_ADMIN_API_USER=api

# SMTP outbound (leave LISTMONK_SMTP_HOST blank to skip)
LISTMONK_SMTP_HOST=
LISTMONK_SMTP_PORT=587
LISTMONK_SMTP_USERNAME=
LISTMONK_SMTP_PASSWORD=

# Bounce mailbox (leave ENABLED=false to skip)
LISTMONK_BOUNCE_MAILBOX_ENABLED=false
LISTMONK_BOUNCE_MAILBOX_HOST=
LISTMONK_BOUNCE_MAILBOX_USERNAME=
LISTMONK_BOUNCE_MAILBOX_PASSWORD=
```

Copy these to the server with:
```bash
./image/ops/restore.sh   # pushes a full backup incl. env files
```
or manually via `scp`/`ssh`.

---

## First-time Deployment

### 1. Provision a VPS

See [image/README.md](../image/README.md) for full instructions. After first boot
the server will have:
- Docker (rootless) running as `website`
- Repo cloned to `/src/website`
- `website` CLI available at `/usr/local/bin/website`

### 2. Configure DNS

Point all required hostnames at the server IP before deploying. Traefik uses
HTTP-01 ACME to obtain certificates — DNS must resolve first.

### 3. Place env files

SSH in as `website` user and create the env files:
```bash
ssh -p 2222 -i ~/.ssh/blueshell-website website@<IP>
nano /src/website/services/api/.db.env
nano /src/website/services/api/.api.env
nano /src/website/services/listmonk/.listmonk.env
```

Or use `restore.sh` if migrating from an existing server.

### 4. Deploy

```bash
website up
```

This calls `deployment/deploy.sh`, which:
1. Loads env files
2. Ensures Traefik (infra) is running
3. Deploys the production project

Traefik obtains Let's Encrypt certificates automatically on first HTTPS request.
No manual certbot step needed.

---

## Day-to-day Operations

Run all commands as the `website` user:

```bash
website status          # show Compose service status
website logs api        # tail API logs
website logs nginx      # tail Nginx logs (Traefik)
website pull            # git pull + redeploy (same as CI deploy step)
website up              # (re-)deploy the project
website down            # stop all services
website shell           # open shell in /src/website
```

Backup (root only — run via cron or `sudo website backup` as blueshell):
```bash
sudo website backup     # run DB + storage backup now
```

### View container status

```bash
docker compose --project-name website ps
docker compose --project-name website logs api
```

### Traefik (infra project)

```bash
docker compose --project-name infra ps
docker compose --project-name infra logs traefik
```

### Redeploy after image push

CI does this automatically on every `main` push. Manually:
```bash
website pull
```

---

## Stack Topology

```
Internet
    │
   80/443
    │
 ┌──▼───────────────────────────┐
 │  traefik  (infra project)    │  TLS, HTTP→HTTPS, ACME, routing
 └──┬───────────────────────────┘
    │  traefik-public network
    │
 ┌──▼──────────────────────────────────────────┐
 │  website / website-staging / website-dev    │
 │                                             │
 │  ┌──────────┐   ┌───────────┐               │
 │  │ frontend │   │    api    │  frontback     │
 │  └──────────┘   └──┬────────┘               │
 │                    │  database              │
 │            ┌───────┴───────────────┐        │
 │            │                       │        │
 │        ┌───▼────┐         ┌────────▼──────┐ │
 │        │   db   │         │  listmonk-db  │ │
 │        └────────┘         └───────────────┘ │
 │                                ▲            │
 │                           ┌────┴──────────┐ │
 │                           │   listmonk    │ │
 │                           └───────────────┘ │
 │                                ▲ secrets    │
 │                           ┌────┴──────────┐ │
 │                           │listmonk-setup │ │
 │                           └───────────────┘ │
 └─────────────────────────────────────────────┘
```

**Volumes** (prefixed with project name, e.g. `website_`):
- `db_data` — MariaDB data
- `listmonk_data` — Listmonk PostgreSQL data
- `listmonk_secrets` — API token written by setup, read by api

**Infra volumes** (fixed names):
- `traefik_letsencrypt` — Let's Encrypt certificates (shared, survives infra redeploys)

---

## Troubleshooting

**Service keeps restarting:**
```bash
docker compose --project-name website ps
docker compose --project-name website logs <name>
```

**api won't start (db not ready):**
The api service has a healthcheck with up to 20 retries. Check DB logs:
```bash
docker compose --project-name website logs db
```

**Listmonk setup fails:**
```bash
docker compose --project-name website logs listmonk-setup
```

**Certificate not issued / HTTPS not working:**
- Verify DNS resolves to the server: `dig esa-blueshell.nl`
- Traefik obtains certs lazily on first request — make an HTTP request to trigger it
- Check Traefik logs: `docker compose --project-name infra logs traefik`
- Inspect acme.json: `docker run --rm -v traefik_letsencrypt:/d alpine cat /d/acme.json`
