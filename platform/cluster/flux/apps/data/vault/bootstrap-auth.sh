#!/usr/bin/env sh
set -eu

# Idempotent Vault bootstrap for the website. Runs on every Flux apply;
# re-running against an already-configured Vault mutates the narrow set
# of resources below without disturbing sealed state or data.

# --- Auth + KV engine ----------------------------------------------------

if ! vault auth list -format=json | grep -q '"kubernetes/"'; then
  vault auth enable kubernetes
fi

# Deliberately do NOT pass token_reviewer_jwt. If set, Vault uses that
# literal JWT to call TokenReview, and projected service account tokens
# expire after 1 h by default — the first time the role is exercised
# past that window, every incoming auth login returns
# "Code: 403. * permission denied" with no obvious diagnostic. When
# token_reviewer_jwt is unset, Vault uses its own pod's SA token
# (kubelet auto-rotates it), so the config never goes stale.
#
# The Vault SA has system:auth-delegator via the vault-server-binding
# ClusterRoleBinding created by the Vault Helm chart, so it's already
# authorised to call TokenReview.
vault write auth/kubernetes/config \
  kubernetes_host="https://${KUBERNETES_SERVICE_HOST}:${KUBERNETES_SERVICE_PORT_HTTPS}" \
  kubernetes_ca_cert=@/var/run/secrets/kubernetes.io/serviceaccount/ca.crt

# kv-v2 at `secret/` is Vault's conventional default. Services read
# secret/data/<path>, the kv-v2 engine rewrites to <path>.
if ! vault secrets list -format=json | grep -q '"secret/"'; then
  vault secrets enable -path=secret kv-v2
fi

# --- Transit engine for JWT signing -------------------------------------

if ! vault secrets list -format=json | grep -q '"transit/"'; then
  vault secrets enable transit
fi

if ! vault read transit/keys/api-jwt >/dev/null 2>&1; then
  vault write transit/keys/api-jwt type="rsa-2048"
fi

# Derived keys that seal private details (api ADR-038): each value is bound to its context.
if ! vault read transit/keys/api-address >/dev/null 2>&1; then
  vault write transit/keys/api-address derived=true
fi

# --- Policies -----------------------------------------------------------

cat <<'EOF' >/tmp/api.hcl
path "secret/data/api" {
  capabilities = ["read"]
}

path "secret/data/api/*" {
  capabilities = ["read"]
}

# The mail passwords, from the path Stalwart shares.
path "secret/data/platform/mail" {
  capabilities = ["read"]
}

path "database/creds/api" {
  capabilities = ["read"]
}

# A stopping pod revokes its leased database users rather than leaving them to
# expire. Renewing needs nothing here: the default policy allows it.
path "sys/leases/revoke" {
  capabilities = ["update"]
}

path "transit/sign/api-jwt" {
  capabilities = ["update"]
}

path "transit/keys/api-jwt" {
  capabilities = ["read"]
}

# Seal, open and rewrap a member's address; nothing else with the key.
path "transit/encrypt/api-address" {
  capabilities = ["update"]
}

path "transit/decrypt/api-address" {
  capabilities = ["update"]
}

path "transit/rewrap/api-address" {
  capabilities = ["update"]
}
EOF

# The migrate Job boots the api's configuration and logs in as the schema's owner,
# which the api's own pods cannot read (api ADR-033).
cat <<'EOF' >/tmp/migrate.hcl
path "secret/data/api" {
  capabilities = ["read"]
}

path "secret/data/platform/mail" {
  capabilities = ["read"]
}

path "secret/data/platform/mariadb" {
  capabilities = ["read"]
}
EOF

cat <<'EOF' >/tmp/stalwart.hcl
path "secret/data/platform/mail" {
  capabilities = ["read"]
}

path "secret/data/platform/edge" {
  capabilities = ["read"]
}
EOF

# VSO reads here to mint k8s Secrets in the namespaces of apps that need
# them: platform/edge (Cloudflare DNS-01 token for cert-manager and
# external-dns), platform/mail (stalwart admin + SMTP relay credentials,
# bounce mailbox, DKIM), platform/ghcr (GitHub PAT for pulling private
# ghcr.io images), platform/flux-git (write deploy key
# image-automation-controller pushes with).
# See platform/docs/vault-bootstrap.md §4 for the full key list.
cat <<'EOF' >/tmp/admin.hcl
# Broad operator policy attached to OIDC-issued tokens for users with
# ROLE_ADMIN. Mirrors the access an interactive Vault administrator
# needs through the UI: read/write on every KV-v2 secret, manage auth
# methods + policies + transit keys, list mounts, lookup own token,
# inspect dynamic-secret roles. Excludes raw-storage and sys/seal so
# accidental destruction stays gated on the unseal flow.
path "secret/*"        { capabilities = ["create", "read", "update", "delete", "list"] }
path "secret/data/*"   { capabilities = ["create", "read", "update", "delete", "list"] }
path "secret/metadata/*" { capabilities = ["create", "read", "update", "delete", "list"] }
path "transit/*"       { capabilities = ["create", "read", "update", "delete", "list"] }
path "database/*"      { capabilities = ["create", "read", "update", "delete", "list"] }
path "auth/*"          { capabilities = ["create", "read", "update", "delete", "list", "sudo"] }
path "sys/auth"        { capabilities = ["read", "list"] }
path "sys/auth/*"      { capabilities = ["create", "read", "update", "delete", "list", "sudo"] }
path "sys/policies/acl"   { capabilities = ["list"] }
path "sys/policies/acl/*" { capabilities = ["create", "read", "update", "delete", "list"] }
path "sys/mounts"      { capabilities = ["read", "list"] }
path "sys/mounts/*"    { capabilities = ["create", "read", "update", "delete", "list", "sudo"] }
path "sys/health"      { capabilities = ["read"] }
path "sys/capabilities-self" { capabilities = ["update"] }
path "auth/token/lookup-self"  { capabilities = ["read"] }
path "auth/token/renew-self"   { capabilities = ["update"] }
path "auth/token/revoke-self"  { capabilities = ["update"] }
EOF

cat <<'EOF' >/tmp/vso.hcl
path "secret/data/platform/edge" {
  capabilities = ["read"]
}

path "secret/data/platform/mail" {
  capabilities = ["read"]
}

path "secret/data/platform/ghcr" {
  capabilities = ["read"]
}

path "secret/data/platform/flux-git" {
  capabilities = ["read"]
}

path "secret/data/platform/mariadb" {
  capabilities = ["read"]
}

path "secret/data/platform/alerting" {
  capabilities = ["read"]
}
EOF

vault policy write api /tmp/api.hcl
vault policy write migrate /tmp/migrate.hcl
vault policy write stalwart /tmp/stalwart.hcl
vault policy write vso /tmp/vso.hcl
vault policy write admin /tmp/admin.hcl

# --- Kubernetes auth roles ---------------------------------------------

vault write auth/kubernetes/role/api \
  bound_service_account_names="api" \
  bound_service_account_namespaces="default" \
  policies="api" \
  ttl="1h"

vault write auth/kubernetes/role/migrate \
  bound_service_account_names="migrate" \
  bound_service_account_namespaces="default" \
  policies="migrate" \
  ttl="1h"

vault write auth/kubernetes/role/stalwart \
  bound_service_account_names="stalwart" \
  bound_service_account_namespaces="mail-system" \
  policies="stalwart" \
  ttl="1h"

vault write auth/kubernetes/role/vso \
  bound_service_account_names="vault-secrets-operator" \
  bound_service_account_namespaces="vso-system,cert-manager,external-dns,default,mail-system,data-system,utility-system,flux-system,flagger-system" \
  policies="vso" \
  ttl="1h"

# Stalwart's apply sidecar creates bounce@ from account.bounce, and the api sends
# and polls as it (#1868): without the key there is no mailbox. Seeded once and
# never overwritten; any answer but "not there" fails the Job rather than guess.
if BOUNCE_READ=$(vault kv get -field=account.bounce secret/platform/mail 2>&1); then
  :
elif printf '%s' "$BOUNCE_READ" | grep -q -e 'No value found' -e 'not present in secret'; then
  BOUNCE_PASSWORD=$(head -c 32 /dev/urandom | base64 | tr -d '=+/\n' | head -c 32)
  [ "${#BOUNCE_PASSWORD}" -eq 32 ] || { echo "Could not generate account.bounce." >&2; exit 1; }
  # A put replaces every key on the path, so it is only for a path that has none.
  if printf '%s' "$BOUNCE_READ" | grep -q 'No value found'; then
    printf '%s' "$BOUNCE_PASSWORD" | vault kv put secret/platform/mail account.bounce=- >/dev/null
  else
    printf '%s' "$BOUNCE_PASSWORD" | vault kv patch secret/platform/mail account.bounce=- >/dev/null
  fi
  unset BOUNCE_PASSWORD
  echo "Seeded secret/platform/mail account.bounce."
else
  echo "Could not read secret/platform/mail: $BOUNCE_READ" >&2
  exit 1
fi
unset BOUNCE_READ

# --- MariaDB dynamic secrets (database engine) --------------------------
#
# The api reads DB creds from Vault via `database/creds/api`. The
# engine itself is configured lazily because the mariadb chart does not
# expose an admin password via a Kubernetes Secret VSO can mint — an
# operator seeds it via `secret/platform/mariadb`. Prefer the explicit
# `admin-user` / `admin-password` fields; fall back to the legacy
# `user` / `password` pair so an already-seeded cluster keeps working.
# The block short-circuits until the secret exists so a fresh Vault
# install does not block the bootstrap Job on an unsatisfiable
# precondition.

if ! vault secrets list -format=json | grep -q '"database/"'; then
  vault secrets enable database
fi

if vault kv get secret/platform/mariadb >/dev/null 2>&1; then
  DB_ADMIN_USER=$(vault kv get -field=admin-user secret/platform/mariadb 2>/dev/null || vault kv get -field=user secret/platform/mariadb)
  DB_ADMIN_PASS=$(vault kv get -field=admin-password secret/platform/mariadb 2>/dev/null || vault kv get -field=password secret/platform/mariadb)

  vault write database/config/mariadb \
    plugin_name=mysql-database-plugin \
    allowed_roles="api" \
    connection_url="{{username}}:{{password}}@tcp(mariadb.data-system.svc.cluster.local:3306)/" \
    username="${DB_ADMIN_USER}" \
    password="${DB_ADMIN_PASS}" \
    verify_connection=true

  # Data only: a leased user that created a trigger or view would be named its
  # DEFINER, and Vault drops that user when the lease ends (api ADR-033).
  vault write database/roles/api \
    db_name=mariadb \
    default_ttl="72h" \
    max_ttl="168h" \
    creation_statements="CREATE USER '{{name}}'@'%' IDENTIFIED BY '{{password}}'; GRANT SELECT, INSERT, UPDATE, DELETE, CREATE TEMPORARY TABLES ON blueshell.* TO '{{name}}'@'%';"

  unset DB_ADMIN_USER DB_ADMIN_PASS
else
  echo "secret/platform/mariadb not seeded yet — skipping database/config/mariadb."
  echo "Seed with: vault kv put secret/platform/mariadb root-password=<secret> user=<app-user> password=<app-secret> admin-user=<db-admin> admin-password=<db-admin-secret>"
fi

# --- OIDC auth method (vault login -method=oidc) ------------------------
# Short-circuit when the client secret is unseeded so a fresh Vault
# install doesn't fail the Job on an unsatisfiable precondition.

if vault kv get -field=auth.clients.vault.secret secret/api >/dev/null 2>&1; then
  if ! vault auth list -format=json | grep -q '"oidc/"'; then
    vault auth enable oidc
  fi

  OIDC_CLIENT_SECRET=$(vault kv get -field=auth.clients.vault.secret secret/api)

  # `oidc_discovery_url` is validated at write time. Tolerate failure
  # for the first run on a cold cluster (api not yet Ready); the next
  # reconcile re-runs this Job idempotently.
  if vault write auth/oidc/config \
      oidc_discovery_url="https://esa-blueshell.nl/api" \
      oidc_client_id="vault" \
      oidc_client_secret="${OIDC_CLIENT_SECRET}" \
      default_role="admin"; then
    # `bound_claims` is defence-in-depth: the api already 403s non-admins
    # at /oauth2/authorize. `roles` is emitted as Role.name ("ADMIN").
    #
    # bound_claims must arrive at Vault as a map, not a JSON-encoded
    # string. `vault write key=value` always serialises the value as a
    # string, so the entire role payload is posted as a JSON request
    # body via `@-` (read from stdin) instead — that keeps the nested
    # object typed correctly. Without this the OIDC plugin rejects
    # the write with: `error converting input for field "bound_claims":
    # '' expected a map, got 'string'`.
    vault write auth/oidc/role/admin - <<'JSON'
{
  "bound_audiences": "vault",
  "allowed_redirect_uris": "https://vault.esa-blueshell.nl/ui/vault/auth/oidc/oidc/callback,http://localhost:8250/oidc/callback",
  "user_claim": "sub",
  "groups_claim": "groups",
  "oidc_scopes": "openid,profile,email,groups",
  "bound_claims": {"roles": ["ADMIN"]},
  "token_policies": "admin"
}
JSON
  else
    echo "auth/oidc/config write failed (api OIDC discovery URL likely not"
    echo "reachable yet — fresh cluster, apps-stateless not Ready). Skipping"
    echo "OIDC role write; the next reconcile re-runs this Job idempotently."
  fi

  unset OIDC_CLIENT_SECRET
else
  echo "secret/api:auth.clients.vault.secret not seeded yet; skipping OIDC auth method."
  echo "Seed with: scripts/seed-vault-from-env.sh --apply <env-files...> (then re-run this Job via flux reconcile kustomization apps-data)."
fi
