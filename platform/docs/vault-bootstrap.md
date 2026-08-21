# Vault bootstrap

One-time steps after a fresh cluster. Run these in order; the `apps-data`
Kustomization converges without them but Vault starts sealed and most
secrets remain unsynced until you complete the sequence.

## 1. Initialize and unseal Vault

```bash
kubectl exec -n data-system vault-0 -- vault operator init \
  -key-shares=5 -key-threshold=3 \
  -format=json > /tmp/vault-init.json

# Store ALL values OFFLINE (password manager, split across operators).
# Never commit /tmp/vault-init.json; shred it after the keys are saved.
ROOT_TOKEN=$(jq -r '.root_token' /tmp/vault-init.json)

for i in 0 1 2; do
  kubectl exec -n data-system vault-0 -- vault operator unseal \
    "$(jq -r ".unseal_keys_b64[$i]" /tmp/vault-init.json)"
done
```

Single-replica Raft, so the three unseal operations complete on the one
pod. You must unseal again after every Vault pod restart (e.g. node
reboot) — three of the five shares are required each time. Automate with
[vault-unseal](https://github.com/lrstanley/vault-unseal) or store the
unseal keys in a cloud KMS later.

## 2. Seed the bootstrap token

The bootstrap Job reads its Vault root token from `vault-bootstrap-token`.
Create that secret before applying the Job (or Flux will stall on the Job
with a missing secret error):

```bash
kubectl create secret generic vault-bootstrap-token \
  -n data-system \
  --from-literal=token="$ROOT_TOKEN"
```

## 3. Run the bootstrap Job

Flux applies the Job automatically once `apps-data` reconciles. If it has
not run yet, trigger it manually:

```bash
flux reconcile kustomization apps-data --timeout=5m
```

Watch progress:

```bash
kubectl logs -n data-system -l app.kubernetes.io/name=vault-bootstrap-auth -f
```

The Job is idempotent — you can re-run it safely after editing
`bootstrap-auth.sh` (kustomize content-hash triggers a force-replace).

## 4. Seed static secrets

### Day-0 checklist (have these in hand before unsealing Vault)

Each item below maps to one or more keys in §4.x — gather them once, in
one local working directory, before starting the seed flow. Missing any
of them blocks at least one downstream Secret.

- **Old-VPS `.env` files** (operator-controlled, never committed). The
  repo's `scripts/seed-vault-from-env.sh` reads dotenv-style files;
  typical inventory:
  - `services/api/.api.env` — `JWT_SECRET` (Base64, ≥64 bytes), Brevo,
    Mollie, Google Calendar SA, Facebook, X, Discord tokens.
  - `services/api/.db.env` — `MYSQL_ROOT_PASSWORD`, `MYSQL_USER`,
    `MYSQL_PASSWORD`.
- **Cloudflare DNS API token** with `Zone:DNS:Edit` scope on
  `esa-blueshell.nl` (cert-manager DNS-01 + external-dns).
- **GHCR pull credential**: GitHub username + a fine-grained PAT scoped
  to `read:packages` on `ESA-Blueshell` (private api/frontend images).
- **Stalwart admin user/password** + base64-encoded RSA-2048 DKIM
  private key + bounce mailbox `bounce@esa-blueshell.nl` credentials.
- **Discord incoming webhook URL** for the channel that receives Gatus
  uptime alerts and Keel rollout notifications. Optional at day 0 —
  both consumers start without it.
- **One-shot generated values** (only if missing from the legacy env):
  - `JWT_SECRET` — `openssl rand -base64 64`.
  - `vault-oidc-client-secret` — `openssl rand -hex 32`.

Sanity-check the env files locally with a dry run *before* unsealing:

```bash
scripts/seed-vault-from-env.sh \
  /path/to/old/.api.env \
  /path/to/old/.db.env \
  /path/to/extra-tokens.env
```

The dry run prints every Vault path/field it would write. If a path
shows up empty or with fewer fields than §4.x lists below, top up the
env files and re-run the dry run.

### Seed Vault

These paths must exist in Vault before the corresponding VSO
`VaultStaticSecret` CRs can sync. The VSO CRs loop on a 1h refresh and
will eventually succeed once the paths are present; there is no need to
unseal+re-bootstrap after seeding.

The MariaDB `existingSecret` reference looks like a chicken-and-egg —
the Bitnami chart won't start until its k8s Secret exists, but the
Secret only appears after VSO syncs from Vault. The repo solves this
by placing the chart-blocking VaultStaticSecret CR
(`mariadb-credentials`) inside `apps-data` itself, so it applies
alongside the HelmRelease. As soon as Vault is
unsealed and the bootstrap Job has wired up the kubernetes auth role
for VSO, both Secrets materialise in-place and the charts upgrade on
their own. No manual `kubectl create secret` pre-seed is needed.

If you already have dotenv files from the old VPS and/or the current
repo-local examples, the repo can translate them into the Vault paths
below:

```bash
scripts/seed-vault-from-env.sh \
  ../blueshell-website-old/.env \
  services/api/.db.env \
  services/api/.api.env
```

Preview is the default. Re-run with `--apply` once the mapping looks
correct. Add `--sync-api` when you want the script to force VSO to pull
the refreshed `secret/api` values and roll the api pod immediately. The
script reconstructs `google-calendar-sa-json` automatically from either
the current single JSON env var or the legacy split `GOOGLE_CALENDAR_*`
fields.

### Cloudflare DNS token (cert-manager + external-dns)

```bash
vault kv put secret/platform/edge \
  cloudflare.dns_api_token=<token-with-Zone:DNS:Edit>
```

### MariaDB credentials

```bash
vault kv put secret/platform/mariadb \
  root-password=<strong-password> \
  user=blueshell \
  password=<app-password> \
  admin-user=root \
  admin-password=<root-or-separate-admin-password>
```

The bootstrap Job's MariaDB dynamic-secrets block reads
`admin-user` / `admin-password` when present and falls back to the
legacy `user` / `password` pair otherwise. `user` / `password` are the
app credentials the Helm chart keeps stable; `admin-*` is the privileged
login Vault uses to mint short-lived `database/creds/api` users.
It is safe to run the Job before seeding — the block short-circuits and
prints a reminder. If you want to preserve the old Swarm-era database
login for operator reference, store it separately as
`legacy-user` / `legacy-password`; the v2 restore flow does not need it.

### Stalwart mail server

```bash
vault kv put secret/platform/mail \
  admin-user=admin \
  admin-password=<stalwart-admin-password> \
  bounce-mailbox-user=bounce@esa-blueshell.nl \
  bounce-mailbox-password=<bounce-mailbox-password>
```

### API third-party secrets

The api Vault Agent template renders these into `/vault/secrets/api.env`
at pod start (`platform/cluster/flux/apps/stateless/api/deployment.yaml`).
Every key must exist; the template silently substitutes empty strings
when a KV key is missing, which boots the pod with a broken integration.

```bash
vault kv put secret/api \
  jwt-secret=$(openssl rand -base64 64) \
  brevo-api-key=<brevo-api-key> \
  brevo-folder-contribution-periods-id=<brevo-folder-id> \
  mollie-api-key=<mollie-api-key> \
  google-calendar-id=<calendar-id> \
  google-calendar-sa-json=<raw-single-line-service-account-json> \
  facebook-page-id=<facebook-page-id> \
  facebook-access-token=<long-lived-page-token> \
  x-api-key=<x-consumer-key> \
  x-api-secret=<x-consumer-secret> \
  x-access-token=<x-access-token> \
  x-access-secret=<x-access-token-secret> \
  discord-bot-token=<discord-bot-token> \
  discord-guild-id=<discord-guild-id> \
  vault-oidc-client-secret=$(openssl rand -hex 32)
```

Notes:

- `jwt-secret` is the HMAC key the api uses to sign its own JWTs. Must be
  Base64 and decode to at least 64 bytes because the service signs with
  HS512. `openssl rand -base64 64` satisfies that guard.
- `vault-oidc-client-secret` is the shared secret the Vault OIDC auth
  method uses when calling back to the api. It reaches the api via VSO
  (`api-secrets` Kubernetes Secret) rather than the Vault Agent template,
  so it must be seeded here even though it is not in the agent template.
- `google-calendar-sa-json` is the full JSON contents of a Google service
  account key as raw JSON on one line, not base64. If your legacy env
  still has split `GOOGLE_CALENDAR_*` fields, the seeding script
  reconstructs the JSON for you; there is no separate Google-only script.

### Transit signing key + Vault OIDC auth method (handled by the bootstrap Job)

The bootstrap Job (`apps-data/vault/bootstrap-auth.sh`) already creates
`transit/keys/api-jwt` (RSA-2048, used by the api's OIDC issuer to sign
JWTs) and configures the `oidc` auth method that backs
`vault login -method=oidc` — the operator does not run any `vault write`
commands for either.

The OIDC step short-circuits when `secret/api:vault-oidc-client-secret`
is missing, so on a fresh cluster the Job runs once before the seed
script and again after it. After running `seed-vault-from-env.sh
--apply`, trigger the Job to re-run:

```bash
flux reconcile kustomization apps-data
```

If you ever need to inspect or override the OIDC config manually:

```bash
vault read auth/oidc/config
vault read auth/oidc/role/admin
```

The redirect URI baked into the role is
`https://vault.esa-blueshell.nl/ui/vault/auth/oidc/oidc/callback`; it
must match the `vault` client registered in `RegisteredClients.kt`.

### GHCR pull credential (api + frontend Deployments)

`ghcr.io/esa-blueshell/{api,frontend}` are private packages. VSO
materialises `default/ghcr-pull-secret` (type
`kubernetes.io/dockerconfigjson`) from this Vault path; both
Deployments reference it via `imagePullSecrets`.

```bash
vault kv put secret/platform/ghcr \
  username=<github-username> \
  token=<github-pat-with-read:packages>
```

The PAT needs **only** `read:packages` scope (fine-grained PAT: repo
access to `ESA-Blueshell`, permission `Packages: read-only`). Rotate by
re-running the same `kv put` with a new token — VSO re-renders the
dockerconfigjson within one refresh cycle (1 h) and pods pick up the
new auth on their next pull.

### Alerting webhook (Gatus + Keel)

Gatus posts uptime alerts and Keel posts image-rollout notifications to
the same Discord incoming webhook. VSO materialises
`utility-system/alerting-discord` with a single key,
`DISCORD_WEBHOOK_URL`, from this path.

```bash
vault kv put secret/platform/alerting \
  discord.webhook_url=https://discord.com/api/webhooks/<id>/<token>
```

Create the webhook in Discord under *Server Settings → Integrations →
Webhooks*; the channel it targets is where every alert and rollout
message lands. Anyone holding the URL can post to that channel, so it
is a credential and never belongs in git.

Both consumers tolerate the path being absent: the Gatus Deployment
marks its `secretKeyRef` optional and logs the discord provider as
misconfigured while continuing to serve the status page, and the Keel
HelmRelease patches its chart-rendered `envFrom` to `optional: true` so
image auto-updates keep running with notifications off. Seeding the
path and letting VSO sync (≤1 h, or force a reconcile) turns both on;
the Gatus Deployment is restarted automatically because the
VaultStaticSecret lists it under `rolloutRestartTargets`.

Rotate by re-running the same `kv put`. Keel reads the env var per
notification and needs no restart; Gatus reads it once at config load,
which the rollout-restart target handles.

## 5. Confirm VSO sync

After seeding, force a VSO reconcile and verify secrets appear:

```bash
flux reconcile kustomization apps-vso-secrets --timeout=3m
kubectl get secret -n cert-manager cloudflare-api-token
kubectl get secret -n data-system  mariadb-credentials
kubectl get secret -n default      api-secrets
kubectl get secret -n default      stalwart-secrets
kubectl get secret -n mail-system  stalwart-secrets
```

## 6. Rotate the root token

The `vault-bootstrap-token` secret holds the root token. Revoke the root
token once initial setup is complete and store the unseal key offline:

```bash
vault token revoke "$ROOT_TOKEN"
kubectl delete secret -n data-system vault-bootstrap-token
```

You can regenerate a new root token at any time from the unseal key:

```bash
vault operator generate-root -init
```

## 7. Rotating credentials

Every Vault path consumed by an app is rendered into the running pod
either by VSO (k8s Secret) or the Vault Agent injector
(`/vault/secrets/*.env`). Both modes are pre-populate-only — neither
auto-rolls a pod when the source changes — so the rotation pattern is
always: *update Vault, then restart the consumer.*

### MariaDB password (api + Bitnami chart)

The api reads `MYSQL_USER` / `MYSQL_PASSWORD` from `secret/api`
(rendered into `/vault/secrets/api.env` by the Vault Agent template in
`apps/stateless/api/deployment.yaml`). The Bitnami MariaDB chart reads
the same value from `secret/platform/mariadb` via VSO. Keep the two
fields in lockstep:

```bash
NEW=<new-password>
vault kv patch secret/api               mysql-password="$NEW"
vault kv patch secret/platform/mariadb  password="$NEW"

ROOT=$(vault kv get -field=root-password secret/platform/mariadb)
kubectl -n data-system exec mariadb-0 -- \
  mysql -uroot -p"$ROOT" -e \
  "ALTER USER 'blueshell'@'%' IDENTIFIED BY '$NEW'; FLUSH PRIVILEGES;"

kubectl -n default rollout restart deployment/api
```

The `mariadb-credentials` k8s Secret picks up the new value on VSO's
next refresh (within 1 h, or trigger immediately with the
`vso.secrets.hashicorp.com/force-refresh` annotation).

### Other Vault paths

Same shape, narrower blast radius:

| Path | Consumers | Restart |
|---|---|---|
| `secret/api` | api Deployment | `kubectl -n default rollout restart deployment/api` |
| `secret/platform/mail` | stalwart Deployment | `kubectl -n mail-system rollout restart deployment/stalwart` |
| `secret/platform/edge` | cert-manager + external-dns | restarts not usually needed; VSO refreshes the Secret in place |
| `secret/platform/ghcr` | api + frontend `imagePullSecrets` | next image pull picks up the new auth |

For the api's third-party tokens (Brevo, Mollie, etc.),
`scripts/seed-vault-from-env.sh --apply --sync-api` does the
`vault kv patch` + VSO force-refresh + api pod delete in one step.

### Future: Spring Cloud Vault dynamic MariaDB creds

`bootstrap-auth.sh` already configures the MariaDB dynamic-secrets
engine (`database/config/mariadb`) and role
(`database/roles/api`, 72h default / 168h max). Once
`spring.cloud.vault.database.enabled=true` correctly rebinds
`spring.datasource.{username,password}` (currently broken in our
Spring Cloud Vault version), drop `mysql-user` / `mysql-password`
from the Vault Agent template and re-set
`VAULT_DB_ENABLED=true` on the api Deployment. Vault then mints a
short-lived MariaDB user per pod and rotates it without operator
involvement.
