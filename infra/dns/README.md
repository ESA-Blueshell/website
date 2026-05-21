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
- `v2.esa-blueshell.nl` remains pointed at the same node as a fallback
  during the 30-day grace period after the apex cutover; the frontend
  and api IngressRoutes match both hostnames so legacy bookmarks keep
  working. Retired in PR 11.

## Proxy status

After import, toggle the orange cloud per record:

| Record                       | Proxy | Reason                                                 |
| ---------------------------- | ----- | ------------------------------------------------------ |
| `@`, `www`                   | on    | Public HTTP(S) behind Cloudflare.                      |
| `@` (MX target)              | off   | Mail cannot be proxied.                                |
| `minecraft`                  | off   | Game traffic — Cloudflare proxy is HTTP(S) only.       |
| `ftp`                        | off   | Non-HTTP protocol.                                     |
| `v2`                         | on    | Grace-period fallback to the apex backend (PR 11).     |

## ACME / Let's Encrypt

Wildcard `*.esa-blueshell.nl` is issued via DNS-01. cert-manager writes
the challenge TXT record at `_acme-challenge.esa-blueshell.nl` through
the Cloudflare API. The API token needs `Zone:DNS:Edit` scoped to this
zone.
