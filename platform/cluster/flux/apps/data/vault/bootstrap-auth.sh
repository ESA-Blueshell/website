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
# external-dns), platform/api (api secrets), platform/listmonk (listmonk
# admin + SMTP), platform/mail (stalwart admin + DKIM),
# platform/ghcr (GitHub PAT for pulling private ghcr.io images).
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
# operator seeds it via `secret/platform/mariadb.{user,password}` the
# first time, then this block picks it up. The block short-circuits
# until that secret exists so a fresh Vault install does not block the
# bootstrap Job on an unsatisfiable precondition.

if ! vault secrets list -format=json | grep -q '"database/"'; then
  vault secrets enable database
fi

if vault kv get secret/platform/mariadb >/dev/null 2>&1; then
  DB_ADMIN_USER=$(vault kv get -field=user secret/platform/mariadb)
  DB_ADMIN_PASS=$(vault kv get -field=password secret/platform/mariadb)

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
  echo "Seed with: vault kv put secret/platform/mariadb user=<admin> password=<secret>"
fi
