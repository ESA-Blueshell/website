#!/bin/sh
# stalwart-apply sidecar: reconciles the declarative settings Stalwart
# stores in its RocksDB datastore (listeners, domain wiring, ACME/DNS
# providers, Vault-managed accounts) and renews the Cloudflare DNS-01
# token on every pod start. Runs alongside the stalwart container; a
# VaultStaticSecret rolloutRestartTarget restarts the pod when a secret
# rotates, so this re-runs with the fresh value.
#
# Idempotent: pure-create settings come from the plan template and are
# applied only when no AcmeProvider exists yet (first boot on a fresh
# datastore). Domain wiring, hostname, CF token and account credentials
# are reconciled with query-then-update so they converge regardless of
# the server-assigned ids in the datastore.
#
# `stalwart-cli` is baked into the stalwart-tools image at a pinned
# version; this script trusts that and never downloads at runtime.
set -eu

: "${STALWART_URL:=http://127.0.0.1:8080}"
: "${STALWART_USER:?}"
: "${STALWART_PASSWORD:?}"
: "${STALWART_DOMAIN:?}"
: "${STALWART_HOSTNAME:?}"
: "${CF_DNS_API_TOKEN:?}"
: "${PLAN_TEMPLATE:=/opt/stalwart-tools/plan.ndjson.tmpl}"
: "${ACCOUNTS_FILE:=/opt/stalwart-tools/accounts.json}"
# Directory containing one file per Vault key, mounted from the
# stalwart-secrets Secret. accounts.json's `vaultKey` field names the
# file to read (e.g. `account.api`).
: "${ACCOUNT_PASSWORDS_DIR:=/etc/stalwart-accounts}"

export STALWART_URL STALWART_USER STALWART_PASSWORD

command -v stalwart-cli >/dev/null 2>&1 || {
  echo "apply: stalwart-cli missing from image; rebuild stalwart-tools" >&2
  exit 1
}

sc() { stalwart-cli "$@"; }

# `query --json` emits NDJSON (one object per line). Slurp with `jq -s`
# so the parse is robust for zero, one, or many results.

# id of the first object of a type, or empty string.
first_id() {
  sc query "$1" --json 2>/dev/null | jq -rs '.[0].id // empty'
}

# id of the object of a type whose `name` matches, or empty string.
id_by_name() {
  sc query "$1" --json 2>/dev/null | jq -rs --arg n "$2" 'map(select(.name==$n))[0].id // empty'
}

# id of the Account whose emailAddress matches (the Account list
# projection exposes emailAddress, not name), or empty string.
id_by_email() {
  sc query Account --json 2>/dev/null | jq -rs --arg e "$1" 'map(select(.emailAddress==$e))[0].id // empty'
}

wait_ready() {
  echo "apply: waiting for ${STALWART_URL}/admin/ ..."
  i=0
  until curl -fsS -o /dev/null "${STALWART_URL}/admin/"; do
    i=$((i + 1))
    [ "$i" -gt 120 ] && { echo "apply: stalwart never became ready" >&2; exit 1; }
    sleep 2
  done
}

reconcile() {
  # 1. Pure-create settings on a fresh datastore: DnsServer (Cloudflare),
  #    AcmeProvider (Let's Encrypt DNS-01), Domain, and the three
  #    plaintext/STARTTLS listeners (587, 143, 110). All other listeners
  #    (25/465/993/995/4190/8080/443) come from Stalwart's built-in
  #    defaults and are already bound when the apply runs.
  if [ -z "$(first_id AcmeProvider)" ]; then
    echo "apply: applying base settings plan"
    envsubst < "$PLAN_TEMPLATE" > /tmp/plan.ndjson
    sc apply --file /tmp/plan.ndjson
  fi

  dom="$(id_by_name Domain "${STALWART_DOMAIN}")"
  acme="$(first_id AcmeProvider)"
  dns="$(first_id DnsServer)"

  if [ -z "$dom" ]; then
    echo "apply: domain ${STALWART_DOMAIN} not found after plan apply — check plan.ndjson.tmpl Domain creation" >&2
    return 1
  fi

  # 2. Hostname + default domain (idempotent).
  printf '{"@type":"update","object":"SystemSettings","value":{"defaultHostname":"%s","defaultDomainId":"%s"}}\n' \
    "$STALWART_HOSTNAME" "$dom" | sc apply --file /dev/stdin

  # 3. Wire the domain:
  #    - certificateManagement: Automatic via the ACME provider (DNS-01).
  #    - dnsManagement: Automatic with publishRecords:[] — Stalwart keeps
  #      its DnsServer ref for ACME challenges, but does not auto-publish
  #      SPF / DMARC / MX / DKIM / MTA-STS / TLS-RPT / CAA / autoconfig
  #      records. Those live in the operator-managed zone file
  #      (infra/dns) so the Cloudflare import is the single source of
  #      truth and Stalwart never fights the zone.
  #    - dkimManagement: Manual — reconcile_dkim below owns the
  #      DkimSignature lifecycle, and the matching DNS TXT record is
  #      operator-published; Stalwart's automatic rotation logic would
  #      just clash with both.
  #    Convergent: webadmin changes get straightened on the next boot.
  printf '{"@type":"update","object":"Domain","id":"%s","value":{"certificateManagement":{"@type":"Automatic","acmeProviderId":"%s"},"dnsManagement":{"@type":"Automatic","dnsServerId":"%s","publishRecords":[]},"dkimManagement":{"@type":"Manual"}}}\n' \
    "$dom" "$acme" "$dns" | sc apply --file /dev/stdin

  # 4. Renew the Cloudflare DNS-01 token every boot. Rotating the Vault
  #    secret rolls the pod (VSS rolloutRestartTarget), which re-runs
  #    this step with the new token.
  printf '{"@type":"update","object":"DnsServer","id":"%s","value":{"secret":{"@type":"Value","secret":"%s"}}}\n' \
    "$dns" "$CF_DNS_API_TOKEN" | sc apply --file /dev/stdin

  # 5. DKIM signing — create one Dkim1RsaSha256 signature for the
  #    domain using the private key from secret/platform/mail. No-op
  #    once a DkimSignature exists; rotation is operator-driven.
  reconcile_dkim "$dom"

  # 6. Reconcile Vault-managed accounts (passwords, aliases, group
  #    memberships) from accounts.json. Existing accounts get update-
  #    only — never delete and never recreate — so the mailbox an
  #    account links to is never disturbed. Only list accounts whose
  #    password lives in Vault here; user-managed mailboxes must NOT
  #    appear, or webadmin changes would be overwritten every boot.
  reconcile_accounts "$dom"

  echo "apply: reconcile complete"
}

reconcile_dkim() {
  # $1 is the Domain object id. Creates a Dkim1RsaSha256 signature
  # with selector "default", using the PEM-encoded private key from
  # secret/platform/mail (key `dkim-private-key`). The Vault value
  # must be the raw PEM with literal -----BEGIN PRIVATE KEY-----
  # markers — write it with `vault kv put -mount=secret platform/mail
  # dkim-private-key=@/path/to/key.pem`.
  #
  # No-op when a DkimSignature already exists for this domain. To
  # rotate: delete the existing DkimSignature via webadmin or CLI
  # (`stalwart-cli delete DkimSignature <id>`), bump the Vault key to
  # the new private key, restart the pod. The matching DNS TXT record
  # (`default._domainkey`) must be published manually in Cloudflare
  # from the resulting publicKey field — `infra/dns` ships a
  # placeholder ready to receive it.
  dom="$1"
  pkey_file="${ACCOUNT_PASSWORDS_DIR}/dkim-private-key"
  if [ ! -s "$pkey_file" ]; then
    echo "apply: skipping DKIM: dkim-private-key missing from secret/platform/mail (mail will deliver unsigned)" >&2
    return 0
  fi

  existing="$(sc query DkimSignature --json 2>/dev/null \
    | jq -rs --arg d "$dom" 'map(select(.domainId==$d))[0].id // empty')"
  if [ -n "$existing" ]; then
    echo "apply: DkimSignature already exists for ${STALWART_DOMAIN} (id=${existing}) — skipping"
    return 0
  fi

  echo "apply: creating DkimSignature (selector=default, algorithm=Dkim1RsaSha256)"
  pkey="$(cat "$pkey_file")"
  jq -nc --arg dom "$dom" --arg pk "$pkey" \
    '{"@type":"create","object":"DkimSignature","value":{"dkim-rsa":{"@type":"Dkim1RsaSha256",domainId:$dom,selector:"default",privateKey:$pk}}}' \
    | sc apply --file /dev/stdin
  echo "apply: DkimSignature created. Read the public key for DNS publishing with:"
  echo "  stalwart-cli query DkimSignature --fields selector publicKey"
}

# objectList/set values are encoded by the apply API as index-keyed maps,
# not JSON arrays.
reconcile_accounts() {
  # $1 is the Domain object id (used for domainId references); email
  # addresses are formed from the domain NAME in $STALWART_DOMAIN.
  dom="$1"
  count="$(jq 'length' "$ACCOUNTS_FILE")"
  if [ "$count" = "0" ]; then
    echo "apply: accounts.json is empty — nothing to reconcile"
    return 0
  fi
  i=0
  while [ "$i" -lt "$count" ]; do
    entry="$(jq -c ".[$i]" "$ACCOUNTS_FILE")"
    i=$((i + 1))
    lp="$(printf '%s' "$entry" | jq -r '.localPart')"
    vault_key="$(printf '%s' "$entry" | jq -r '.vaultKey')"
    pw_file="${ACCOUNT_PASSWORDS_DIR}/${vault_key}"
    if [ ! -s "$pw_file" ]; then
      echo "apply: skipping ${lp}: ${pw_file} missing or empty (seed ${vault_key} in secret/platform/mail)" >&2
      continue
    fi
    # Strip a single trailing newline if the secret was written with
    # `vault kv put …=@file` from a CLI tool that appended one.
    pw="$(awk 'NR==1{printf "%s", $0; next} {printf "\n%s", $0}' "$pw_file")"

    # Always set the password. aliases/memberGroupIds are only touched
    # when the entry explicitly declares them, so password-only entries
    # never clear aliases/groups set via the webadmin.
    fields="$(jq -nc --arg pw "$pw" '{credentials:{"0":{"@type":"Password",secret:$pw}}}')"

    if printf '%s' "$entry" | jq -e 'has("aliases")' >/dev/null; then
      aliases="$(printf '%s' "$entry" | jq -c --arg dom "$dom" \
        '.aliases | to_entries
          | map({(.key|tostring): {name:(.value|split("@")[0]), domainId:$dom, enabled:true}})
          | add // {}')"
      fields="$(printf '%s' "$fields" | jq -c --argjson a "$aliases" '. + {aliases:$a}')"
    fi

    if printf '%s' "$entry" | jq -e 'has("groups")' >/dev/null; then
      gids='{}'
      for g in $(printf '%s' "$entry" | jq -r '.groups[]? // empty'); do
        gid="$(id_by_email "${g}@${STALWART_DOMAIN}")"
        [ -n "$gid" ] && gids="$(printf '%s' "$gids" | jq -c --arg id "$gid" '. + {($id):true}')"
      done
      fields="$(printf '%s' "$fields" | jq -c --argjson g "$gids" '. + {memberGroupIds:$g}')"
    fi

    acct="$(id_by_email "${lp}@${STALWART_DOMAIN}")"
    if [ -z "$acct" ]; then
      echo "apply: creating account ${lp}@${STALWART_DOMAIN}"
      jq -nc --arg lp "$lp" --arg dom "$dom" --argjson f "$fields" \
        '{"@type":"create","object":"Account","value":{"acct":({"@type":"User",name:$lp,domainId:$dom}+$f)}}' \
        | sc apply --file /dev/stdin
    else
      echo "apply: updating account ${lp}@${STALWART_DOMAIN}"
      jq -nc --arg id "$acct" --argjson f "$fields" \
        '{"@type":"update","object":"Account",id:$id,value:$f}' \
        | sc apply --file /dev/stdin
    fi
  done
}

wait_ready
reconcile
echo "apply: idling (re-runs on next pod start)"
exec sleep infinity
