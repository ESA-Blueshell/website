# Vault bootstrap

One-time steps after a fresh cluster. Run these in order; the `apps-data`
Kustomization converges without them but Vault starts sealed and most
secrets remain unsynced until you complete the sequence.

## 1. Initialize and unseal Vault

```bash
kubectl exec -n data-system vault-0 -- vault operator init \
  -key-shares=1 -key-threshold=1 \
  -format=json > /tmp/vault-init.json

# Store both values somewhere safe (password manager).
UNSEAL_KEY=$(jq -r '.unseal_keys_b64[0]' /tmp/vault-init.json)
ROOT_TOKEN=$(jq -r '.root_token'          /tmp/vault-init.json)

kubectl exec -n data-system vault-0 -- vault operator unseal "$UNSEAL_KEY"
```

Single-replica Raft, so one unseal is enough. You must unseal again after
every Vault pod restart (e.g. node reboot). Automate with
[vault-unseal](https://github.com/lrstanley/vault-unseal) or store the
unseal key in a cloud KMS later.

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
  password=<app-password>
```

The bootstrap Job's MariaDB dynamic-secrets block reads `user` and
`password` from this path to configure the `database/` secrets engine.
It is safe to run the Job before seeding — the block short-circuits and
prints a reminder.

### Listmonk

```bash
vault kv put secret/listmonk \
  db-admin-password=<postgres-superuser-password> \
  db-password=<listmonk-user-password> \
  admin-user=listmonk-admin \
  admin-password=<listmonk-ui-password> \
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

```bash
vault kv put secret/api \
  brevo-api-key=<key> \
  mollie-api-key=<key> \
  google-calendar-sa-json=<base64-json> \
  facebook-app-secret=<secret> \
  x-api-secret=<secret>
```

## 5. Confirm VSO sync

After seeding, force a VSO reconcile and verify secrets appear:

```bash
flux reconcile kustomization apps-vso-secrets --timeout=3m
kubectl get secret -n cert-manager cloudflare-api-token
kubectl get secret -n data-system  mariadb-credentials
kubectl get secret -n data-system  listmonk-db-credentials
kubectl get secret -n default      api-secrets
kubectl get secret -n default      listmonk-secrets
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
