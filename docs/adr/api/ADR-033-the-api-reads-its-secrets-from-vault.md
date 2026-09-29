# ADR-033: The API Reads Its Secrets From Vault, and They Rotate Without a Restart

## Status
Accepted

## Context

The api reads Vault four ways, and only one of them is live. The Vault injector renders 17
keys from `secret/api` into an env file once at pod start, and the start script exports
them. The Vault Secrets Operator syncs the same path into a Kubernetes Secret, which the api
reads one key from, and the mail passwords arrive the same way from a second synced Secret.
Spring Cloud Vault is on, but its `vault://` import reads `secret/BlueshellAPI` and
`secret/application`, not `secret/api`, so it delivers nothing the api uses. Only Transit,
which signs the api's OIDC and forward-auth tokens, reaches Vault while the api runs.

Every one of those copies goes stale when a key rotates: the running api keeps the old value
until somebody restarts the pod, and a restart after a rotation is easy to forget. The api
and the migrate Job also log in to MariaDB with a static password copied into `secret/api`,
beside a dynamic credentials engine configured for them and switched off.

## Decision

**The api and the migrate Job read secrets from Vault through Spring Cloud Vault only.** They
log in with Kubernetes auth as the `api` role. No rendered env file, no environment variable
and no synced Secret sits between Vault and the api.

**Each KV key is named for the Spring property it fills.** The prod profile imports
`secret/api` and `secret/platform/mail`, and an import outranks `application.yaml`, so a
key in Vault wins over the `${ENV_VAR:default}` placeholder beside the same property there.
Those placeholders are how dev, test and CI supply values without Vault; the production pod
sets none of those variables. Adding a key is one KV write and one property, and
`platform/docs/vault-bootstrap.md` says how to rotate it.

**A read Vault refuses stops the start.** `fail-fast` is on, so a missing grant surfaces as
a failed pod rather than an empty key.

**KV is polled.** KV v2 secrets carry no lease, so Spring Cloud Vault's lease lifecycle never
re-reads them, and Vault tells the api nothing when a key changes. On an interval set in the
prod profile, the api re-reads the KV paths it imports, puts any key that changed in front of
the imported ones and names the changed keys in an `EnvironmentChangeEvent`. Each consumer
reads the current value rather than the one it was built with: a client is rebuilt on that
event, or reads the key per call.

**A refresh never makes things worse.** A refresh that cannot reach Vault keeps the values in
use. A value a consumer refuses, such as a two-factor key of the wrong length, keeps the
previous one in use.

**A rotation does not end what the old value made.** A retired two-factor key still opens
what it sealed (api ADR-031). A token signed with the previous JWT secret stays valid until it
expires.

**The api's database login is leased.** The api takes its MariaDB login from
`database/creds/api`. Spring Cloud Vault renews that lease only up to the role's `max_ttl`,
so the api also asks for the role as a rotating secret and hands every new login to the
connection pool before the old one is revoked.

**The migration logs in as the schema's owner.** MariaDB records the creating user as each
trigger's DEFINER, and a trigger whose definer Vault has dropped fails every write to its
table. So the migrate Job keeps the stable owner login the MariaDB chart holds, read under a
Vault role of its own that the api's pods do not have.

**A sealed Vault stops the api from starting.** That is accepted: without Transit the api
cannot sign an OIDC or forward-auth token anyway. Auto-unseal is a separate question.

**A credential gate is decided at start.** The Discord token gate and the other gates decide
whether a bean exists, and a refresh does not create or remove beans. A key that changes
takes effect without a restart; a key that goes from empty to set needs one.

**The Vault Secrets Operator serves consumers that are not Spring**: Stalwart, MariaDB,
Gatus, cert-manager, external-dns and Flux. The api and the migrate Job do not use it, and it
is not how a rotated key reaches the api.

## Considered Options

- **The synced Secret, read as environment variables.** Keeps a restarted api starting while
  Vault is sealed. But a container's environment never changes after it starts, and the
  operator's `rolloutRestartTargets` only automates the restart.
- **The synced Secret mounted as files, read with `configtree:`.** The files do update in
  place, so the same refresh code would work. It adds the operator's `refreshAfter` and the
  kubelet's sync delay on top of the poll, for a copy the api does not need, since Spring
  Cloud Vault already talks to Vault.
- **The Vault injector.** Renders once at pod start, as stale as environment variables, plus
  an init container and a template that repeats every key.
- **Spring Cloud's `ContextRefresher`.** It re-runs the whole `vault://` import. Measured
  against a real Vault and MariaDB, three refreshes leased three more database users, and a
  refresh put a development value back over a Vault one.
- **Push instead of poll.** Vault sends nothing to a KV v2 reader when a key changes, so a
  push would need a separate notifier. Polling one path on an interval is cheaper than
  running one.
- **Lease the migration's login too.** Every trigger and view it creates would name a user
  Vault drops within minutes of the Job ending.

## Consequences

- Every secret consumer in the api needs a way to take a new value: the Brevo, Google
  Calendar and Discord clients, the mail sender, the IMAP bounce poller, the two-factor
  cipher, the JWT reader and the registered OIDC client.
- The JWT reader accepts two secrets for one token lifetime after a rotation.
- The Vault OIDC client secret lives both in Vault's OIDC config and in `secret/api`, so its
  rotation changes both, in the order the bootstrap doc gives.
- Removing a key is one KV delete and one property, with no manifest, template or Secret to
  edit.
- The `api-secrets` Secret, the injector annotations and the env-file start script go.
- The `api` policy the bootstrap Job writes is what the api can read; an integration test
  logs in with exactly that policy against a real Vault, so the two cannot drift.

## Implementation status

Built: Kubernetes auth, the KV import, fail-fast and the key names (#1682), and the leased
api login with the migration on the owner's (#1826). `VaultConfigImportIT` proves the
import against a real Vault with the `api` policy, and `DatabaseLoginIT` proves a rotation
past `max_ttl` against a real MariaDB. The poll (#1823) reaches every consumer: Brevo,
Google Calendar, the mail sender and the bounce poller (#1823), the two-factor key, the JWT
secret and the Vault OIDC client secret (#1827), and the Discord REST client and gateway
(#1828). Until the contract step removes the api Deployment's `EMAIL_BOUNCE_IMAP_PASSWORD`,
that variable outranks Vault for the bounce password.
