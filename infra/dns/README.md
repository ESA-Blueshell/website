# DNS zones

Authoritative zone files, one per domain. Imported into Cloudflare via
**Dashboard → DNS → Records → Import and Export → Import**.

## esa-blueshell.nl

- Zone: [`esa-blueshell.nl.zone`](./esa-blueshell.nl.zone)
- Migrated off TransIP on 2026-04-21.
- Apex `esa-blueshell.nl` continues to serve the legacy single-VPS stack at
  `136.144.191.63`.
- The new NixOS + k3s platform lives under `v2.esa-blueshell.nl` and the
  `*.v2` wildcard (`157.173.115.164` / `2a02:c207:2316:2642::1`). Every
  service hostname the platform advertises (`api.v2`, `kube.v2`, `vault.v2`,
  `mail.v2`, `auth.v2`, `status.v2`, …) resolves through that wildcard.
- Once the v2 stack is validated end-to-end the apex A/AAAA records flip
  to the v2 backend and the `v2` label retires.

## Proxy status

After import, toggle the orange cloud per record:

| Record                       | Proxy | Reason                                                 |
| ---------------------------- | ----- | ------------------------------------------------------ |
| `@`, `www`                   | on    | Public HTTP(S) behind Cloudflare.                      |
| `@` (MX target)              | off   | Mail cannot be proxied.                                |
| `minecraft`                  | off   | Game traffic — Cloudflare proxy is HTTP(S) only.       |
| `ftp`                        | off   | Non-HTTP protocol.                                     |
| `v2`, `*.v2`                 | off   | Different backend; wildcard wildcard-cert via DNS-01.  |

## ACME / Let's Encrypt

Wildcard `*.v2.esa-blueshell.nl` is issued via DNS-01. cert-manager writes
the challenge TXT record at `_acme-challenge.v2.esa-blueshell.nl` through
the Cloudflare API. The API token needs `Zone:DNS:Edit` scoped to this
zone.
