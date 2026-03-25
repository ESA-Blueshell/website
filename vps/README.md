# VPS Provisioning & Operations

Everything needed to provision and operate the production server (Contabo VPS)
for the ESA Blueshell website.

## Directory Structure

```
vps/
├── .example.env              Configuration template (copy to .env)
├── cloud-init/
│   ├── cloud-config.template.yaml  Cloud-init template
│   ├── render.sh             Renders the template with real values from .env
│   ├── rendered/             Auto-generated env files (gitignored)
│   └── scripts/              One-time setup scripts (called from runcmd)
│       ├── setup-infisical.sh    Install Infisical CLI (best-effort)
│       ├── setup-firewall.sh     UFW rules (SSH 2222, HTTP 80, HTTPS 443)
│       ├── setup-docker.sh       Docker proxy config + daemon enable
│       ├── setup-directories.sh  App + backup directory structure
│       ├── setup-deploy-keys.sh  Distribute GitHub deploy key to users
│       ├── setup-swarm.sh        Docker Swarm init + overlay networks
│       ├── setup-repo.sh         Clone repo (HTTPS-first, SSH fallback)
│       └── setup-ghcr.sh         Log website user into ghcr.io
├── lib/
│   ├── credentials.py        Credential loading + auto-generation
│   └── render.py             Template renderer (Python)
├── scripts/
│   ├── local/                Runs on YOUR machine
│   │   ├── reinstall.sh      Reinstall the Contabo VPS with a fresh image
│   │   ├── backup.sh         Pull a full backup from the server
│   │   └── restore.sh        Push a backup to the server
│   └── server/               Persistent utilities at /usr/local/bin/
│       ├── db-backup.sh      Server-side backup (cron daily at 03:00)
│       └── website-cli.sh    Docker Swarm management CLI
└── tests/
    ├── test_hash_verification.py   Password hash tests
    ├── test_schema.py              YAML schema + script embedding tests
    ├── test_server_scripts.py      Setup + utility script unit tests
    └── test_cloud_init.py          Full LXD VM integration test
```

---

## Setting Up the Server

### Prerequisites

- [`cntb` CLI](https://github.com/contabo/cntb) installed and available on `$PATH`
- A Contabo account with API access (OAuth2 credentials)
- A GitHub account with access to the `ghcr.io` registry

---

### Step 1 — Configure

```bash
cd vps
cp .example.env .env
```

Edit `.env` and fill in the required values. See `.example.env` for what's
needed — secrets marked "auto-generated" will get random values from `render.sh`
if left blank. See `lib/credentials.py` for the full list of optional variables.

To get your Contabo OAuth2 credentials, visit:
https://api.contabo.com/#section/Authentication

To find your instance ID:
```bash
source .env
cntb get instances \
  --oauth2-clientid      "${CLIENT_ID}" \
  --oauth2-client-secret "${CLIENT_SECRET}" \
  --oauth2-user          "${CONTABO_API_USER}" \
  --oauth2-password      "${CONTABO_API_PASSWORD}"
```

---

### Step 2 — Generate SSH keys

Run `render.sh` to generate the two ed25519 keypairs (if they don't exist yet):

```bash
./cloud-init/render.sh
```

This generates (or reuses) in `~/.ssh/`:

- `blueshell-admin` — for the `admin` sudo admin user
- `blueshell-website` — for the `website` application user

> If the keys already exist they are reused unchanged. Safe to run multiple times.

---

### Step 3 — Upload the admin SSH key to Contabo

The admin key must be registered with Contabo before reinstalling the VPS.

```bash
source .env

cntb create secret \
  --oauth2-clientid       "${CLIENT_ID}" \
  --oauth2-client-secret  "${CLIENT_SECRET}" \
  --oauth2-user           "${CONTABO_API_USER}" \
  --oauth2-password       "${CONTABO_API_PASSWORD}" \
  --name                  blueshell-admin \
  --value                 "$(cat ~/.ssh/blueshell-admin.pub)" \
  --type                  ssh
```

Note the returned integer ID, then list to confirm:

```bash
cntb get secrets --type ssh \
  --oauth2-clientid       "${CLIENT_ID}" \
  --oauth2-client-secret  "${CLIENT_SECRET}" \
  --oauth2-user           "${CONTABO_API_USER}" \
  --oauth2-password       "${CONTABO_API_PASSWORD}"
```

Set `CONTABO_SSH_KEY_ID` to that integer in `.env`.

> This step only needs to be done once. Reuse the same key ID for future reinstalls.

---

### Step 4 — Render the cloud-init config

```bash
./cloud-init/render.sh
# Produces: cloud-init/cloud-config.yaml
#           cloud-init/rendered/.db.env, .api.env, .listmonk.env, .infra.env
```

This hashes the user passwords (SHA-512 crypt), substitutes all SSH public
keys, GHCR credentials, and auto-generated secrets into the template. The
rendered files contain secrets — do not commit them.

---

### Step 5 — Reinstall the VPS

```bash
./scripts/local/reinstall.sh
```

This calls `cntb reinstall instance` with the debian-13 image and passes the
rendered cloud-init config as user-data. **All existing data on the VPS will be
wiped.** The script asks for confirmation before proceeding.

The IP address of the VPS does not change after reinstall. Make sure `REMOTE_HOST`
in `.env` is set to the correct IP.

---

### Step 6 — Wait for cloud-init to complete (~10–15 min)

Cloud-init runs on first boot. Each step is a standalone script called
directly from `runcmd` (no orchestrator — each is independently testable):

1. Installs system packages (Docker CE, cron, etc.)
2. `setup-infisical.sh` — Install Infisical CLI (optional, best-effort)
3. `setup-firewall.sh` — UFW rules (only ports 2222, 80, 443)
4. `setup-docker.sh` — Docker proxy config + daemon enable
5. `setup-directories.sh` — App + backup directory structure
6. `setup-deploy-keys.sh` — Distribute deploy key to users
7. `setup-swarm.sh` — Docker Swarm init + overlay networks
8. `setup-repo.sh` — Clone repo (HTTPS-first, SSH fallback)
9. `setup-ghcr.sh` — Log website user into ghcr.io
10. **Deploys the infrastructure stack** (Traefik, monitoring, Infisical)

To check progress once the VPS responds on port 2222:

```bash
ssh -p 2222 -i ~/.ssh/blueshell-admin admin@<IP>
sudo tail -f /var/log/cloud-init-output.log
```

Wait for the final message:

```
Cloud-init setup complete — stacks deployed automatically!
```

---

### Step 7 — Verify and finish

1. **Check services**:
   ```bash
   su -l website -c "docker stack services infra"
   su -l website -c "docker stack services website"
   ```

2. **SSL certificates** — Traefik obtains them automatically on the first
   request. DNS must point to the server before this works.

3. **Infisical** (optional) — The self-hosted Infisical vault is deployed as
   part of the infra stack. To enable centralized secret management:
    - Open `https://vault.<domain>` and create an admin account
    - Create an organization and project
    - Create a machine identity with universal auth
    - Generate an access token and write `.server.env`:
      ```bash
      cat > /src/website/.server.env <<EOF
      INFISICAL_TOKEN=<machine-identity-token>
      INFISICAL_PROJECT_ID=<project-id>
      INFISICAL_API_URL=http://localhost:8080
      EOF
      ```
    - The system works without Infisical — deploy scripts fall back to local
      env files automatically.

4. **Verify automated backup** — runs daily at 03:00, or trigger now:

   ```bash
   sudo /usr/local/bin/db-backup
   ls -l /src/backups/db/
   ```

5. **Configure Uptime Kuma** at `https://status.<domain>`

Full deployment documentation: [services/README.md](../services/README.md)

---

## Backup & Restore

### Pull a backup from the server

```bash
./scripts/local/backup.sh [remote_host]
```

Triggers a fresh server-side backup, then downloads to `./backup/`:

| Path                                             | Contents                                  |
|--------------------------------------------------|-------------------------------------------|
| `backup/db/db-backup-*.sql.gz`                   | MariaDB dumps (7-day retention on server) |
| `backup/db/listmonk-backup-*.sql.gz`             | Listmonk PostgreSQL dumps                 |
| `backup/env/services/{api,listmonk,mailserver}/` | Env files (contain secrets)               |
| `backup/storage/`                                | File uploads (incremental rsync)          |
| `backup/mailserver/`                             | Mail data + config                        |

### Push a backup to a server

```bash
./scripts/local/restore.sh [remote_host]
```

Pushes the most recent local backup to the server:

- All DB dumps -> `/src/backups/db/`
- Env files -> service directories
- Storage -> `/src/website/storage/` (incremental)
- Latest MariaDB dump -> `services/api/1_dump.sql` (auto-imported by MariaDB on first container start)
- Mailserver data -> `services/mailserver/docker-data/dms/`

### Automated server-side backups

The server runs a daily backup at 03:00 via cron. Backed up:

- MariaDB (mysqldump, gzipped)
- Listmonk PostgreSQL (pg_dump, gzipped, if running)
- Env files snapshot
- File storage (incremental rsync)
- Mailserver data (incremental rsync, if present)

Old DB dumps are pruned after 7 days.

---

## Credential Rotation

All credential rotation is handled by the **rotator service**
(`services/rotator/`), which runs as part of the application Docker Swarm stack.

The rotator:

1. Changes the credential in the backing service (ALTER USER, API call, etc.)
2. Updates the local env file (`.db.env`, `.api.env`, `.listmonk.env`)
3. Updates Infisical (if configured and reachable)
4. Triggers a full stack redeploy to pick up the new credentials

Rotation runs automatically on a weekly schedule (Sunday 23:00 UTC) and can
also be triggered manually via the GitHub Actions workflow.

---

## `website` CLI Reference

The `website` command is installed at `/usr/local/bin/website` and runs as the
`website` user. Environment defaults to `production`.

```
website up      [env]           Deploy (or redeploy) the application
website down    [env]           Stop and remove the application
website status  [env]           Show service status
website logs    [env] <service> Tail service logs
website pull                    git pull + redeploy production (used by CI)
website infra up/down           Deploy/remove infrastructure stack (Traefik + monitoring)
website backup                  Run DB + storage backup (root only)
website shell                   Open a bash shell in /src/website
website help                    Show help
```

Pass `staging` or `development` to target other stacks on the same host:

```bash
website up staging
website logs staging api
```

Admin users delegate to the website user via:

```bash
su -l website -c "website up"
```

---

## Testing

Tests run in CI via the `test-cloud-config` GitHub Actions workflow on pushes
that touch `vps/**`. All fast tests run automatically; the full VM test is
manual dispatch only.

| Test | Duration | What it checks |
|------|----------|----------------|
| **Shellcheck** | ~30s | Static analysis of all shell scripts |
| **Hash verification** | ~5s | SHA-512 crypt hashes match plaintext passwords |
| **Schema validation** | ~5s | Rendered cloud-config passes cloud-init JSON schema |
| **Script embedding** | ~5s | All server scripts are in write_files and called from runcmd |
| **Server script tests** | ~5s | Individual scripts behave correctly (mocked commands) |
| **Deploy key check** | ~30s | SSH deploy key can authenticate to GitHub |
| **VM test** (manual) | ~10 min | Full Debian 13 VM via LXD: cloud-init, SSH, security |

### Running locally

```bash
cd vps
uv sync
uv run pytest tests/ -v -k "not vm"    # All fast tests
uv run pytest tests/ -v                  # Including VM test (needs LXD)
```

---

## Security Notes

- SSH is **keys-only** on port **2222**; password authentication is disabled.
- UFW allows only ports 2222, 80, and 443.
- The `website` user runs Docker — the `admin` user has sudo with password.
- Backup files are owned by `root:backup` (`chmod 2770`); the `website` user cannot delete them.
- The rendered `cloud-config.yaml` contains secrets — do not commit it.
- Infisical port 8080 is bound locally and blocked by UFW.

---

## GitHub Secrets

Only the deployment environments are needed in GitHub:

| Environment | Secrets | Used by |
|-------------|---------|---------|
| `production` | `SSH_KEY`, `IP_ADDRESS`, `PORT`, `DEPLOY_USER` | `deploy.yml`, `rotate-credentials.yml` |
| `staging` | `SSH_KEY`, `IP_ADDRESS`, `PORT`, `DEPLOY_USER` | `deploy.yml` |
| `development` | `SSH_KEY`, `IP_ADDRESS`, `PORT`, `DEPLOY_USER` | `deploy.yml` |
| repo-level | `SSH_DEPLOY_KEY` | `test-cloud-config.yml` |

All Contabo API credentials, TransIP keys, and VPS passwords are used only
locally (from `vps/.env`) and do not need to be in GitHub.
