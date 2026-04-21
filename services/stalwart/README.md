# Stalwart (dev)

Dev-only Stalwart mail server fragment. Listmonk delivers outbound mail through
Stalwart's SMTP on port 25 (exposed to the host on `localhost:1025`) and polls
bounces via IMAP on port 143 (`localhost:1143`).

Production Stalwart is deployed under `platform/cluster/flux/apps/mail/stalwart/`
(lands with the platform tree); the config here deliberately mirrors the prod
flat-TOML layout with TLS off so no certificates are needed locally.

**Admin UI**: http://localhost:8085 (fallback admin: `admin` / `admin`).
**Bounce account**: `bounce@dev.local` / `bounce`, seeded by the one-shot
`stalwart-init` sidecar after the server is healthy.
