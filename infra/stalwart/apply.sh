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
: "${STALWART_CATCHALL:=board@${STALWART_DOMAIN}}"
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

  # 2. Hostname + default domain + advertised-service hostnames
  #    (idempotent).
  #
  #    `defaultHostname` is just the SMTP greeting / fallback name.
  #    Auto-publish for SRV / autoconfig / autodiscover / MX records
  #    reads each *advertised service's own* `hostname` field, and
  #    falls back to the OS hostname (= pod name in k8s, e.g.
  #    `stalwart-54b9578f55-cttqm`) when that field is null. That
  #    fallback leaks pod names into the public zone and breaks every
  #    mail client doing autodiscovery. Pin every advertised hostname
  #    + the MX target to STALWART_HOSTNAME so the records Stalwart
  #    writes to Cloudflare reference a name that actually resolves
  #    publicly.
  jq -nc --arg host "$STALWART_HOSTNAME" --arg dom "$dom" '
    {
      "@type": "update",
      "object": "SystemSettings",
      "value": {
        defaultHostname: $host,
        defaultDomainId: $dom,
        mailExchangers: {"0": {hostname: $host, priority: 10}},
        services: (
          ["caldav","carddav","imap","jmap","managesieve","pop3","smtp","webdav"]
          | map({(.): {hostname: $host, cleartext: false}})
          | add
        )
      }
    }' | sc apply --file /dev/stdin

  # 3. Wire the domain:
  #    - certificateManagement: Automatic via the ACME provider (DNS-01).
  #    - dnsManagement: Automatic — Stalwart uses the configured
  #      DnsServer for ACME DNS-01 challenges AND for auto-publishing
  #      mail records (DKIM/SPF/MX/DMARC/MTA-STS/TLS-RPT/CAA/autoConfig).
  #      The publishRecords field is intentionally not patched here:
  #      empty `{}` is rejected (`must be at least 1 chars`) and `[]`
  #      is rejected as invalidPatch — the field requires at least one
  #      record type. We let Stalwart keep its default-all-true setting
  #      so it manages those records based on its own model; the
  #      operator-imported records in infra/dns are advance scaffolding
  #      (placeholders / fallbacks) and get authoritatively replaced by
  #      Stalwart's writes once the Domain object is wired up.
  #    - dkimManagement: Automatic — Stalwart generates Dkim1RsaSha256
  #      + Dkim1Ed25519Sha256 keys at first boot, rotates them on a
  #      schedule, and publishes the matching <selector>._domainkey
  #      TXT records via the same DnsServer used for ACME. With Manual
  #      management Stalwart still generates the keys but does not
  #      push the TXT records, leaving outbound mail unsigned — which
  #      was the case before this change (DKIM ✗ in the security check
  #      summary while SPF + DMARC were ✓).
  #    Convergent: webadmin changes get straightened on the next boot.
  printf '{"@type":"update","object":"Domain","id":"%s","value":{"certificateManagement":{"@type":"Automatic","acmeProviderId":"%s"},"dnsManagement":{"@type":"Automatic","dnsServerId":"%s"},"dkimManagement":{"@type":"Automatic"}}}\n' \
    "$dom" "$acme" "$dns" | sc apply --file /dev/stdin

  # 4. Renew the Cloudflare DNS-01 token every boot. Rotating the Vault
  #    secret rolls the pod (VSS rolloutRestartTarget), which re-runs
  #    this step with the new token.
  printf '{"@type":"update","object":"DnsServer","id":"%s","value":{"secret":{"@type":"Value","secret":"%s"}}}\n' \
    "$dns" "$CF_DNS_API_TOKEN" | sc apply --file /dev/stdin

  # 5. Reconcile Vault-managed accounts (passwords, aliases, group
  #    memberships) from accounts.json. Existing accounts get update-
  #    only, never recreate, so the mailbox an account links to is never
  #    disturbed. The one deletion is an empty account whose address
  #    accounts.json now lists as an alias (retire_claimed_accounts). Only list accounts whose
  #    password lives in Vault here; user-managed mailboxes must NOT
  #    appear, or webadmin changes would be overwritten every boot.
  retire_claimed_accounts
  reconcile_accounts "$dom"

  # 6. Unknown addresses go to the catch-all. Set after the accounts so
  #    the target exists.
  printf '{"@type":"update","object":"Domain","id":"%s","value":{"catchAllAddress":"%s"}}\n' \
    "$dom" "$STALWART_CATCHALL" | sc apply --file /dev/stdin

  reconcile_stdout_tracer

  echo "apply: reconcile complete"
}

# Stalwart's only default tracer writes to a file inside the container,
# so pod logs stay empty without this one.
reconcile_stdout_tracer() {
  tracer="$(sc query Tracer --json 2>/dev/null | jq -rs 'map(select(."@type"=="Stdout"))[0].id // empty')"
  fields='{"enable":true,"level":"info","ansi":false,"multiline":false,"buffered":false,"lossy":false,"events":{},"eventsPolicy":"exclude"}'
  if [ -n "$tracer" ]; then
    jq -nc --arg id "$tracer" --argjson f "$fields" \
      '{"@type":"update","object":"Tracer",id:$id,value:$f}' | sc apply --file /dev/stdin
    return 0
  fi
  echo "apply: creating the stdout tracer"
  jq -nc --argjson f "$fields" \
    '{"@type":"create","object":"Tracer","value":{"trc-stdout":({"@type":"Stdout"}+$f)}}' \
    | sc apply --file /dev/stdin
  # Stalwart is already running, so a new tracer needs a reload. A failed
  # reload is not fatal: the next pod start picks the tracer up.
  printf '{"@type":"create","object":"Action","value":{"reload":{"@type":"ReloadSettings"}}}\n' \
    | sc apply --file /dev/stdin \
    || echo "apply: settings reload failed; the stdout tracer starts on the next pod start" >&2
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
      aliases="$(printf '%s' "$entry" | jq -c --arg dom "$dom" --arg held "$HELD" \
        '.aliases | map(split("@")[0]) | map(select(. as $a | ($held | split(" ") | index($a)) | not))
          | to_entries
          | map({(.key|tostring): {name:.value, domainId:$dom, enabled:true}})
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

    reconcile_forward "${lp}@${STALWART_DOMAIN}" "$pw" "$(printf '%s' "$entry" | jq -r '.forwardTo // empty')"
  done
}

# An address accounts.json lists as an alias can still be an account of its
# own from before it was folded into another. That account is deleted only
# when its mailbox is empty; one holding mail stays, and its address is kept
# out of the aliases (HELD) until the mail is moved. Aliases accounts.json
# gives to another account are taken off any account it does not manage.
HELD=""
retire_claimed_accounts() {
  managed="$(jq -r '.[].localPart' "$ACCOUNTS_FILE")"
  claimed="$(jq -r '.[] | .aliases[]? | split("@")[0]' "$ACCOUNTS_FILE")"
  for id in $(sc query Account --json 2>/dev/null | jq -r '.id'); do
    acct="$(sc get Account "$id" --json 2>/dev/null)" || continue
    email="$(printf '%s' "$acct" | jq -r '.emailAddress // empty')"
    [ "${email#*@}" = "$STALWART_DOMAIN" ] || continue
    lp="${email%@*}"
    printf '%s\n' "$managed" | grep -qx "$lp" && continue

    if printf '%s\n' "$claimed" | grep -qx "$lp"; then
      used="$(printf '%s' "$acct" | jq -r '.usedDiskQuota // 0')"
      if [ "$used" = "0" ]; then
        echo "apply: deleting account $email: its mailbox is empty and accounts.json makes it an alias"
        sc delete Account --ids "$id" >/dev/null
        continue
      fi
      echo "apply: keeping account $email: its mailbox holds $used bytes; move or delete that mail and the next start makes it an alias" >&2
      HELD="$HELD $lp"
    fi

    kept="$(printf '%s' "$acct" | jq -c --arg c "$claimed $managed" \
      '[.aliases // {} | to_entries[] | .value | select(.name as $n | ($c | split("\n") | join(" ") | split(" ") | index($n)) | not)]')"
    if [ "$(printf '%s' "$kept" | jq 'length')" != "$(printf '%s' "$acct" | jq '.aliases // {} | length')" ]; then
      echo "apply: taking the aliases accounts.json gives elsewhere off $email"
      printf '%s' "$kept" | jq -c --arg id "$id" \
        '{"@type":"update","object":"Account",id:$id,value:{aliases:(to_entries | map({(.key|tostring): .value}) | add // {})}}' \
        | sc apply --file /dev/stdin
    fi
  done
}

# jmap_as ADDRESS PASSWORD posts the JMAP request on stdin as that account.
jmap_as() {
  curl -fsS -u "$1:$2" -H 'Content-Type: application/json' --data-binary @- "${STALWART_URL}/jmap/"
}

# The account's one managed Sieve script, named forward-<hash of its text>,
# redirects a copy of every message not marked as spam to FORWARD; the mail
# stays in the account too. Stalwart sends the copy with the account as
# envelope sender, so SPF passes for our domain. Spam is not forwarded: the
# catch-all feeds board@, and spam sent on to another provider costs our
# reputation. The X-Spam-Status header is written after user scripts run,
# so the script asks the filter through spamtest instead. Activating the script deactivates any other on the account.
# Without FORWARD the managed script is removed. Failures are logged, never
# fatal: mail still arrives without the forward.
reconcile_forward() {
  addr="$1" pw="$2" target="$3"
  session="$(curl -fsS -u "$addr:$pw" "${STALWART_URL}/jmap/session" 2>/dev/null)" \
    || { echo "apply: cannot sign in as $addr to manage its forward" >&2; return 0; }
  acc="$(printf '%s' "$session" | jq -r '.primaryAccounts["urn:ietf:params:jmap:sieve"] // empty')"
  [ -n "$acc" ] || { echo "apply: $addr has no Sieve account" >&2; return 0; }
  using='["urn:ietf:params:jmap:core","urn:ietf:params:jmap:sieve"]'
  scripts="$(jq -nc --argjson u "$using" --arg a "$acc" '{using:$u,methodCalls:[["SieveScript/get",{accountId:$a},"0"]]}' \
    | jmap_as "$addr" "$pw" | jq -c '.methodResponses[0][1].list // []')" \
    || { echo "apply: cannot read the Sieve scripts of $addr" >&2; return 0; }

  name=""
  if [ -n "$target" ]; then
    text="$(printf 'require ["copy", "spamtest", "relational", "comparator-i;ascii-numeric"];\n# Managed by the stalwart-apply sidecar from infra/stalwart/accounts.json.\nif spamtest :value "ge" :comparator "i;ascii-numeric" "5" {\n  stop;\n}\nredirect :copy "%s";\n' "$target")"
    name="forward-$(printf '%s' "$text" | sha256sum | cut -c1-8)"
    if [ "$(printf '%s' "$scripts" | jq --arg n "$name" 'map(select(.name == $n and .isActive)) | length')" = "0" ]; then
      blob="$(printf '%s' "$text" | curl -fsS -u "$addr:$pw" -H 'Content-Type: application/sieve' --data-binary @- \
        "${STALWART_URL}/jmap/upload/${acc}/" | jq -r '.blobId // empty')"
      [ -n "$blob" ] || { echo "apply: could not upload the forward script for $addr" >&2; return 0; }
      created="$(jq -nc --argjson u "$using" --arg a "$acc" --arg n "$name" --arg b "$blob" \
        '{using:$u,methodCalls:[["SieveScript/set",{accountId:$a,create:{"f":{name:$n,blobId:$b}},onSuccessActivateScript:"#f"},"0"]]}' \
        | jmap_as "$addr" "$pw" | jq -r '.methodResponses[0][1].created.f.id // empty')"
      [ -n "$created" ] || { echo "apply: Stalwart refused the forward script for $addr" >&2; return 0; }
      echo "apply: $addr forwards to $target"
    fi
  fi

  old="$(printf '%s' "$scripts" | jq -c --arg n "$name" '[.[] | select((.name | startswith("forward-")) and .name != $n) | .id]')"
  [ "$old" = "[]" ] && return 0
  # An active script cannot be destroyed, so with no forward left it is
  # deactivated in a request of its own first.
  if [ -z "$target" ]; then
    jq -nc --argjson u "$using" --arg a "$acc" '{using:$u,methodCalls:[["SieveScript/set",{accountId:$a,onSuccessDeactivateScript:true},"0"]]}' \
      | jmap_as "$addr" "$pw" >/dev/null || { echo "apply: could not deactivate the forward script of $addr" >&2; return 0; }
  fi
  jq -nc --argjson u "$using" --arg a "$acc" --argjson ids "$old" \
    '{using:$u,methodCalls:[["SieveScript/set",{accountId:$a,destroy:$ids},"0"]]}' \
    | jmap_as "$addr" "$pw" >/dev/null || { echo "apply: could not remove the old forward script of $addr" >&2; return 0; }
  [ -n "$target" ] || echo "apply: $addr no longer forwards"
}

wait_ready
reconcile
echo "apply: idling (re-runs on next pod start)"
exec sleep infinity
