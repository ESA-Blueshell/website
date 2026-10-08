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

- **`.env` files** (operator-controlled, never committed). The
  repo's `scripts/seed-vault-from-env.sh` reads dotenv-style files named
  like the repo's examples:
  - `services/api/.api.env`: `JWT_SECRET` (Base64, ≥64 bytes),
    `TWO_FACTOR_ENCRYPTION_KEY`, the Brevo, Google Calendar and Discord keys
    and `VAULT_OIDC_CLIENT_SECRET`.
  - An extra file for the platform paths: `CF_DNS_API_TOKEN`,
    `GHCR_USERNAME`/`GHCR_TOKEN`, `STALWART_ADMIN_USER`/`STALWART_ADMIN_PASSWORD`
    and `EMAIL_BOUNCE_IMAP_USERNAME`/`EMAIL_BOUNCE_IMAP_PASSWORD`. The script
    reads these names only.
  - `services/api/.db.env` — `MYSQL_ROOT_PASSWORD`, `MYSQL_USER`,
    `MYSQL_PASSWORD`.
- **Cloudflare DNS API token** with `Zone:DNS:Edit` scope on
  `esa-blueshell.nl` (cert-manager DNS-01 + external-dns).
- **GHCR pull credential**: GitHub username + a fine-grained PAT scoped
  to `read:packages` on `ESA-Blueshell` (private api/frontend images).
- **Stalwart admin user/password** + base64-encoded RSA-2048 DKIM
  private key + bounce mailbox `bounce@esa-blueshell.nl` credentials.
- **Discord incoming webhook URL** for the channel that receives Gatus
  uptime alerts and Flagger release events. Optional at day 0 —
  both consumers start without it.
- **One-shot generated values** (only if missing from the env files):
  - `JWT_SECRET`: `openssl rand -base64 64`.
  - `VAULT_OIDC_CLIENT_SECRET`: `openssl rand -hex 32`.

Sanity-check the env files locally with a dry run *before* unsealing:

```bash
scripts/seed-vault-from-env.sh \
  services/api/.db.env \
  services/api/.api.env \
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

If you have dotenv files named like the repo-local examples, the repo can
translate them into the Vault paths below:

```bash
scripts/seed-vault-from-env.sh \
  services/api/.db.env \
  services/api/.api.env
```

Preview is the default. Re-run with `--apply` once the mapping looks
correct. Add `--sync-api` to restart the api once `secret/api` is written,
since it reads the path at start.

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
prints a reminder.

### Stalwart mail server

```bash
vault kv put secret/platform/mail \
  admin-user=admin \
  admin-password=<stalwart-admin-password> \
  bounce-mailbox-user=bounce@esa-blueshell.nl \
  bounce-mailbox-password=<bounce-mailbox-password>
```

### API secrets

The api reads `secret/api` itself. In the prod profile Spring Cloud Vault logs
in with Kubernetes auth as role `api`, bound to the `api` ServiceAccount, and
imports the path as configuration (api ADR-033). Nothing renders these into a
file, an environment variable or a Kubernetes Secret, and the api reads them at
start. Each key is named for the Spring property it fills, so a new one is one
KV write and one property.

```bash
vault kv put secret/api \
  app.jwt.secret=$(openssl rand -base64 64) \
  app.two-factor.key=$(openssl rand -base64 32) \
  brevo.apiKey=<brevo-api-key> \
  brevo.folders.contributionPeriodsId=<brevo-folder-id> \
  google.calendar.id=<calendar-id> \
  google.calendar.serviceAccountJson=<raw-single-line-service-account-json> \
  discord.botToken=<discord-bot-token> \
  discord.guildId=<discord-guild-id> \
  auth.clients.vault.secret=$(openssl rand -hex 32)
```

A missing key is blank in production, never `application.yaml`'s development
value: the hardening guard refuses to start without `app.jwt.secret`,
`app.two-factor.key` or `auth.clients.vault.secret`, and an empty integration
key leaves that integration off.

The api also reads `secret/platform/mail`, the path Stalwart uses, with the
prefix `mail.`. It sends and polls as `bounce@`, with `account.bounce`: the
envelope sender is the bounce mailbox, so a report of undelivered mail comes back
where the poller reads, while `From:` stays `no-reply@`. The bootstrap Job seeds
`account.bounce` when it is missing, and Stalwart's apply sidecar creates the
mailbox from it on its next start.

Adding or rotating only the Discord bot is a `vault kv patch`, not a `put`:
see [`discord-bot.md`](discord-bot.md), or run `scripts/discord-bot-check.sh --vault`.

Notes:

- `app.jwt.secret` is the HMAC key the api uses to sign its own JWTs. Must be
  Base64 and decode to at least 64 bytes because the service signs with
  HS512. `openssl rand -base64 64` satisfies that guard.
- `app.two-factor.key` seals every authenticator app's secret (api ADR-031). It
  must decode to exactly 32 bytes; `openssl rand -base64 32` does. The api and the
  migrate Job refuse to start without it. It is not rotated by replacing it: a new key
  takes a new `app.two-factor.key-id`, and the old one moves to
  `app.two-factor.retired-keys` as `id:key`, or every secret sealed with it stops
  opening. Losing it means every person with two-factor needs a
  [two-factor reset](../../docs/flows/two-factor/README.md).
- `auth.clients.vault.secret` is the shared secret the Vault OIDC auth
  method uses when calling back to the api. The bootstrap Job reads the same
  key to configure that method.
- `google.calendar.serviceAccountJson` is the full JSON contents of a Google
  service account key as raw JSON on one line, not base64.
- There is no database login here: the api leases one from Vault. See
  "MariaDB logins" below.

### Transit signing key + Vault OIDC auth method (handled by the bootstrap Job)

The bootstrap Job (`apps-data/vault/bootstrap-auth.sh`) already creates
`transit/keys/api-jwt` (RSA-2048, used by the api's OIDC issuer to sign
JWTs) and configures the `oidc` auth method that backs
`vault login -method=oidc` — the operator does not run any `vault write`
commands for either.

The OIDC step short-circuits when `secret/api:auth.clients.vault.secret`
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

The role allows two redirect URIs, and both must also be registered on
the `vault` client in `RegisteredClients.kt` or the api refuses the
authorize request before Vault sees it:

- `https://vault.esa-blueshell.nl/ui/vault/auth/oidc/oidc/callback`
  for the browser, where the Vault UI completes the login itself.
- `http://localhost:8250/oidc/callback` for
  `vault login -method=oidc`, which binds a listener on that port and
  cannot read a code delivered to the UI instead.

Logging in from a terminal:

```bash
kubectl --context blueshell -n data-system port-forward svc/vault 8200:8200 &
export VAULT_ADDR=http://127.0.0.1:8200
vault login -method=oidc
```

An `Unable to authorize role "" with redirect_uri` error means the
role is missing the localhost entry: re-run the bootstrap Job with
`flux reconcile kustomization apps-data`.

### GHCR pull credential (Deployments + registry scanning)

`ghcr.io/esa-blueshell/{api,frontend,stalwart-tools}` are private
packages. VSO materialises `ghcr-pull-secret` (type
`kubernetes.io/dockerconfigjson`) from this Vault path into three
namespaces: `default` and `mail-system`, where the Deployments
reference it via `imagePullSecrets`, and `flux-system`, where
image-reflector-controller reads it to list tags. A tag listing needs
the same `read:packages` the pulls need, so one credential serves both.

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

### Alerting webhook (Gatus + Flagger)

Gatus posts uptime alerts and Flagger posts release events to
the same Discord incoming webhook. VSO materialises
`utility-system/alerting-discord` from this path, with
`DISCORD_WEBHOOK_URL` and `GATUS_BACKUP_TOKEN`. The second is the token
the nightly backup reports to Gatus with; `bootstrap-auth.sh` seeds
`gatus.backup_token` on its own.

```bash
vault kv put secret/platform/alerting \
  discord.webhook_url=https://discord.com/api/webhooks/<id>/<token>
```

Create the webhook under *Server Settings → Integrations → Webhooks*;
the channel it targets receives both alerts and rollout messages.

The path is optional: both consumers mark their Secret reference
`optional`, so an unseeded Vault costs notifications but neither the
status page nor image auto-updates. Seeding it turns both on within one
refresh cycle (1 h, or force a reconcile). Rotate the webhook with
`vault kv patch secret/platform/alerting discord.webhook_url=…`: a
`kv put` replaces the whole path and drops the backup token until the
bootstrap Job next runs. Gatus is restarted by the
`rolloutRestartTargets` entry on the VaultStaticSecret, Flagger needs no
restart.

### Nightly backup

`secret/platform/backup` holds the Kopia password and the
`backup-writer` key the nightly backup reads. An owner seeds it, never an
agent; [backup.md](backup.md#seeding-the-credentials) has the steps.

## 5. Confirm VSO sync

After seeding, force a VSO reconcile and verify secrets appear:

```bash
flux reconcile kustomization apps-vso-secrets --timeout=3m
kubectl get secret -n cert-manager cloudflare-api-token
kubectl get secret -n data-system  mariadb-credentials
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

VSO renders a path into a Kubernetes Secret for the consumers that are not
Spring, and none of them reloads it on its own, so for those the pattern is:
*update Vault, then restart the consumer.* The api needs no restart for the keys
below.

### Keys the api takes without a restart

The api re-reads `secret/api` and `secret/platform/mail` every five minutes
(`app.vault.refresh-interval`) and hands a changed key to whatever uses it, so
none of these needs `kubectl rollout restart`. Write each rotation as one
`vault kv patch`, so the api never reads half of it.

- **Brevo key, Google Calendar service account.** Patch the key; the next
  Brevo request sends it, and the calendar client is rebuilt from it. A service
  account that does not parse, or a key patched to blank, keeps the one in use.

  ```bash
  vault kv patch secret/api brevo.apiKey=<new-key>
  vault kv patch secret/api google.calendar.serviceAccountJson=@service-account.json
  ```

- **Discord bot token.** The REST client sends it on its next call and the
  gateway connects again under it; pages fall back as when Discord is down
  until the new session is up. A token set where none was before needs a
  restart, since the bot's beans only exist when the api starts with one.
  `scripts/discord-bot-check.sh --guild <server-id> --vault` patches it; see
  [`discord-bot.md`](discord-bot.md).

- **Bounce mailbox password** (`account.bounce` in `secret/platform/mail`). The
  api sends and polls with it, and reads it at each send and each poll.
  Stalwart takes it when VSO restarts it, within the Secret's refresh, so for up
  to one api refresh interval the two may disagree. A send that fails in that
  window shows as failed in the email manager, which can retry it. Until the
  contract step of api ADR-033 removes `EMAIL_BOUNCE_IMAP_PASSWORD` from the api
  Deployment, that variable outranks Vault, so this one still needs an api
  restart until then.

- **Two-factor key.** A new key takes a new id, and the old one moves to the
  retired keys in the same patch, or every secret it sealed stops opening. A key
  of the wrong length is refused and the old one stays; the api logs it.

  ```bash
  OLD_ID=$(vault kv get -field=app.two-factor.key-id secret/api)
  OLD=$(vault kv get -field=app.two-factor.key secret/api)
  vault kv patch secret/api \
    app.two-factor.key-id=$((OLD_ID + 1)) \
    app.two-factor.key="$(openssl rand -base64 32)" \
    app.two-factor.retired-keys="$OLD_ID:$OLD"
  ```

  Keep earlier retired keys in the list (`id:key` pairs, comma-separated) until
  every secret they sealed has been re-sealed.

- **JWT secret.** Tokens signed with the previous secret keep reading, so nobody
  is signed out. Only the one secret before the current one reads: rotate again
  no sooner than a sign-in lives.

  ```bash
  vault kv patch secret/api app.jwt.secret="$(openssl rand -base64 64)"
  ```

- **Vault OIDC client secret.** Vault's OIDC config holds the same secret, so
  Vault sign-in fails from the patch until both sides hold the new one, up to one
  refresh interval. Do it in a quiet moment: patch Vault KV, then run the
  bootstrap Job, which writes the new secret into `auth/oidc/config`.

  ```bash
  vault kv patch secret/api auth.clients.vault.secret="$(openssl rand -hex 32)"
  flux -n flux-system reconcile kustomization apps-data
  ```

### MariaDB logins (api, migrate Job and Bitnami chart)

The api does not keep a MariaDB password. Its prod profile leases a login from
`database/creds/api` (72h default, 168h max), which Vault creates as a user of
its own, and `DatabaseLoginRotation` asks for a fresh one before each lease runs
out and hands it to the connection pool. A stopping pod revokes its leases.
Nothing to rotate by hand.

The migrate Job logs in as `blueshell`, the stable owner the chart keeps, read
from `secret/platform/mariadb` through its own Vault role `migrate`. It owns the
schema because MariaDB records the creating user as each trigger's DEFINER, and a
trigger whose definer Vault has dropped fails every write to its table. Rotate
the owner the way the chart expects, then nothing restarts: the next migrate Job
reads the new value.

```bash
NEW=<new-password>
vault kv patch secret/platform/mariadb  password="$NEW"

ROOT=$(vault kv get -field=root-password secret/platform/mariadb)
kubectl -n data-system exec mariadb-0 -- \
  mysql -uroot -p"$ROOT" -e \
  "ALTER USER 'blueshell'@'%' IDENTIFIED BY '$NEW'; FLUSH PRIVILEGES;"
```

The `mariadb-credentials` k8s Secret picks up the new value on VSO's
next refresh (within 1 h, or trigger immediately with the
`vso.secrets.hashicorp.com/force-refresh` annotation).

Rotating `admin-user` / `admin-password`, the login Vault creates the leased
users with, needs the bootstrap Job to run again so `database/config/mariadb`
takes it: `flux reconcile kustomization apps-data`.

### Other Vault paths

Same shape, narrower blast radius:

| Path | Consumers | Restart |
|---|---|---|
| `secret/api` | api Deployment | none for the keys above; a new key the api has never read needs one |
| `secret/platform/mail` | stalwart Deployment | `kubectl -n mail-system rollout restart deployment/stalwart` |
| `secret/platform/edge` | cert-manager + external-dns | restarts not usually needed; VSO refreshes the Secret in place |
| `secret/platform/ghcr` | `imagePullSecrets` plus the Flux registry scan | next image pull picks up the new auth; a scan recovers on its own interval |

For the api's third-party tokens (Brevo, Google Calendar, Discord),
`scripts/seed-vault-from-env.sh --apply --sync-api` writes Vault and
restarts the api in one step.
