#!/usr/bin/env bash
# Opens the sealed addresses and bank details into two tab-separated files.
# Needs VAULT_TOKEN set to the root token from step 7, and the stack from step 5 running.
set -euo pipefail
cd "$(dirname "$0")"
: "${VAULT_TOKEN:?Set VAULT_TOKEN first}"

sql() { docker compose exec -T db mariadb -uroot -precovery -N -B blueshell -e "$1"; }

open() { # key, context, ciphertext
  local out
  if out=$(docker compose exec -T -e VAULT_TOKEN="$VAULT_TOKEN" vault vault write -field=plaintext \
      "transit/decrypt/$1" ciphertext="$3" context="$(printf '%s' "$2" | base64)" </dev/null 2>/dev/null); then
    printf '%s' "$out" | base64 -d
  else
    printf 'UNOPENABLE'
  fi
}

mkdir -p export
printf 'user_id\taddress\n' > export/addresses.tsv
sql "SELECT u.id, a.sealed_address FROM addresses a JOIN users u ON u.address_id = a.id
     WHERE a.sealed_address IS NOT NULL" |
while IFS=$'\t' read -r user sealed; do
  printf '%s\t%s\n' "$user" "$(open api-address "address:$user" "$sealed")" >> export/addresses.tsv
done

printf 'membership_id\tuser_id\tiban\taccount_holder\tmandate_address\n' > export/bank-details.tsv
sql "SELECT id, user_id, mandate_iban, mandate_account_holder, IFNULL(mandate_address, '')
     FROM memberships WHERE mandate_iban IS NOT NULL" |
while IFS=$'\t' read -r membership user iban holder address; do
  printf '%s\t%s\t%s\t%s\t%s\n' "$membership" "$user" \
    "$(open api-bank-details "iban:$user" "$iban")" \
    "$(open api-bank-details "account-holder:$user" "$holder")" \
    "$([ -n "$address" ] && open api-bank-details "address:$user" "$address")" >> export/bank-details.tsv
done

echo "Written: export/addresses.tsv and export/bank-details.tsv"
