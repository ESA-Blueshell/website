#!/usr/bin/env bash
# shellcheck source-path=SCRIPTDIR
#
# Checks everything other mail servers judge esa-blueshell.nl's mail by, and
# walks you through the fixes that live outside the repository: DNS (MX, SPF,
# DKIM against the selectors Stalwart signs with, DMARC, MTA-STS, TLS-RPT), the
# MTA-STS policy, reverse DNS, the SMTP greeting and certificate, blocklists,
# the postmaster, abuse and catch-all addresses, recent Outlook.com rejections,
# and Microsoft's sender programs. It reads only, except for the steps you do in
# a browser, which it remembers once you confirm them. Safe to re-run.
#
#   platform/bootstrap/mail.sh
#
# Without the blueshell kubectl context the Stalwart checks are skipped and the
# public ones still run. KUBE_CONTEXT names another context.

here="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=lib/wizard.sh
source "$here/lib/wizard.sh"
# shellcheck source=lib/blueshell.sh
source "$here/lib/blueshell.sh"
require_bash4

EMBEDDED=""
case "${1:-}" in
  "") ;;
  --embedded) EMBEDDED=yes ;;   # started by cluster.sh, which already confirmed the cluster
  -h|--help) sed -n '3,15p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
  *) die "unknown argument: $1" ;;
esac

require_tools dig curl jq openssl nc perl
RESOLVER=${MAIL_CHECK_RESOLVER:-1.1.1.1}
PROBLEMS=0
problem() { warn "$1"; PROBLEMS=$((PROBLEMS + 1)); }
later() { warn "$1"; SKIPPED+=("$1"); }
q() { dig +short +time=3 +tries=2 "@$RESOLVER" "$@" 2>/dev/null; }
txt() { q TXT "$1" | sed 's/" "//g; s/^"//; s/"$//'; }
# with_timeout SECONDS CMD...: macOS has no timeout(1).
with_timeout() { perl -e 'alarm shift; exec @ARGV' "$@"; }

CLUSTER=""
HAVE_CONTEXT=""
sc_query() { # sc_query ARGS...: stalwart-cli in the apply sidecar, which carries the admin login
  k -n mail-system exec deploy/stalwart -c stalwart-apply -- sh -c 'HOME=/tmp exec stalwart-cli "$@"' sh "$@" 2>/dev/null
}

# ── stages ────────────────────────────────────────────────────────────────

stage_cluster() {
  stage "Stalwart: reach it through the cluster"
  if ! kubectl config get-contexts -o name 2>/dev/null | grep -qx "$CONTEXT"; then
    note "no kubectl context $CONTEXT: the Stalwart checks are skipped"
    return 0
  fi
  if [[ -n "$EMBEDDED" ]]; then export _BLUESHELL_CLUSTER_CONFIRMED=yes; fi
  require_cluster
  HAVE_CONTEXT=yes
  if sc_query query Domain --json >/dev/null; then
    CLUSTER=yes
    ok "stalwart-cli answers in the apply sidecar"
  else
    later "stalwart-cli does not answer in mail-system/stalwart: the Stalwart checks are skipped"
  fi
}

stage_dns() {
  local mx ips spf dmarc rua sel type rec sts tlsrpt
  stage "DNS: MX, SPF, DKIM, DMARC, MTA-STS and TLS-RPT (resolver $RESOLVER)"
  mx=$(q MX "$DOMAIN" | sort -n | awk 'NR == 1 {print $2}')
  [[ -n "$mx" ]] || { problem "$DOMAIN has no MX record"; }
  if [[ -n "$mx" ]]; then
    ips="$(q A "$mx") $(q AAAA "$mx")"
    if [[ " $ips " == *" $SERVER_V4 "* && " $ips " == *" $SERVER_V6 "* ]]; then
      ok "MX $mx reaches $SERVER_V4 and $SERVER_V6"
    else
      problem "MX $mx resolves to $ips, not $SERVER_V4 and $SERVER_V6"
    fi
  fi
  ips="$(q A "$MAIL_HOST") $(q AAAA "$MAIL_HOST")"
  if [[ " $ips " == *" $SERVER_V4 "* && " $ips " == *" $SERVER_V6 "* ]]; then
    ok "$MAIL_HOST is $SERVER_V4 and $SERVER_V6"
  else
    problem "$MAIL_HOST resolves to $ips (proxied through Cloudflare? mail needs it DNS-only)"
  fi

  spf=$(txt "$DOMAIN" | grep '^v=spf1' || true)
  if [[ -z "$spf" ]]; then
    problem "$DOMAIN publishes no SPF record"
  elif [[ "$(grep -c . <<<"$spf")" -gt 1 ]]; then
    problem "$DOMAIN publishes several SPF records, which fails SPF: $spf"
  elif [[ "$spf" == *" mx"* || "$spf" == *"ip4:$SERVER_V4"* ]]; then
    ok "SPF: $spf"
  else
    problem "SPF does not authorise the mail server: $spf"
  fi

  dmarc=$(txt "_dmarc.$DOMAIN" | grep '^v=DMARC1' || true)
  if [[ -z "$dmarc" ]]; then
    problem "_dmarc.$DOMAIN publishes no DMARC record"
  else
    ok "DMARC: $dmarc"
    rua=$(sed -n 's/.*rua=mailto:\([^;, ]*\).*/\1/p' <<<"$dmarc")
    [[ -n "$rua" ]] && RUA="$rua"
  fi

  if [[ -n "$CLUSTER" ]]; then
    while IFS=$'\t' read -r sel type; do
      [[ -n "$sel" ]] || continue
      rec=$(txt "$sel._domainkey.$DOMAIN")
      if [[ "$rec" != *"p="* || "$rec" == *"p=;"* || "$rec" == *"p=" ]]; then
        problem "Stalwart signs with $sel, but $sel._domainkey.$DOMAIN publishes no key"
      elif [[ "$type" == *Ed25519* && "$rec" != *"k=ed25519"* ]]; then
        problem "$sel is an ed25519 key, but its DNS record is not k=ed25519"
      else
        ok "DKIM $sel ($type) is published"
      fi
    done < <(sc_query query DkimSignature --json | jq -r 'select(.stage == "active") | [.selector, ."@type"] | @tsv')
  else
    note "DKIM: without Stalwart the selectors to check are unknown"
  fi

  sts=$(txt "_mta-sts.$DOMAIN")
  if [[ "$sts" == v=STSv1* ]]; then ok "MTA-STS record: $sts"; else problem "_mta-sts.$DOMAIN publishes no MTA-STS record"; fi
  tlsrpt=$(txt "_smtp._tls.$DOMAIN")
  if [[ "$tlsrpt" == v=TLSRPTv1* ]]; then ok "TLS-RPT: $tlsrpt"; else problem "_smtp._tls.$DOMAIN publishes no TLS-RPT record"; fi
  note "Stalwart publishes and renews these records itself (dnsManagement Automatic): a missing one"
  note "usually means the Cloudflare token in secret/platform/edge stopped working."
}

# authoritative HOST prints HOST's first A record from the zone's own name
# server, which a public resolver may still have cached as missing.
authoritative() {
  local ns
  ns=$(dig +short NS "$DOMAIN" | head -n 1)
  dig +short +time=3 +tries=2 "@${ns:-$RESOLVER}" "$1" A 2>/dev/null | grep -E '^[0-9.]+$' | head -n 1
}

# fetch_policy pins the request to the address the zone gives, so a resolver
# that cached the name as missing cannot hide a record made seconds ago.
fetch_policy() {
  local host="mta-sts.$DOMAIN" ip
  ip=$(authoritative "$host")
  [[ -n "$ip" ]] || return 0
  curl -s --max-time 10 --resolve "$host:443:$ip" "https://$host/.well-known/mta-sts.txt" || true
}

# mta_sts_repair walks the chain from repository to sender: the IngressRoute
# Flux applies, the DNS record external-dns makes from it, and the file
# Stalwart serves behind it. It fixes the first broken link it can, and
# succeeds once a fetch could work.
mta_sts_repair() {
  local host="mta-sts.$DOMAIN" logs served image _
  if [[ -z "$HAVE_CONTEXT" ]]; then
    note "without the cluster the cause cannot be looked into"
    return 1
  fi
  if k -n edge-system get ingressroute mta-sts >/dev/null 2>&1; then
    ok "the IngressRoute edge-system/mta-sts exists"
  else
    warn "the IngressRoute edge-system/mta-sts does not exist"
    command -v flux >/dev/null 2>&1 || { warn "the flux CLI is not on PATH, so Flux cannot be asked to apply apps-edge"; return 1; }
    confirm "Ask Flux to apply apps-edge from main now?" || return 1
    flux --context "$CONTEXT" -n flux-system reconcile kustomization apps-edge --with-source --timeout=3m >/dev/null 2>&1 \
      || { warn "Flux could not apply apps-edge: flux --context $CONTEXT get kustomizations"; return 1; }
    k -n edge-system get ingressroute mta-sts >/dev/null 2>&1 \
      || { warn "apps-edge applied without making it: is platform/cluster/flux/apps/edge/ingressroutes/mta-sts.yaml on main?"; return 1; }
    ok "Flux made edge-system/mta-sts"
  fi

  if [[ -n "$(authoritative "$host")" ]]; then
    ok "$host has a DNS record"
  else
    warn "$host has no DNS record, though its IngressRoute exists"
    image=$(k -n external-dns get deploy external-dns -o jsonpath='{.spec.template.spec.containers[0].image}' 2>/dev/null || true)
    say "external-dns makes the record from the IngressRoute; it runs ${image:-an unknown image}."
    if [[ "$image" =~ :v0\.2[2-4]\. ]]; then
      warn "external-dns v0.22 to v0.24 make no record for any IngressRoute (#2635): older records stay, new ones never come"
      say "Pin external-dns to v0.21.0 in platform/cluster/flux/apps/core/external-dns/release.yaml, then run this again."
      return 1
    fi
    logs=$(k -n external-dns logs deploy/external-dns --since=1h 2>/dev/null | grep -iE "level=(error|warn)|$host" | tail -n 3 || true)
    [[ -z "$logs" ]] || cut -c1-200 <<<"$logs" | sed 's/^/    /'
    confirm "Restart external-dns, so it lists every route again?" || return 1
    k -n external-dns rollout restart deploy/external-dns >/dev/null
    k -n external-dns rollout status deploy/external-dns --timeout=180s >/dev/null \
      || { warn "external-dns did not come back: kubectl --context $CONTEXT -n external-dns get pods"; return 1; }
    for _ in $(seq 1 18); do
      [[ -n "$(authoritative "$host")" ]] && break
      sleep 10
    done
    if [[ -z "$(authoritative "$host")" ]]; then
      warn "still no record three minutes after the restart; its log:"
      k -n external-dns logs deploy/external-dns --since=5m 2>/dev/null | tail -n 5 | cut -c1-200 | sed 's/^/    /'
      return 1
    fi
    ok "external-dns made the record for $host"
  fi

  served=$(k -n mail-system exec deploy/stalwart -c stalwart-apply -- \
    curl -fsS --max-time 10 http://127.0.0.1:8080/.well-known/mta-sts.txt 2>/dev/null || true)
  if [[ "$served" != *"version: STSv1"* ]]; then
    warn "Stalwart itself serves no policy at /.well-known/mta-sts.txt: check its MtaSts settings in the admin UI"
    return 1
  fi
  ok "Stalwart serves the policy"
}

stage_mta_sts() {
  local policy
  stage "MTA-STS: the policy file senders fetch"
  policy=$(fetch_policy)
  if [[ "$policy" != *"version: STSv1"* ]]; then
    warn "https://mta-sts.$DOMAIN/.well-known/mta-sts.txt serves no policy; looking for why"
    mta_sts_repair && policy=$(fetch_policy)
  fi
  if [[ "$policy" != *"version: STSv1"* ]]; then
    problem "https://mta-sts.$DOMAIN/.well-known/mta-sts.txt serves no policy"
    return 0
  fi
  ok "the policy is served: $(tr '\r\n' '  ' <<<"$policy" | tr -s ' ')"
  grep -q "^mx: $MAIL_HOST" <<<"$(tr -d '\r' <<<"$policy")" || problem "the policy does not list mx: $MAIL_HOST"
  grep -q "^mode: enforce" <<<"$(tr -d '\r' <<<"$policy")" || note "the policy is not in enforce mode"
}

check_ptr() { # check_ptr IP: succeeds when its PTR is MAIL_HOST and that resolves back to IP
  local ptr
  ptr=$(q -x "$1" | sed 's/\.$//')
  [[ "$ptr" == "$MAIL_HOST" ]] || { printf '%s' "${ptr:-none}"; return 1; }
  [[ " $(q A "$MAIL_HOST") $(q AAAA "$MAIL_HOST") " == *" $1 "* ]] || { printf '%s (does not resolve back)' "$ptr"; return 1; }
}

stage_rdns() {
  local ip got tries=0 bad=()
  stage "Reverse DNS: each sending address names $MAIL_HOST"
  say "Outlook.com and Gmail distrust a server whose address does not name the host it greets as."
  while true; do
    bad=()
    for ip in "$SERVER_V4" "$SERVER_V6"; do
      if got=$(check_ptr "$ip"); then ok "$ip → $MAIL_HOST → $ip"; else warn "$ip → $got"; bad+=("$ip"); fi
    done
    (( ${#bad[@]} == 0 )) && return 0
    tries=$((tries + 1))
    if (( tries > 2 )); then
      problem "reverse DNS is still wrong for ${bad[*]}: it can take up to a day to show"
      return 0
    fi
    say "Contabo sets the reverse DNS of its addresses in the customer panel:"
    step "Log in, then Network Services, DNS Management, and its reverse DNS section."
    for ip in "${bad[@]}"; do step "$ip: set the PTR to $MAIL_HOST"; done
    step "An IPv6 address needs \"Add a PTR record for an IPv6 address\" first."
    step "Save. The change shows within minutes, sometimes hours."
    open_url "https://my.contabo.com/"
    confirm "Done? Check again now?" || { problem "reverse DNS is wrong for ${bad[*]}"; return 0; }
  done
}

stage_smtp() {
  local greeting cert
  stage "SMTP: the greeting and the STARTTLS certificate on port 25"
  greeting=$( (sleep 3; printf 'QUIT\r\n') | nc -w 6 "$SERVER_V4" 25 2>/dev/null | head -n 1 | tr -d '\r' || true)
  if [[ -z "$greeting" ]]; then
    later "port 25 on $SERVER_V4 does not answer from here (many home networks block it); check from another network"
    return 0
  fi
  if [[ "$greeting" == "220 $MAIL_HOST"* ]]; then ok "greeting: $greeting"; else problem "greeting names something else: $greeting"; fi
  cert=$(with_timeout 15 openssl s_client -starttls smtp -connect "$SERVER_V4:25" -servername "$MAIL_HOST" </dev/null 2>/dev/null \
    | openssl x509 -noout -checkhost "$MAIL_HOST" -enddate 2>/dev/null || true)
  if [[ "$cert" == *"does match"* ]]; then
    ok "the certificate covers $MAIL_HOST, $(grep notAfter <<<"$cert")"
    openssl x509 -checkend $((14 * 86400)) -noout >/dev/null 2>&1 < <(with_timeout 15 openssl s_client -starttls smtp \
      -connect "$SERVER_V4:25" -servername "$MAIL_HOST" </dev/null 2>/dev/null) \
      || problem "the certificate expires within 14 days: Stalwart renews it through ACME, check its logs"
  else
    problem "the STARTTLS certificate does not cover $MAIL_HOST"
  fi
}

stage_blocklists() {
  local rev list ans ns listed=0
  stage "Blocklists: is $SERVER_V4 listed?"
  rev=$(awk -F. '{print $4"."$3"."$2"."$1}' <<<"$SERVER_V4")
  # Spamhaus refuses queries through public resolvers, so it is asked directly.
  ns=$(q NS zen.spamhaus.org | head -n 1)
  ans=$(dig +short +time=3 +tries=2 "@${ns:-a.gns.spamhaus.org}" "$rev.zen.spamhaus.org" A 2>/dev/null || true)
  if [[ "$ans" == 127.255.255.* ]]; then
    note "Spamhaus refused the query ($ans)"
  elif [[ -n "$ans" ]]; then
    problem "listed on Spamhaus ZEN ($ans): https://check.spamhaus.org/results/?query=$SERVER_V4"; listed=1
  else
    ok "not on Spamhaus ZEN"
  fi
  for list in b.barracudacentral.org bl.spamcop.net psbl.surriel.com bl.mailspike.net dnsbl-1.uceprotect.net ix.dnsbl.manitu.net dnsbl.dronebl.org; do
    ans=$(q A "$rev.$list" | grep '^127\.0\.0\.' || true)
    if [[ -n "$ans" ]]; then problem "listed on $list ($ans)"; listed=1; else ok "not on $list"; fi
  done
  (( listed == 0 )) || note "each list's own site has its delisting form; fix the cause before asking"
  note "Microsoft keeps its own list, which no public lookup shows: see the Microsoft stage."
}

stage_addresses() {
  local accounts catchall addr
  stage "Addresses: postmaster, abuse and the catch-all"
  if [[ -z "$CLUSTER" ]]; then note "skipped without Stalwart"; return 0; fi
  accounts=$(sc_query query Account --json | jq -rs 'map(.emailAddress // empty) | join(" ")')
  # The DMARC report address is usually postmaster@ itself: check each once.
  while IFS= read -r addr; do
    if [[ " $accounts " == *" $addr "* ]]; then ok "$addr is a mailbox"; else problem "$addr is not a mailbox (reports to it bounce)"; fi
  done < <(printf '%s\n' "postmaster@$DOMAIN" "${RUA:-}" | awk 'NF && !seen[$0]++')
  if grep -q '"abuse"' "$REPO_ROOT/infra/stalwart/accounts.json"; then
    ok "abuse@$DOMAIN is declared in infra/stalwart/accounts.json"
  else
    problem "abuse@$DOMAIN is not declared in infra/stalwart/accounts.json"
  fi
  catchall=$(sc_query query Domain --json | jq -rs --arg d "$DOMAIN" 'map(select(.name == $d))[0].catchAllAddress // empty')
  if [[ -n "$catchall" ]]; then ok "unknown addresses go to $catchall"; else problem "$DOMAIN has no catch-all: unknown addresses bounce"; fi
}

stage_rejections() {
  local lines
  stage "Recent rejections from Outlook.com and Hotmail"
  if [[ -z "$CLUSTER" ]]; then note "skipped without Stalwart"; return 0; fi
  lines=$(k -n mail-system logs deploy/stalwart -c stalwart --since=72h 2>/dev/null \
    | grep -iE 'outlook|hotmail|live\.(com|nl)|msn\.com' | grep -E '5\.7\.[0-9]|S3150|550 ' | tail -n 5 || true)
  if [[ -n "$lines" ]]; then
    problem "Microsoft rejected mail in the last 72 hours:"
    cut -c1-220 <<<"$lines" | sed 's/^/    /'
    MS_BLOCKED=yes
  else
    ok "no Microsoft rejection in Stalwart's log for the last 72 hours"
    note "the log holds deliveries only once the stdout tracer is on (#2627)"
  fi
}

# ms_done KEY: a step done once in a browser, remembered in ENV_FILE under KEY.
ms_done() { [[ -n "$(_existing "$1" || true)" ]]; }

stage_microsoft() {
  stage "Microsoft: SNDS, JMRP and delisting"
  say "Outlook.com, Hotmail and Live keep their own reputation for each sending address."
  if ms_done MAIL_SNDS_SINCE; then
    ok "SNDS access requested on $(_existing MAIL_SNDS_SINCE)"
  else
    say "SNDS shows Microsoft's view of $SERVER_V4: its spam rate, traps hit and any block."
    step "Sign in with a Microsoft account, Request Access, enter $SERVER_V4."
    step "Pick postmaster@$DOMAIN as the address to confirm with, then follow the link it mails."
    open_url "https://sendersupport.olc.protection.outlook.com/snds/"
    if confirm "Access requested and confirmed?"; then write_env MAIL_SNDS_SINCE "$(date +%F)"; else later "request SNDS access for $SERVER_V4"; fi
  fi
  if ms_done MAIL_JMRP_SINCE; then
    ok "JMRP joined on $(_existing MAIL_JMRP_SINCE)"
  else
    say "JMRP mails a copy of each message an Outlook.com user marks as junk, so a pattern shows early."
    step "Same portal, Junk Mail Reporting Program: add $SERVER_V4, complaints to abuse@$DOMAIN."
    open_url "https://sendersupport.olc.protection.outlook.com/snds/JMRP.aspx"
    if confirm "Joined?"; then write_env MAIL_JMRP_SINCE "$(date +%F)"; else later "join JMRP for $SERVER_V4"; fi
  fi
  if [[ -n "${MS_BLOCKED:-}" ]] || confirm "Do Outlook.com addresses bounce with \"messages from [$SERVER_V4] weren't sent\"?"; then
    say "That is Microsoft's own block. Ask for mitigation once the checks above pass."
    step "Fill in the Sender Information for Outlook.com Delivery form:"
    step "the IP $SERVER_V4, the domain $DOMAIN, and one full bounce message."
    step "Microsoft answers by mail within a day; a refusal can be escalated by replying to it."
    (( PROBLEMS == 0 )) || warn "$PROBLEMS check(s) above still fail: fix those first, or the request is refused"
    open_url "https://olcsupport.office.com/"
    if confirm "Request sent?"; then write_env MAIL_DELIST_ASKED "$(date +%F)"; else later "ask Microsoft to lift the block on $SERVER_V4"; fi
  elif ms_done MAIL_DELIST_ASKED; then
    note "a delisting was asked on $(_existing MAIL_DELIST_ASKED)"
  fi
}

# ── run ───────────────────────────────────────────────────────────────────

TOTAL_STAGES=9
[[ -n "$EMBEDDED" ]] || banner "Blueshell: mail deliverability"
stage_cluster
stage_dns
stage_mta_sts
stage_rdns
stage_smtp
stage_blocklists
stage_addresses
stage_rejections
stage_microsoft

printf '\n'
if (( PROBLEMS == 0 )); then
  printf '%s%s  Every check passes%s\n' "$BOLD" "$GREEN" "$RESET"
else
  printf '%s%s  %s check(s) fail; each says what to change%s\n' "$BOLD" "$YELLOW" "$PROBLEMS" "$RESET"
fi
(( ${#WRITTEN_ENV[@]} )) && note "remembered ${WRITTEN_ENV[*]} in $ENV_FILE"
if (( ${#SKIPPED[@]} )); then
  printf '\n'; warn "still to do:"
  for s in "${SKIPPED[@]}"; do note "  - $s"; done
fi
printf '\n'
