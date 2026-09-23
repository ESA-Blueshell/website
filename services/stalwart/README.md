# Stalwart (dev)

Dev-only Stalwart mail server fragment. The API delivers its mail through
Stalwart's SMTP on port 25 (exposed to the host on `localhost:1025`), and every
`@blueshell.test` address, the domain of every seeded account, lands in one
inbox.

Production Stalwart is deployed under `platform/cluster/flux/apps/mail/stalwart/`
and keeps its settings in Stalwart's datastore. The dev config is a flat TOML file
with TLS off, so no certificates are needed locally; the settings Stalwart keeps
in its database are set by `stalwart-init`.

**Reading the mail**: log in as `dev` / `dev`, over IMAP on `localhost:1143`
(no TLS) from any mail client, or over JMAP at `http://localhost:8085/jmap`.

**Admin UI**: http://localhost:8085 (fallback admin: `admin` / `admin`).

**Accounts**, seeded by the one-shot `stalwart-init` sidecar after the server is
healthy:
- `dev` / `dev`: the catch-all for `@blueshell.test`.
- `bounce@dev.local` / `bounce`: a bounce inbox, for the API's IMAP bounce
  poller when that is switched on.

`stalwart-init` exits non-zero when Stalwart refuses a principal for any reason
other than it already existing, so a seeding failure shows in
`docker compose ps`.
