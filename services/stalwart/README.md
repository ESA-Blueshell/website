# Stalwart (dev)

Dev-only Stalwart mail server fragment. The API delivers its mail through
Stalwart's SMTP on port 25 (exposed to the host on `localhost:1025`), and every
`@blueshell.test` address, the domain of every seeded account, lands in one
inbox.

Production Stalwart is deployed under `platform/cluster/flux/apps/mail/stalwart/`.
Both read only `config.json`, a pointer to the datastore, from disk; every other
setting is an object in the datastore. The dev image is the pinned Stalwart with
`stalwart-cli` beside it, and its entrypoint seeds the datastore on the first
start: `settings.ndjson`, then `seed.ndjson`, each followed by a restart, since
Stalwart binds a new listener and applies a new password policy only when it
starts. A `seeded` marker in the volume keeps a later start from seeding again,
and the healthcheck waits for it. TLS is off, so no certificates are needed
locally.

**Reading the mail**: log in as `dev@blueshell.test` / `dev`, over IMAP on
`localhost:1143` (no TLS) from any mail client, or over JMAP at
`http://localhost:8085/jmap`.

**Admin UI**: http://localhost:8085 (administrator: `admin` / `admin`).

**Accounts**:
- `dev@blueshell.test` / `dev`: the catch-all for `@blueshell.test`.
- `bounce@dev.local` / `bounce`: a bounce inbox, for the API's IMAP bounce
  poller when that is switched on.

The spam filter is off, since dev mail comes from `localhost` with no SPF, and so
is the inbound per-IP throttle, since the seed mails every account at once.

A seeding failure stops the container, so it shows in `docker compose ps`. To
seed again from scratch, remove the volume: `docker compose rm -sfv stalwart &&
docker volume rm website_stalwart-datastore`.
