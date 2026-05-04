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

These paths must exist in Vault before the corresponding VSO
`VaultStaticSecret` CRs can sync. The VSO CRs loop on a 1h refresh and
will eventually succeed once the paths are present; there is no need to
unseal+re-bootstrap after seeding.

The MariaDB and Listmonk-Postgres `existingSecret` references look like
a chicken-and-egg — the Bitnami charts won't start until their k8s
Secrets exist, but the Secrets only appear after VSO syncs from Vault.
The repo solves this by placing the two chart-blocking VaultStaticSecret
CRs (`mariadb-credentials`, `listmonk-db-credentials`) inside `apps-data`
itself, so they apply alongside the HelmReleases. As soon as Vault is
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
  services/api/.api.env \
  services/listmonk/.listmonk.env
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

### Listmonk

```bash
vault kv put secret/listmonk \
  db-admin-password=<postgres-superuser-password> \
  db-password=<listmonk-user-password> \
  admin-user=listmonk-admin \
  admin-password=<listmonk-ui-password> \
  admin-email=admin@esa-blueshell.nl \
  api-user=api \
  smtp-password=<smtp-password>
```

### Stalwart mail server

```bash
vault kv put secret/platform/mail \
  admin-user=admin \
  admin-password=<stalwart-admin-password> \
  dkim-private-key=<base64-encoded-rsa-2048-pem> \
  bounce-mailbox-user=bounce@v2.esa-blueshell.nl \
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

### Transit signing key (OIDC issuer)

The api's OIDC issuer signs JWTs with a Vault-managed RSA key. The
bootstrap Job grants the `api` policy `sign` on this key but does not
create it (idempotency is cheaper than a pre-check).

```bash
vault write -f transit/keys/api-jwt type=rsa-2048
vault write transit/keys/api-jwt/config deletion_allowed=false
```

### Vault OIDC auth method (logging into Vault via the api)

`vault login -method=oidc` redirects the operator through the
website api as the IdP. Configure the auth method to point at the
same-origin issuer:

```bash
vault auth enable oidc 2>/dev/null || true
vault write auth/oidc/config \
  oidc_discovery_url="https://v2.esa-blueshell.nl/api" \
  oidc_client_id="vault" \
  oidc_client_secret="$(vault kv get -field=vault-oidc-client-secret secret/api)" \
  default_role="admin"
vault write auth/oidc/role/admin \
  bound_audiences="vault" \
  allowed_redirect_uris="https://vault.esa-blueshell.nl/ui/vault/auth/oidc/oidc/callback" \
  user_claim="sub" \
  policies="default"
```

The redirect URI must match the one registered in
`RegisteredClients.kt:47`. The client secret was seeded above with
`openssl rand -hex 32` under `vault-oidc-client-secret`.

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

## 5. Confirm VSO sync

After seeding, force a VSO reconcile and verify secrets appear:

```bash
flux reconcile kustomization apps-vso-secrets --timeout=3m
kubectl get secret -n cert-manager cloudflare-api-token
kubectl get secret -n data-system  mariadb-credentials
kubectl get secret -n data-system  listmonk-db-credentials
kubectl get secret -n default      api-secrets
kubectl get secret -n default      listmonk-secrets
kubectl get secret -n default      stalwart-secrets
kubectl get secret -n mail-system  stalwart-secrets
```

`listmonk-api-token` is not a Vault/VSO Secret. The `listmonk-setup` Job
creates it after first-time setup and the api pod mounts it at
`/run/secrets/listmonk/api-token.env`.

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
