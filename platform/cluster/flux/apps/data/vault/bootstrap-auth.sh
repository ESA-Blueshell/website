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

# --- Policies -----------------------------------------------------------

cat <<'EOF' >/tmp/api.hcl
path "secret/data/api" {
  capabilities = ["read"]
}

path "secret/data/api/*" {
  capabilities = ["read"]
}

# Spring Cloud Vault's KV backend probes secret/<spring.application.name>
# (= "BlueshellAPI"), secret/<...>/<active-profile>, secret/application,
# and secret/application/<active-profile> on every startup. None of those
# paths are seeded — but a 403 on the lookup is fatal to the boot
# sequence (the Vault property source aborts), while a 404 is a benign
# INFO log. Granting read here turns the deny-by-default into a 404.
# These four paths are intentionally never written.
path "secret/data/application" {
  capabilities = ["read"]
}

path "secret/data/application/*" {
  capabilities = ["read"]
}

path "secret/data/BlueshellAPI" {
  capabilities = ["read"]
}

path "secret/data/BlueshellAPI/*" {
  capabilities = ["read"]
}

path "database/creds/api" {
  capabilities = ["read"]
}

path "transit/sign/api-jwt" {
  capabilities = ["update"]
}

path "transit/keys/api-jwt" {
  capabilities = ["read"]
}
EOF

cat <<'EOF' >/tmp/listmonk.hcl
path "secret/data/listmonk" {
  capabilities = ["read"]
}

path "secret/data/listmonk/*" {
  capabilities = ["read"]
}

path "database/creds/listmonk" {
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
# them — platform/edge (Cloudflare DNS-01 token for cert-manager and
# external-dns), api (third-party integration keys — Brevo, Mollie,
# Google Calendar, Facebook, X, Discord, plus jwt-secret and the Vault
# OIDC client secret), listmonk (postgres + admin + SMTP passwords),
# platform/mail (stalwart admin, bounce mailbox, DKIM),
# platform/ghcr (GitHub PAT for pulling private ghcr.io images).
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

path "secret/data/api" {
  capabilities = ["read"]
}

path "secret/data/listmonk" {
  capabilities = ["read"]
}

path "secret/data/platform/mail" {
  capabilities = ["read"]
}

path "secret/data/platform/ghcr" {
  capabilities = ["read"]
}

path "secret/data/platform/mariadb" {
  capabilities = ["read"]
}
EOF

vault policy write api /tmp/api.hcl
vault policy write listmonk /tmp/listmonk.hcl
vault policy write stalwart /tmp/stalwart.hcl
vault policy write vso /tmp/vso.hcl
vault policy write admin /tmp/admin.hcl

# --- Kubernetes auth roles ---------------------------------------------

vault write auth/kubernetes/role/api \
  bound_service_account_names="api" \
  bound_service_account_namespaces="default" \
  policies="api" \
  ttl="1h"

vault write auth/kubernetes/role/listmonk \
  bound_service_account_names="listmonk" \
  bound_service_account_namespaces="default" \
  policies="listmonk" \
  ttl="1h"

vault write auth/kubernetes/role/stalwart \
  bound_service_account_names="stalwart" \
  bound_service_account_namespaces="mail-system" \
  policies="stalwart" \
  ttl="1h"

vault write auth/kubernetes/role/vso \
  bound_service_account_names="vault-secrets-operator" \
  bound_service_account_namespaces="vso-system,cert-manager,external-dns,default,mail-system,data-system" \
  policies="vso" \
  ttl="1h"

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

  vault write database/roles/api \
    db_name=mariadb \
    default_ttl="72h" \
    max_ttl="168h" \
    creation_statements="CREATE USER '{{name}}'@'%' IDENTIFIED BY '{{password}}'; GRANT ALL ON blueshell.* TO '{{name}}'@'%';"

  unset DB_ADMIN_USER DB_ADMIN_PASS
else
  echo "secret/platform/mariadb not seeded yet — skipping database/config/mariadb."
  echo "Seed with: vault kv put secret/platform/mariadb root-password=<secret> user=<app-user> password=<app-secret> admin-user=<db-admin> admin-password=<db-admin-secret>"
fi

# --- OIDC auth method (vault login -method=oidc) ------------------------
# Short-circuit when the client secret is unseeded so a fresh Vault
# install doesn't fail the Job on an unsatisfiable precondition.

if vault kv get -field=vault-oidc-client-secret secret/api >/dev/null 2>&1; then
  if ! vault auth list -format=json | grep -q '"oidc/"'; then
    vault auth enable oidc
  fi

  OIDC_CLIENT_SECRET=$(vault kv get -field=vault-oidc-client-secret secret/api)

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
    vault write auth/oidc/role/admin \
      bound_audiences="vault" \
      allowed_redirect_uris="https://vault.esa-blueshell.nl/ui/vault/auth/oidc/oidc/callback" \
      user_claim="sub" \
      groups_claim="groups" \
      oidc_scopes="openid,profile,email,groups" \
      bound_claims='{"roles":["ADMIN"]}' \
      token_policies="admin"
  else
    echo "auth/oidc/config write failed (api OIDC discovery URL likely not"
    echo "reachable yet — fresh cluster, apps-stateless not Ready). Skipping"
    echo "OIDC role write; the next reconcile re-runs this Job idempotently."
  fi

  unset OIDC_CLIENT_SECRET
else
  echo "secret/api:vault-oidc-client-secret not seeded yet — skipping OIDC auth method."
  echo "Seed with: scripts/seed-vault-from-env.sh --apply <env-files...> (then re-run this Job via flux reconcile kustomization apps-data)."
fi
