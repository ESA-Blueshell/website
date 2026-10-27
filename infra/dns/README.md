# DNS zones

Authoritative zone files, one per domain. Imported into Cloudflare via
**Dashboard → DNS → Records → Import and Export → Import**.

## esa-blueshell.nl

- Zone: [`esa-blueshell.nl.zone`](./esa-blueshell.nl.zone)
- Migrated off TransIP on 2026-04-21.
- Apex `esa-blueshell.nl` serves the NixOS + k3s stack at
  `157.173.115.164`; its IPv6 address is not published until the server
  can serve it (#2640). Every service hostname
  the platform advertises (`api` lives at apex `/api`, plus admin hosts
  `kube`, `vault`, `stalwart`, `status`, `traefik`) resolves through
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
| `minecraft`                     | off   | Game traffic — Cloudflare proxy is HTTP(S) only.                             |
| `ftp`                           | off   | Non-HTTP protocol.                                                           |

## Mail records

`mail` A is in the zone file: non-proxied, pointing at the Frankfurt
VPS public IP, where Stalwart binds the mail ports (25/465/587/993)
through `hostPort`.

Stalwart owns every other mail record. Its Domain has
`dnsManagement` and `dkimManagement` set to Automatic (see
`infra/stalwart/apply.sh`), so it writes MX, SPF, DMARC and the DKIM
`_domainkey` keys to Cloudflare itself and overwrites anything imported
from the zone file. The zone file lists their live values as a comment
only. Read them from DNS, not from this repo:

```sh
dig +short esa-blueshell.nl MX
dig +short esa-blueshell.nl TXT
dig +short _dmarc.esa-blueshell.nl TXT
```

Brevo's records are the exception: the `brevo-code` TXT on the apex and
its DKIM records are added by hand in Cloudflare, so Brevo can sign the
mail it relays for Microsoft's addresses as `esa-blueshell.nl` (#2645).
`platform/bootstrap/mail.sh` checks they resolve and walks through adding
them.

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
