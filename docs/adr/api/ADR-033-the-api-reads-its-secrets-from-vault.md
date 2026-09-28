# ADR-033: The Api Reads Its Secrets From Vault, and They Rotate Without a Restart

## Status
Accepted. Not built yet: the slices of epic #1824 build it, in order #1682 (the KV import),
#1826 (leased database credentials), then #1823, #1827 and #1828 (rotation without a restart).
Until #1682 lands, `main` still reads secrets from an injector-rendered env file.

## Context

The api read Vault four ways, and only one of them was live. The Vault injector rendered 17
keys from `secret/api` into an env file once at pod start, and the start script exported
them. The Vault Secrets Operator synced the same path into a Kubernetes Secret, which the api
read one key from, and the mail passwords arrived the same way from a second synced Secret.
Spring Cloud Vault was on, but its `vault://` import read `secret/BlueshellAPI` and
`secret/application`, not `secret/api`, so it delivered nothing the api used. Only Transit,
which signs the api's OIDC and forward-auth tokens, reached Vault while the api ran.

Every one of those copies went stale when a key rotated: the running api kept the old value
until somebody restarted the pod, and a restart after a rotation is easy to forget. The api
and the migrate Job also logged in to MariaDB with a static password copied into
`secret/api`, beside a dynamic credentials engine configured for them and switched off.

## Decision

**The api and the migrate Job read secrets from Vault through Spring Cloud Vault only.** They
log in with Kubernetes auth as the `api` role. No rendered env file, no environment variable
and no synced Secret sits between Vault and the api, because each copy goes stale when a key
rotates.

**Each KV key is named for the Spring property it fills.** `spring.config.import` names
`secret/api` and `secret/platform/mail`, and `application.yaml` binds the keys directly, with
no `${ENV_VAR}` placeholder for a secret. Dev and test run without Vault on their profile
defaults. Adding a key is one KV write and one property, and the Vault bootstrap doc says how
to rotate it.

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
Gatus, cert-manager, external-dns and Flux. The api and the migrate Job do not use it.

## Considered Options

- **The synced Secret, read as environment variables.** Keeps a restarted api starting while
  Vault is sealed. But a container's environment never changes after it starts, so every
  rotation needs a restart, and the operator's `rolloutRestartTargets` only automates that
  restart.
- **The synced Secret mounted as files, read with `configtree:`.** The files do update in
  place, so the same refresh code would work. It adds the operator's `refreshAfter` and the
  kubelet's sync delay on top of the poll, for a copy the api does not need, since Spring
  Cloud Vault already talks to Vault.
- **The Vault injector.** Renders once at pod start, the same staleness as environment
  variables, plus an init container and a template that repeats every key.
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
