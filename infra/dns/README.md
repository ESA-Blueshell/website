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

| Record                       | Proxy | Reason                                                 |
| ---------------------------- | ----- | ------------------------------------------------------ |
| `@`, `www`                   | on    | Public HTTP(S) behind Cloudflare.                      |
| `@` (MX target)              | off   | Mail cannot be proxied.                                |
| `minecraft`                  | off   | Game traffic — Cloudflare proxy is HTTP(S) only.       |
| `ftp`                        | off   | Non-HTTP protocol.                                     |

## ACME / Let's Encrypt

Wildcard `*.esa-blueshell.nl` is issued via DNS-01. cert-manager writes
the challenge TXT record at `_acme-challenge.esa-blueshell.nl` through
the Cloudflare API. The API token needs `Zone:DNS:Edit` scoped to this
zone.
