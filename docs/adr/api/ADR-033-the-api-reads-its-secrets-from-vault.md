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
re-reads them, and Vault tells the api nothing when a key changes. The api calls
`ContextRefresher.refresh()` on an interval set in `application.yaml`. Each consumer reads
the current value rather than the one it was built with: a client is rebuilt, or reads the
key per call.

**A refresh never makes things worse.** A refresh that cannot reach Vault keeps the values in
use. A value a consumer refuses, such as a two-factor key of the wrong length, keeps the
previous one in use.

**A rotation does not end what the old value made.** A retired two-factor key still opens
what it sealed (api ADR-031). A token signed with the previous JWT secret stays valid until it
expires.

**Database credentials are leased.** The api and the migrate Job take their MariaDB login
from `database/creds/api`. The lease renews while the pod runs, and the connection pool takes
a new login before the old one reaches `max_ttl` and is revoked.

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
- **Push instead of poll.** Vault sends nothing to a KV v2 reader when a key changes, so a
  push would need a separate notifier. Polling one path on an interval is cheaper than
  running one.

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

Kubernetes auth, the KV import, fail-fast and the key names are built (#1682);
`VaultConfigImportIT` proves the import against a real Vault with the `api` policy. The
database login is still a static pair in `secret/api` until #1826 leases it, and keys are
read at start until #1823, #1827 and #1828 make them rotate without a restart.
