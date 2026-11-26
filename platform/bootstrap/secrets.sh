#!/usr/bin/env bash
# shellcheck source-path=SCRIPTDIR
#
# Every secret a person puts in Blueshell's Vault, in one place: run it on a new
# cluster after Vault is unsealed, after a restore, and whenever a value needs
# adding or replacing. With no flags it checks every section, skips each value
# that is already right, and stops only where one is missing or wrong: there it
# says what it will write, asks, writes and reads it back. Safe to re-run.
#
#   platform/bootstrap/secrets.sh                        check everything, fix what is missing
#   platform/bootstrap/secrets.sh --list                 the sections, one line each
#   platform/bootstrap/secrets.sh --only mail,api
#   platform/bootstrap/secrets.sh --only api --rotate    offer a new value for each key that may change
#   platform/bootstrap/secrets.sh --from-env services/api/.api.env --from-env extra.env
#   platform/bootstrap/secrets.sh --only backup --new-writer-key
#
# Flags:
#   --only S[,S]       run these sections
#   --rotate           ask to replace values that are present. Never a key whose
#                      replacement breaks stored state (the two-factor key, MariaDB's
#                      logins): vault-bootstrap.md has their rotations
#   --from-env FILE    take missing values from a dotenv file named like the repo's
#                      examples (JWT_SECRET, BREVO_API_KEY, MYSQL_PASSWORD, ...);
#                      repeatable
#   --new-writer-key   with --only backup: replace the Scaleway writer key
#
# Vault is reached through a port-forward to vault-v2-0 and OIDC sign-in through
# the site (lib/blueshell.sh vault_connect). Every secret travels to vault on
# stdin, never as an argument, and is shown only as a 12-character SHA-256
# fingerprint.
#
# BLUESHELL_VAULT_ADDR=http://127.0.0.1:8200 runs against that Vault directly and
# skips every cluster step and live check: a dry run against `vault server -dev`.
#
# Not written here, because the Vault bootstrap Job generates them once; the run
# only reports whether they exist: secret/platform/backup-report/<namespace>.

here="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=lib/wizard.sh
source "$here/lib/wizard.sh"
# shellcheck source=lib/blueshell.sh
source "$here/lib/blueshell.sh"
require_bash4

ACCOUNTS_FILE="$REPO_ROOT/infra/stalwart/accounts.json"

# name|what it holds: the order a full run takes.
SECTIONS=(
  "edge|the Cloudflare DNS token cert-manager, external-dns and Stalwart use"
  "ghcr|the GitHub package credential for the private images and Flux's registry scan"
  "flux-git|the deploy key Flux image automation pushes tag bumps with"
  "mariadb|MariaDB's root, app and admin logins"
  "mail|Stalwart's admin and every mailbox in infra/stalwart/accounts.json"
  "api|the api's signing keys, Vault OIDC client secret and integration keys"
  "alerting|the Discord webhook Gatus and Flagger post to"
  "backup|optional: the Kopia password and Scaleway writer key, seeded by an owner"
)
# The optional section only reports in a full run, and acts when --only names it.

usage() { sed -n '/^#   platform\/bootstrap/,/^#   --new-writer-key/p' "$0" | sed 's/^# \{0,1\}//'; }

ONLY="" ROTATE="" NEW_WRITER=""
declare -A FROM_ENV=() ORIGIN=()

# load_env_file FILE reads dotenv lines into FROM_ENV. Operator-controlled
# input: quotes are stripped and a double-quoted value takes escapes.
load_env_file() {
  local raw line key value
  [[ -f "$1" ]] || die "env file not found: $1"
  while IFS= read -r raw || [[ -n "$raw" ]]; do
    line="${raw%$'\r'}"
    line="${line#"${line%%[![:space:]]*}"}"
    [[ -z "$line" || "$line" == \#* || "$line" != *=* ]] && continue
    line="${line#export }"
    key="${line%%=*}"; key="${key%"${key##*[![:space:]]}"}"
    value="${line#*=}"; value="${value#"${value%%[![:space:]]*}"}"; value="${value%"${value##*[![:space:]]}"}"
    [[ "$key" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || continue
    if [[ "$value" == \"*\" && ${#value} -ge 2 ]]; then
      value="${value:1:${#value}-2}"; value="$(printf '%b' "$value")"
    elif [[ "$value" == \'*\' && ${#value} -ge 2 ]]; then
      value="${value:1:${#value}-2}"
    fi
    FROM_ENV[$key]="$value"
    ORIGIN[$key]="from $key in $1"
  done <"$1"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --list) for e in "${SECTIONS[@]}"; do printf '%-10s %s\n' "${e%%|*}" "${e#*|}"; done; exit 0 ;;
    --only) shift; ONLY="${1:?--only needs a section}" ;;
    --rotate) ROTATE=yes ;;
    --from-env) shift; load_env_file "${1:?--from-env needs a file}" ;;
    --new-writer-key) NEW_WRITER=yes ;;
    -h|--help) usage; exit 0 ;;
    *) usage; die "unknown argument: $1" ;;
  esac
  shift
done

SELECTED=()
if [[ -n "$ONLY" ]]; then
  IFS=',' read -r -a SELECTED <<<"$ONLY"
  for name in "${SELECTED[@]}"; do
    found=""; for e in "${SECTIONS[@]}"; do [[ "${e%%|*}" == "$name" ]] && found=yes; done
    [[ -n "$found" ]] || die "no section $name (see --list)"
  done
else
  for e in "${SECTIONS[@]}"; do SELECTED+=("${e%%|*}"); done
fi
[[ -z "$NEW_WRITER" || "$ONLY" == backup ]] || die "--new-writer-key goes with --only backup"
named() { [[ ",$ONLY," == *",$1,"* ]]; }

WORK=$(mktemp -d)
trap blueshell_cleanup EXIT

RESULTS=()
RERUN_JOB=""
WRITES=0
WRITES_BEFORE=0
section() { stage "$1"; WRITES_BEFORE=$WRITES; }
# result NAME [TEXT]: without TEXT, how many keys ensure wrote in the section.
result() {
  local text="${2:-}"
  [[ -n "$text" ]] || { text="$((WRITES - WRITES_BEFORE)) written"; [[ "$text" != "0 written" ]] || text=checked; }
  RESULTS+=("$(printf '%-10s %s' "$1" "$text")")
}
later() { warn "$1"; SKIPPED+=("$1"); }
live() { [[ -z "$LOCAL_VAULT" ]]; }

# ensure PATH KEY MODE ENVNAME VERIFY PROMPT HELP
#
# Checks one key and fixes it when missing, failing VERIFY, or (with --rotate)
# when you ask. Sets WROTE=yes when it wrote. MODE says where a new value comes
# from when no --from-env file offers ENVNAME:
#   mint=FN            FN generates it; --rotate offers a new one
#   keep=FN            FN generates it where missing; never replaced here
#   secret | plain     asked on a hidden or a visible prompt
#   optional-secret | optional-plain | optional-file    the same, and Enter skips
# HELP, when given, is a function that says where to get the value.
ensure() {
  local path="$1" key="$2" mode="$3" envname="$4" verify="$5" prompt="${6:-$2}" help="${7:-}"
  local doc cur new="" src="" _V="" tries=0 skip=""
  WROTE=""
  [[ "$mode" == optional-* ]] && skip=" (Enter skips)"
  doc=$(kv_load "$path")
  cur=$(kv_field "$doc" "$key")
  if [[ -n "$cur" ]]; then
    if [[ -n "$verify" ]] && ! "$verify" "$cur"; then
      warn "secret/$path $key ($(fp "$cur")) does not work"
    elif [[ -z "$ROTATE" || "$mode" == keep=* ]]; then
      ok "$key is set ($(fp "$cur"))"
      return 0
    elif ! confirm "Replace $key ($(fp "$cur"))?"; then
      ok "$key kept ($(fp "$cur"))"
      return 0
    fi
  fi
  if [[ -n "$envname" && -n "${FROM_ENV[$envname]:-}" ]]; then
    new="${FROM_ENV[$envname]}"; src="${ORIGIN[$envname]:-from $envname}"
  fi
  while true; do
    if [[ -z "$new" ]]; then
      case "$mode" in
        mint=*|keep=*) new=$("${mode#*=}"); src="generated" ;;
        *)
          [[ -n "$help" && "$tries" -eq 0 ]] && "$help"
          case "$mode" in
            *secret) ask_secret _V "$prompt$skip:" ;;
            *plain) ask _V "$prompt$skip:" ;;
            *file)
              ask _V "Path to $prompt (Enter skips):"
              if [[ -n "$_V" ]]; then
                [[ -f "$_V" ]] || { warn "no file at $_V"; _V=""; continue; }
                _V=$(jq -c . "$_V") || { warn "$prompt is not JSON"; _V=""; continue; }
              fi
              ;;
          esac
          new="$_V"; src="entered"
          ;;
      esac
    fi
    if [[ -z "$new" ]]; then
      [[ "$mode" == optional-* ]] || die "no value for secret/$path $key"
      later "secret/$path $key is not set; its integration stays off until it is"
      return 0
    fi
    [[ -z "$verify" ]] || "$verify" "$new" && break
    tries=$((tries + 1))
    [[ "$src" == entered && "$tries" -lt 3 ]] || die "the $key $src does not work"
    warn "that $key does not work; try again"
    new=""
  done
  confirm "Write $key ($(fp "$new"), $src) to secret/$path?" || { later "secret/$path $key left as it was"; return 0; }
  printf '%s' "$new" | kv_set "$path" "$key"
  doc=$(kv_load "$path")
  [[ "$(kv_field "$doc" "$key")" == "$new" ]] || die "secret/$path $key did not read back"
  ok "wrote $key ($(fp "$new"))"
  WROTE=yes
  WRITES=$((WRITES + 1))
}

# ── edge ──────────────────────────────────────────────────────────────────

verify_cloudflare() {
  live || return 0
  local auth
  auth=$(printf 'Authorization: Bearer %s' "$1")
  curl -s --max-time 10 -H @<(printf '%s\n' "$auth") https://api.cloudflare.com/client/v4/user/tokens/verify \
    | jq -e '.result.status == "active"' >/dev/null || return 1
  curl -s --max-time 10 -H @<(printf '%s\n' "$auth") "https://api.cloudflare.com/client/v4/zones?name=$DOMAIN" \
    | jq -e '.result | length == 1' >/dev/null
}

help_cloudflare() {
  say "A Cloudflare API token that may edit $DOMAIN's DNS records."
  step "Create Token, then the \"Edit zone DNS\" template."
  step "Zone Resources: Include, Specific zone, $DOMAIN. Create it and copy the token."
  open_url https://dash.cloudflare.com/profile/api-tokens
}

section_edge() {
  local value
  ensure platform/edge cloudflare.dns_api_token secret CF_DNS_API_TOKEN verify_cloudflare "Cloudflare API token" help_cloudflare
  if [[ -n "$WROTE" ]] && live; then
    value=$(kv_field "$(kv_load platform/edge)" cloudflare.dns_api_token)
    for ns in cert-manager external-dns mail-system; do vss_nudge "$ns" cloudflare-api-token; done
    vss_wait cert-manager cloudflare-api-token api-token "$value" || later "cert-manager/cloudflare-api-token has not synced yet"
    note "Stalwart reads the token at start: the mail section restarts it when asked."
  fi
  result edge
}

# ── ghcr ──────────────────────────────────────────────────────────────────

# verify_ghcr USER TOKEN succeeds when the pair may pull the private api image.
verify_ghcr() {
  live || return 0
  local bearer
  bearer=$(curl -s --max-time 10 -K <(printf 'user = "%s:%s"\n' "$1" "$2") \
    "https://ghcr.io/token?service=ghcr.io&scope=repository:esa-blueshell/api:pull" | jq -r '.token // empty')
  [[ -n "$bearer" ]] || return 1
  [[ "$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 -H @<(printf 'Authorization: Bearer %s\n' "$bearer") \
    https://ghcr.io/v2/esa-blueshell/api/tags/list)" == 200 ]]
}

section_ghcr() {
  local doc user token
  doc=$(kv_load platform/ghcr)
  user=$(kv_field "$doc" username); token=$(kv_field "$doc" token)
  if [[ -n "$user" && -n "$token" ]] && verify_ghcr "$user" "$token"; then
    if [[ -z "$ROTATE" ]] || ! confirm "Replace the ghcr token ($(fp "$token")) of $user?"; then
      ok "$user may pull ghcr.io/esa-blueshell/api (token $(fp "$token"))"
      result ghcr "checked"
      return 0
    fi
  elif [[ -n "$user$token" ]]; then
    warn "secret/platform/ghcr does not pull ghcr.io/esa-blueshell/api"
  fi
  user="${FROM_ENV[GHCR_USERNAME]:-}"; token="${FROM_ENV[GHCR_TOKEN]:-}"
  if [[ -z "$token" ]]; then
    say "A fine-grained token with read access to ESA-Blueshell's packages, nothing else."
    step "Resource owner ESA-Blueshell; Permissions: Packages, read-only. Generate and copy it."
    open_url https://github.com/settings/personal-access-tokens/new
    [[ -n "$user" ]] || ask user "GitHub username that owns the token:"
    ask_secret token "Token:"
  fi
  [[ -n "$user" && -n "$token" ]] || die "no ghcr username or token given"
  verify_ghcr "$user" "$token" || die "$user's token cannot pull ghcr.io/esa-blueshell/api"
  ok "$user's token pulls ghcr.io/esa-blueshell/api"
  confirm "Write username $user and token $(fp "$token") to secret/platform/ghcr?" || { later "secret/platform/ghcr left as it was"; return 0; }
  U="$user" T="$token" jq -n '{username: env.U, token: env.T}' | kv_write platform/ghcr
  doc=$(kv_load platform/ghcr)
  [[ "$(kv_field "$doc" token)" == "$token" ]] || die "secret/platform/ghcr did not read back"
  ok "wrote secret/platform/ghcr"
  if live; then
    for ns in default mail-system flux-system; do vss_nudge "$ns" ghcr-pull-secret; done
    note "pods take the new credential on their next image pull"
  fi
  result ghcr "written"
}

# ── flux-git ──────────────────────────────────────────────────────────────

FLUX_GIT_TITLE="flux image automation (write)"

deploy_key_id() { # deploy_key_id PUBLIC_KEY prints the id of the repository deploy key that matches
  local key
  key=$(awk '{print $1" "$2}' <<<"$1")
  gh api "repos/$REPO/keys" --paginate --jq ".[] | select(.key == \"$key\") | \"\(.id) \(.read_only)\"" 2>/dev/null | head -n 1
}

section_flux_git() {
  local doc pub found old_id="" known
  require_tools ssh-keygen
  doc=$(kv_load platform/flux-git)
  pub=$(kv_field "$doc" identity.pub)
  if [[ -n "$pub" && -n "$(kv_field "$doc" identity)" && -n "$(kv_field "$doc" known_hosts)" ]]; then
    if live; then
      found=$(deploy_key_id "$pub")
      if [[ "$found" == *" false" ]]; then
        if [[ -z "$ROTATE" ]] || ! confirm "Replace the image automation deploy key?"; then
          ok "the key in secret/platform/flux-git is a write deploy key on $REPO"
          result flux-git "checked"
          return 0
        fi
        old_id="${found%% *}"
      else
        warn "the key in secret/platform/flux-git is not a write deploy key on $REPO"
      fi
    else
      ok "secret/platform/flux-git holds a key"
      result flux-git "checked"
      return 0
    fi
  fi
  confirm "Generate a new ed25519 key, add it to $REPO as a write deploy key and write it to secret/platform/flux-git?" \
    || { later "secret/platform/flux-git left as it was"; return 0; }
  ssh-keygen -q -t ed25519 -N '' -C "flux-image-automation" -f "$WORK/flux-git" </dev/null
  # GitHub's own published keys, so no first connection is trusted blindly.
  known=$(gh api meta --jq '.ssh_keys[] | "github.com " + .' 2>/dev/null || true)
  [[ -n "$known" ]] || die "could not read GitHub's SSH host keys (gh api meta)"
  if live; then
    gh api -X POST "repos/$REPO/keys" -f title="$FLUX_GIT_TITLE" -f key="$(cat "$WORK/flux-git.pub")" -F read_only=false >/dev/null \
      || die "GitHub refused the deploy key: adding one needs admin on $REPO"
    ok "added the write deploy key \"$FLUX_GIT_TITLE\" to $REPO"
  fi
  jq -n --rawfile id "$WORK/flux-git" --rawfile pub "$WORK/flux-git.pub" --arg kh "$known" \
    '{identity: $id, "identity.pub": ($pub | rtrimstr("\n")), known_hosts: $kh}' | kv_write platform/flux-git
  doc=$(kv_load platform/flux-git)
  [[ "$(kv_field "$doc" identity.pub)" == "$(tr -d '\n' <"$WORK/flux-git.pub")" ]] || die "secret/platform/flux-git did not read back"
  ok "wrote secret/platform/flux-git"
  rm -f "$WORK/flux-git" "$WORK/flux-git.pub"
  if live; then
    vss_nudge flux-system flux-image-automation
    if [[ -n "$old_id" ]]; then
      if vss_wait flux-system flux-image-automation identity.pub "$(kv_field "$doc" identity.pub)"; then
        gh api -X DELETE "repos/$REPO/keys/$old_id" >/dev/null && ok "removed the old deploy key $old_id"
      else
        later "remove the old image automation deploy key $old_id once flux-system/flux-image-automation syncs"
      fi
    fi
  fi
  result flux-git "written"
}

# ── mariadb ───────────────────────────────────────────────────────────────

section_mariadb() {
  local doc root user password admin_user admin_password has_data="" patch='{}'
  doc=$(kv_load platform/mariadb)
  root=$(kv_field "$doc" root-password); user=$(kv_field "$doc" user); password=$(kv_field "$doc" password)
  admin_user=$(kv_field "$doc" admin-user); admin_password=$(kv_field "$doc" admin-password)
  if [[ -n "$root" && -n "$user" && -n "$password" && -n "$admin_user" && -n "$admin_password" ]]; then
    ok "root-password ($(fp "$root")), user $user, password ($(fp "$password")), admin-user $admin_user are set"
    [[ -z "$ROTATE" ]] || note "MariaDB's logins rotate with ALTER USER: vault-bootstrap.md, MariaDB logins"
    result mariadb "checked"
    return 0
  fi
  live && k -n data-system get pvc -o name 2>/dev/null | grep -q mariadb && has_data=yes
  root="${root:-${FROM_ENV[MYSQL_ROOT_PASSWORD]:-}}"
  user="${user:-${FROM_ENV[MYSQL_USER]:-}}"
  password="${password:-${FROM_ENV[MYSQL_PASSWORD]:-}}"
  if [[ -n "$has_data" && ( -z "$root" || -z "$password" ) ]]; then
    later "MariaDB already holds data, so its passwords cannot be made new: restore Vault, or give them with --from-env (MYSQL_ROOT_PASSWORD, MYSQL_PASSWORD)"
    result mariadb "missing"
    return 0
  fi
  [[ -n "$root" ]] || root=$(gen_password)
  [[ -n "$user" ]] || user=blueshell
  [[ -n "$password" ]] || password=$(gen_password)
  admin_user="${admin_user:-${FROM_ENV[MARIADB_ADMIN_USER]:-root}}"
  admin_password="${admin_password:-${FROM_ENV[MARIADB_ADMIN_PASSWORD]:-$root}}"
  patch=$(R="$root" U="$user" P="$password" AU="$admin_user" AP="$admin_password" jq -n --argjson d "$doc" \
    '{"root-password": env.R, user: env.U, password: env.P, "admin-user": env.AU, "admin-password": env.AP}
     | with_entries(select(($d[.key] // "") == ""))')
  say "secret/platform/mariadb gets: $(jq -r 'keys | join(", ")' <<<"$patch")"
  confirm "Write them (root $(fp "$root"), app $(fp "$password"))?" || { later "secret/platform/mariadb left as it was"; return 0; }
  kv_write platform/mariadb <<<"$patch"
  doc=$(kv_load platform/mariadb)
  [[ "$(kv_field "$doc" password)" == "$password" ]] || die "secret/platform/mariadb did not read back"
  ok "wrote secret/platform/mariadb"
  if live; then
    vss_nudge data-system mariadb-credentials
    vss_wait data-system mariadb-credentials mariadb-password "$password" || later "data-system/mariadb-credentials has not synced yet"
  fi
  # The Job hands the admin login to Vault's database engine.
  RERUN_JOB=yes
  result mariadb "written"
}

# ── mail ──────────────────────────────────────────────────────────────────

section_mail() {
  local doc lp key legacy changed=() logs
  require_tools jq
  [[ -f "$ACCOUNTS_FILE" ]] || die "no $ACCOUNTS_FILE"
  doc=$(kv_load platform/mail)
  # Before account.<localpart>, the api and bounce passwords lived under these.
  for legacy in api:api-password bounce:bounce-mailbox-password; do
    if [[ -n "$(kv_field "$doc" "${legacy#*:}")" ]]; then
      FROM_ENV[LEGACY_${legacy%%:*}]=$(kv_field "$doc" "${legacy#*:}")
      ORIGIN[LEGACY_${legacy%%:*}]="copied from ${legacy#*:}"
    fi
  done
  if [[ -n "${FROM_ENV[EMAIL_BOUNCE_IMAP_PASSWORD]:-}" ]]; then
    FROM_ENV[LEGACY_bounce]="${FROM_ENV[EMAIL_BOUNCE_IMAP_PASSWORD]}"
    ORIGIN[LEGACY_bounce]="${ORIGIN[EMAIL_BOUNCE_IMAP_PASSWORD]}"
  fi

  say "admin: the recovery admin Stalwart seeds at start, and the apply sidecar logs in as."
  ensure platform/mail account.admin mint=gen_password STALWART_ADMIN_PASSWORD ""
  [[ -n "$WROTE" ]] && changed+=(account.admin)
  # The account list arrives on fd 3: the prompts inside read stdin.
  while IFS= read -r lp <&3; do
    key=$(jq -r --arg lp "$lp" '.[] | select(.localPart == $lp) | .vaultKey' "$ACCOUNTS_FILE")
    say "$lp@$DOMAIN$(jq -r --arg lp "$lp" --arg d "$DOMAIN" \
      '.[] | select(.localPart == $lp) | (.aliases // []) | if length > 0 then " (also " + (map(. + "@" + $d) | join(", ")) + ")" else "" end' \
      "$ACCOUNTS_FILE")"
    ensure platform/mail "$key" mint=gen_password "LEGACY_$lp" ""
    [[ -n "$WROTE" ]] && changed+=("$key")
  done 3< <(jq -r '.[].localPart' "$ACCOUNTS_FILE")
  note "read a mailbox password with: vault kv get -field=account.<localpart> secret/platform/mail"

  if live; then
    if (( ${#changed[@]} )); then
      vss_nudge mail-system stalwart-secrets
      doc=$(kv_load platform/mail)
      for key in "${changed[@]}"; do
        vss_wait mail-system stalwart-secrets "$key" "$(kv_field "$doc" "$key")" || later "mail-system/stalwart-secrets lacks the new $key"
      done
      [[ " ${changed[*]} " == *" account.bounce "* ]] && note "the api reads account.bounce again within five minutes"
    fi
    logs=$(k -n mail-system logs deploy/stalwart -c stalwart-apply --tail=300 2>/dev/null || true)
    if (( ${#changed[@]} )) || grep -q 'apply: skipping' <<<"$logs"; then
      grep 'apply: skipping' <<<"$logs" | sed 's/^/    /' || true
      if confirm "Restart Stalwart so its apply sidecar creates and updates the mailboxes?"; then
        k -n mail-system rollout restart deploy/stalwart >/dev/null
        k -n mail-system rollout status deploy/stalwart --timeout=300s >/dev/null || die "Stalwart did not come back"
        for _ in $(seq 1 36); do
          logs=$(k -n mail-system logs deploy/stalwart -c stalwart-apply 2>/dev/null || true)
          grep -q 'apply: reconcile complete' <<<"$logs" && break
          sleep 5
        done
        grep -q 'apply: reconcile complete' <<<"$logs" || later "Stalwart's apply sidecar has not finished: kubectl -n mail-system logs deploy/stalwart -c stalwart-apply"
        if grep -q 'apply: skipping' <<<"$logs"; then
          grep 'apply: skipping' <<<"$logs" | sed 's/^/    /'
          later "Stalwart skipped accounts whose password it could not read"
        else
          ok "Stalwart reconciled every account"
        fi
      else
        later "restart Stalwart to apply the mail changes: kubectl -n mail-system rollout restart deploy/stalwart"
      fi
    else
      ok "Stalwart's apply sidecar skipped no account on its last start"
    fi
  fi
  result mail
}

# ── api ───────────────────────────────────────────────────────────────────

gen_jwt() { openssl rand -base64 64 | tr -d '\n'; }
gen_two_factor() { openssl rand -base64 32 | tr -d '\n'; }
gen_hex32() { openssl rand -hex 32; }

b64_bytes() { printf '%s' "$1" | openssl base64 -d -A 2>/dev/null | wc -c | tr -d ' '; }
verify_jwt() { (( $(b64_bytes "$1") >= 64 )); }
verify_two_factor() { (( $(b64_bytes "$1") == 32 )); }
verify_sa_json() { jq -e '.type == "service_account" and (.private_key | length > 0) and (.client_email | length > 0)' <<<"$1" >/dev/null 2>&1; }

verify_brevo() {
  live || return 0
  [[ "$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 -H @<(printf 'api-key: %s\n' "$1") https://api.brevo.com/v3/account)" == 200 ]]
}

verify_discord_bot() {
  live || return 0
  curl -s --max-time 10 -H @<(printf 'Authorization: Bot %s\n' "$1") https://discord.com/api/v10/users/@me | jq -e '.bot == true' >/dev/null
}

section_api() {
  local wrote_new="" key had spec mode envname verify prompt
  ensure api app.jwt.secret mint=gen_jwt JWT_SECRET verify_jwt
  ensure api app.two-factor.key keep=gen_two_factor TWO_FACTOR_ENCRYPTION_KEY verify_two_factor
  [[ -z "$ROTATE" ]] || note "the two-factor key is not replaced here: vault-bootstrap.md, Two-factor key"
  ensure api auth.clients.vault.secret mint=gen_hex32 VAULT_OIDC_CLIENT_SECRET ""
  # Vault's OIDC config holds the same secret, and the bootstrap Job writes it there.
  [[ -n "$WROTE" ]] && RERUN_JOB=yes

  say "The integrations below are optional: an empty key leaves that integration off."
  for spec in \
    "brevo.apiKey|optional-secret|BREVO_API_KEY|verify_brevo|Brevo API key" \
    "brevo.folders.contributionPeriodsId|optional-plain|BREVO_FOLDER_CONTRIBUTION_PERIODS_ID||Brevo folder id for contribution periods" \
    "google.calendar.id|optional-plain|GOOGLE_CALENDAR_ID||Google Calendar id" \
    "google.calendar.serviceAccountJson|optional-file|GOOGLE_CALENDAR_SA_JSON|verify_sa_json|the Google service account key (.json)" \
    "discord.botToken|optional-secret|DISCORD_BOT_TOKEN|verify_discord_bot|Discord bot token" \
    "discord.guildId|optional-plain|DISCORD_GUILD_ID||Discord server id"; do
    IFS='|' read -r key mode envname verify prompt <<<"$spec"
    had=$(kv_field "$(kv_load api)" "$key")
    ensure api "$key" "$mode" "$envname" "$verify" "$prompt"
    [[ -n "$WROTE" && -z "$had" ]] && wrote_new=yes
  done
  # The api re-reads secret/api every five minutes, but an integration whose key
  # was empty at start has no beans to hand the new key to.
  if [[ -n "$wrote_new" ]] && live && k -n default get deploy/api-primary >/dev/null 2>&1; then
    if confirm "An integration got its first key. Restart the api so it starts that integration?"; then
      k -n default rollout restart deploy/api-primary >/dev/null
      k -n default rollout status deploy/api-primary --timeout=600s >/dev/null || later "the api is still restarting"
      ok "restarted the api"
    else
      later "restart the api to start the new integration: kubectl -n default rollout restart deploy/api-primary"
    fi
  fi
  result api
}

# ── alerting ──────────────────────────────────────────────────────────────

verify_webhook() {
  [[ "$1" == https://discord.com/api/webhooks/* ]] || return 1
  live || return 0
  curl -s --max-time 10 -K <(printf 'url = "%s"\n' "$1") | jq -e '.id' >/dev/null
}

help_webhook() {
  say "A Discord incoming webhook for the channel that gets uptime alerts and release events."
  step "Discord: Server Settings, Integrations, Webhooks, New Webhook; pick the channel and copy its URL."
}

section_alerting() {
  ensure platform/alerting discord.webhook_url optional-secret DISCORD_WEBHOOK_URL verify_webhook "Webhook URL" help_webhook
  if [[ -n "$WROTE" ]] && live; then
    for ns in utility-system flagger-system flux-system; do vss_nudge "$ns" alerting-discord; done
    vss_wait utility-system alerting-discord DISCORD_WEBHOOK_URL "$(kv_field "$(kv_load platform/alerting)" discord.webhook_url)" \
      || later "utility-system/alerting-discord has not synced yet"
  fi
  result alerting
}

# ── backup ────────────────────────────────────────────────────────────────

SCW_REGION=nl-ams
SCW_SECRET_NAME=kopia-repository-password
SCW_WRITER_APP=backup-writer
SCW_API=${SCW_API_URL:-https://api.scaleway.com}

# scw METHOD PATH [CURL_ARGS]: the key reaches curl through a file descriptor,
# never as an argument `ps` would show.
scw() {
  local method=$1 path=$2
  shift 2
  curl -sS --fail-with-body -X "$method" -H @<(printf 'X-Auth-Token: %s\n' "$SCW_SECRET_KEY") \
    -H 'Content-Type: application/json' "$SCW_API$path" "$@"
}

section_backup() {
  local doc caps secret_id enabled_revisions enabled_versions app_id in_vault in_sm password
  local copy_to_sm=false copy_to_vault=true old_access make_writer=false access="" secret="" expires key data update versions keys
  doc=$(kv_load platform/backup)
  if ! named backup; then
    if [[ -n "$(kv_field "$doc" kopia.password)" && -n "$(kv_field "$doc" writer.access_key)" && -n "$(kv_field "$doc" writer.secret_key)" ]]; then
      ok "secret/platform/backup holds the Kopia password and the writer key $(kv_field "$doc" writer.access_key)"
      result backup "checked"
    else
      later "secret/platform/backup is incomplete: an owner runs secrets.sh --only backup with a Scaleway key (backup.md)"
      result backup "missing"
    fi
    return 0
  fi
  live || die "the backup section talks to Scaleway; it does not dry-run"

  caps=$(vault token capabilities secret/data/platform/backup </dev/null)
  [[ "$caps" == *root* || ( "$caps" == *create* && "$caps" == *update* && "$caps" == *read* ) ]] \
    || die "your Vault token may not write secret/platform/backup (it has: $caps)"
  [[ -n "${SCW_SECRET_KEY:-}" ]] || ask_secret SCW_SECRET_KEY "Your Scaleway secret key:"
  [[ -n "${SCW_SECRET_KEY:-}" ]] || die "no Scaleway key given"
  if [[ -z "${SCW_DEFAULT_ORGANIZATION_ID:-}" ]]; then
    ask SCW_DEFAULT_ORGANIZATION_ID "Scaleway organization id:"
    write_env SCW_DEFAULT_ORGANIZATION_ID "$SCW_DEFAULT_ORGANIZATION_ID"
  fi
  # The organization's first project shares its id, and the backups live there.
  SCW_DEFAULT_PROJECT_ID=${SCW_DEFAULT_PROJECT_ID:-$SCW_DEFAULT_ORGANIZATION_ID}

  secret_id=$(scw GET "/secret-manager/v1beta1/regions/$SCW_REGION/secrets?name=$SCW_SECRET_NAME&project_id=$SCW_DEFAULT_PROJECT_ID" \
    | jq -r '.secrets[0].id // empty') || die "Scaleway refused your key for Secret Manager"
  [[ -n "$secret_id" ]] || die "Secret Manager has no $SCW_SECRET_NAME in $SCW_REGION: has platform/scaleway/backup been applied?"
  # Filtered here, not by the API: a version deleted moments ago may still come back as enabled.
  scw_enabled() {
    scw GET "/secret-manager/v1beta1/regions/$SCW_REGION/secrets/$secret_id/versions?page_size=100" \
      | jq -r '[.versions[] | select(.status == "enabled") | .revision] | map(tostring) | join(" ")'
  }
  enabled_revisions=$(scw_enabled)
  enabled_versions=$(wc -w <<<"$enabled_revisions" | tr -d ' ')
  app_id=$(scw GET "/iam/v1alpha1/applications?name=$SCW_WRITER_APP&organization_id=$SCW_DEFAULT_ORGANIZATION_ID" \
    | jq -r --arg n "$SCW_WRITER_APP" '[.applications[] | select(.name == $n)][0].id // empty') \
    || die "Scaleway refused your key for IAM"
  [[ -n "$app_id" ]] || die "IAM has no application $SCW_WRITER_APP"
  ok "Scaleway has $SCW_SECRET_NAME ($enabled_versions enabled version(s)) and $SCW_WRITER_APP"

  in_vault=$(kv_field "$doc" kopia.password)
  in_sm=""
  if (( enabled_versions > 0 )); then
    (( enabled_versions == 1 )) || die "$SCW_SECRET_NAME has $enabled_versions enabled versions; disable all but the one the repository opens with"
    in_sm=$(scw GET "/secret-manager/v1beta1/regions/$SCW_REGION/secrets/$secret_id/versions/$enabled_revisions/access" \
      | jq -r '.data' | openssl base64 -d -A)
  fi
  if [[ -n "$in_vault" && -n "$in_sm" ]]; then
    [[ "$in_vault" == "$in_sm" ]] || die "Vault and Secret Manager hold different Kopia passwords: find the one the repository opens with, by hand"
    password=$in_vault; copy_to_vault=false
    ok "the Kopia password is in Vault and Secret Manager, and they match"
  elif [[ -n "$in_vault" ]]; then
    password=$in_vault; copy_to_vault=false; copy_to_sm=true
    say "The Kopia password is in Vault only; it goes to Secret Manager too."
  elif [[ -n "$in_sm" ]]; then
    password=$in_sm
    say "The Kopia password is in Secret Manager only; it goes to Vault too."
  else
    password=$(openssl rand -base64 32); copy_to_sm=true
    say "No Kopia password yet; a new one is generated."
  fi
  unset in_vault in_sm

  old_access=$(kv_field "$doc" writer.access_key)
  if [[ -z "$old_access" || -n "$NEW_WRITER" ]]; then
    make_writer=true
  else
    ok "Vault holds the writer key $old_access; it stays (--new-writer-key replaces it)"
  fi
  if ! $copy_to_sm && ! $copy_to_vault && ! $make_writer; then
    result backup "checked"
    return 0
  fi
  confirm "Write what is listed above to Scaleway and secret/platform/backup?" || { later "the backup credentials were left as they were"; return 0; }

  if $copy_to_sm; then
    data=$(printf '%s' "$password" | openssl base64 -A)
    D=$data jq -n '{data: env.D, description: "Opens the Kopia repository in esa-blueshell-backups"}' \
      | scw POST "/secret-manager/v1beta1/regions/$SCW_REGION/secrets/$secret_id/versions" --data-binary @- >/dev/null \
      || die "Secret Manager refused the new version; nothing was written to Vault"
    unset data
    ok "Secret Manager holds the Kopia password"
  fi
  if $make_writer; then
    expires=$(date -u -v+365d +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -d '+365 days' +%Y-%m-%dT%H:%M:%SZ)
    key=$(jq -n --arg a "$app_id" --arg p "$SCW_DEFAULT_PROJECT_ID" --arg e "$expires" --arg d "Nightly backup, $(date -u +%Y-%m-%d)" \
      '{application_id: $a, default_project_id: $p, expires_at: $e, description: $d}' \
      | scw POST /iam/v1alpha1/api-keys --data-binary @-) || die "IAM refused to make a writer key"
    access=$(jq -r '.access_key' <<<"$key"); secret=$(jq -r '.secret_key' <<<"$key")
    unset key
    [[ -n "$access" && -n "$secret" && "$secret" != null ]] || die "IAM answered without a key"
    ok "made the writer key $access, expiring $expires"
  fi
  update=$(KP=$password AK=$access SK=$secret jq -n '{"kopia.password": env.KP}
    + (if env.AK != "" then {"writer.access_key": env.AK, "writer.secret_key": env.SK} else {} end)')
  kv_write platform/backup <<<"$update"
  unset update password secret
  doc=$(kv_load platform/backup)
  for f in kopia.password writer.access_key writer.secret_key; do
    [[ -n "$(kv_field "$doc" "$f")" ]] || die "secret/platform/backup has no $f after writing"
  done
  ok "secret/platform/backup holds the Kopia password and the writer key $(kv_field "$doc" writer.access_key)"
  if $make_writer && [[ -n "$old_access" && "$old_access" != "$access" ]]; then
    scw DELETE "/iam/v1alpha1/api-keys/$old_access" >/dev/null \
      || later "delete the old writer key $old_access in the Scaleway console"
    ok "deleted the old writer key $old_access"
  fi
  versions=$(wc -w <<<"$(scw_enabled)" | tr -d ' ')
  keys=$(scw GET "/iam/v1alpha1/api-keys?application_id=$app_id&organization_id=$SCW_DEFAULT_ORGANIZATION_ID" | jq -r '.total_count')
  ok "check: Vault has all three fields, Secret Manager $versions enabled version, $SCW_WRITER_APP $keys key(s)"
  (( keys == 1 )) || later "$SCW_WRITER_APP has $keys keys: delete every one but $(kv_field "$doc" writer.access_key) in the Scaleway console"
  note "run the first night now: kubectl --context $CONTEXT -n data-system create job --from=cronjob/backup backup-first"
  result backup "written"
}

generated_check() {
  local ns
  for ns in data-system mail-system utility-system; do
    if [[ -n "$(kv_field "$(kv_load "platform/backup-report/$ns")" token)" ]]; then
      ok "secret/platform/backup-report/$ns exists (the bootstrap Job made it)"
    else
      later "secret/platform/backup-report/$ns is missing: the Vault bootstrap Job makes it on its next run"
    fi
  done
}

# ── run ───────────────────────────────────────────────────────────────────

TOTAL_STAGES=$(( 1 + ${#SELECTED[@]} ))
[[ -n "${_BLUESHELL_VAULT_INHERITED:-}" ]] || banner "Blueshell: the secrets people put in Vault"
section "Vault: reach it and sign in"
require_tools jq curl openssl vault
if [[ -z "$LOCAL_VAULT" && -z "${_BLUESHELL_VAULT_INHERITED:-}" ]]; then
  require_tools kubectl
  require_cluster
fi
vault_connect

for CURRENT in "${SELECTED[@]}"; do
  for e in "${SECTIONS[@]}"; do [[ "${e%%|*}" == "$CURRENT" ]] && section "$CURRENT: ${e#*|}"; done
  case "$CURRENT" in
    edge) section_edge ;;
    ghcr) section_ghcr ;;
    flux-git) section_flux_git ;;
    mariadb) section_mariadb ;;
    mail) section_mail ;;
    api) section_api ;;
    alerting) section_alerting ;;
    backup) section_backup ;;
  esac
done
[[ -n "$ONLY" ]] || generated_check

if [[ -n "$RERUN_JOB" ]]; then
  say "The Vault bootstrap Job reads what was just written (the OIDC client secret, MariaDB's admin login)."
  if [[ -n "${_BLUESHELL_VAULT_INHERITED:-}" ]]; then
    note "cluster.sh runs the Job next"
  elif confirm "Run the Vault bootstrap Job now?"; then
    rerun_bootstrap_job
  else
    later "run the Vault bootstrap Job: platform/bootstrap/cluster.sh, or delete job/$BOOTSTRAP_JOB and reconcile apps-data"
  fi
fi

printf '\n%s%s  Done%s\n' "$BOLD" "$GREEN" "$RESET"
for line in "${RESULTS[@]}"; do note "  $line"; done
(( ${#WRITTEN_ENV[@]} )) && note "wrote ${WRITTEN_ENV[*]} to $ENV_FILE"
if (( ${#SKIPPED[@]} )); then
  printf '\n'; warn "still to do:"
  for s in "${SKIPPED[@]}"; do note "  - $s"; done
fi
printf '\n'
