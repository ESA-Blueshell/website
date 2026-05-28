# DNS zones

Authoritative zone files, one per domain. Imported into Cloudflare via
**Dashboard → DNS → Records → Import and Export → Import**.

## esa-blueshell.nl

- Zone: [`esa-blueshell.nl.zone`](./esa-blueshell.nl.zone)
- Migrated off TransIP on 2026-04-21.
- Apex `esa-blueshell.nl` serves the NixOS + k3s stack at
  `157.173.115.164` / `2a02:c207:2316:2642::1`. Every service hostname
  the platform advertises (`api` lives at apex `/api`, plus admin hosts
  `kube`, `vault`, `mail-admin`, `status`, `traefik`) resolves through
  that node — admin records are external-dns managed off each
  IngressRoute.
- `v2.esa-blueshell.nl` has been retired — both the DNS records and
  the IngressRoute Host matchers were dropped once the apex cutover
  was confirmed healthy.

## Proxy status

After import, toggle the orange cloud per record:

| Record                          | Proxy | Reason                                                                       |
| ------------------------------- | ----- | ---------------------------------------------------------------------------- |
| `@`, `www`                      | on    | Public HTTP(S) behind Cloudflare.                                            |
| `stalwart`                      | on    | Webadmin only — pure HTTP, proxy gives cache + DDoS shielding.               |
| `mail`                          | off   | SMTP/IMAP/POP3 are raw TCP. Cloudflare proxy is HTTP(S)-only.                |
| `@` (MX target = `mail`)        | off   | Inherits from the `mail` A/AAAA above.                                       |
| `_dmarc`, `default._domainkey`  | off   | TXT records — proxy status is irrelevant but Cloudflare convention is "off". |
| `minecraft`                     | off   | Game traffic — Cloudflare proxy is HTTP(S) only.                             |
| `ftp`                           | off   | Non-HTTP protocol.                                                           |

## Mail records

Outbound mail signing + inbound delivery for `esa-blueshell.nl` use
five records, all in this zone file:

- **`mail`** A/AAAA — non-proxied, pointing at the Frankfurt VPS public
  IP. Stalwart binds the mail ports (25/465/587/993) on the host via
  `hostPort` on this address.
- **`@` MX → `mail`** — RFC-mandated indirection; the MX target's
  A/AAAA must be non-proxied so SMTP delivery reaches the pod
  directly.
- **`@` SPF** (`v=spf1 mx ~all`) — authorises the MX hosts to send.
  Soft-fail (`~all`) for the initial rollout; flip to `-all` once
  DMARC aggregate reports confirm no legitimate sender is being
  caught by the `mx` mechanism.
- **`_dmarc` DMARC** (`v=DMARC1; p=none; rua=mailto:postmaster@…`) —
  monitor-only at first. Promote to `p=quarantine` once outbound
  mail is fully aligned on both SPF and DKIM.
- **`default._domainkey` DKIM** — the public key matching the
  `DkimSignature` Stalwart manages with `default` as the selector.
  The zone file ships a placeholder (`p=REPLACE_AFTER_FIRST_BOOT`)
  because the actual public key is derived from the private key in
  Vault and only becomes visible after the apply sidecar creates the
  `DkimSignature` object. To populate it:

  ```sh
  kubectl -n mail-system exec deploy/stalwart -c stalwart-apply -- \
    stalwart-cli query DkimSignature --fields selector publicKey
  ```

  Paste the `publicKey` value (without `-----BEGIN PUBLIC KEY-----`
  markers, on one line) into the `p=` field of the TXT record in
  Cloudflare. Selector + algorithm are already correct in the
  zone file.

## ACME / Let's Encrypt

Wildcard `*.esa-blueshell.nl` is issued via DNS-01. cert-manager writes
the challenge TXT record at `_acme-challenge.esa-blueshell.nl` through
the Cloudflare API. The API token needs `Zone:DNS:Edit` scoped to this
zone.

## Pending cleanups in the zone

These are noted here so a future zone-file PR catches them; this PR
intentionally only adds mail records:

- `v2.*`, `cname-*.v2.esa-blueshell.nl.` TXT, `a-www.v2.*` — the v2
  apex was retired (see header note in this README) but the zone
  file still carries the records and their external-dns ownership
  markers.
- `listmonk.esa-blueshell.nl.` A/AAAA + `mail-admin.esa-blueshell.nl.`
  A/AAAA + the matching `a-listmonk.*` / `aaaa-listmonk.*` and
  `a-mail-admin.*` / `aaaa-mail-admin.*` TXT markers — Listmonk has
  been retired in favour of Stalwart relay (#260+). Drop after the
  Listmonk teardown PR lands cluster-side.
