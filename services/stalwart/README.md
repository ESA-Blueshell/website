# Stalwart (dev)

Dev-only Stalwart mail server fragment. Listmonk delivers outbound mail through
Stalwart's SMTP on port 25 (exposed to the host on `localhost:1025`) and polls
bounces via IMAP on port 143 (`localhost:1143`).

Production Stalwart is deployed via Flux in `platform/cluster/flux/apps/mail/stalwart/`
(lands in PR7) — the config here deliberately mirrors the prod TOML layout
with TLS off and plaintext auth so no certificates are needed locally.

**Admin UI**: http://localhost:8085 (fallback admin: `admin` / `admin`).
**Bounce account**: `bounce@dev.local` / `bounce` (seeded on first start).
