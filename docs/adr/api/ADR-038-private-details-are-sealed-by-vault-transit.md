# ADR-038: Private Details Are Sealed by Vault Transit

## Status
Accepted

## Context

A copy of the database, whether a dump, a backup or a stolen volume, should not reveal a member's
private details. Two kinds stand out: a member's address, and their bank details for incasso.
Both are read rarely: for one person's page, a reveal, or ING's file. Neither is read on every
request.

Two-factor secrets are already encrypted, with AES-GCM under an application key from Vault KV
(ADR-031). That fits a value opened at every sign-in. It also means the key reaches the api, so
anybody holding the api's memory or its Vault token holds the key.

## Decision

**Bank details and the address are sealed by Vault Transit.** The api asks Vault to seal and to
open. The key never reaches the api, and Vault's audit log records every opening. Each kind has
its own derived key: `api-address` and `api-bank-details`. The api's policy may encrypt, decrypt
and rewrap with them, and do nothing else.

**A sealed value is tied to its member.** Each value is sealed under a context naming the field
and the member's user id (`address:42`). A sealed value copied onto another member's row fails to
open, so write access to the database alone cannot move somebody's details onto another account.

**Vault down means refused, never degraded.** Saving answers a 503 (`SealingUnavailable`). A view
shows no value. Nothing falls back to plaintext. A value that does not open under its context is
logged and shown as none.

**No list of people opens a sealed value.** Opening costs a Vault call and an audit line, so only
one person's page, a reveal or ING's file opens one.

**Phone number, date of birth and student number stay plaintext.** They are read on too many
pages to be worth a Vault call each.

**Dev and test use a stand-in** (`privacy.sealing: stand-in`). It keeps the same rule, a value
opens only under the context it was sealed with, through AES-GCM with the context as associated
data and a key derived from the key's name. It protects nothing. Production sets
`privacy.sealing: vault`, and the api then refuses to start unless it can seal with both keys.

**Bank details have no app-side cipher.** A mandate's IBAN and account holder were first sealed
under an application key that fell back to the two-factor key. That cipher is gone: both values
are sealed by Transit in one call, so they share a key version, and ING's file opens a whole part
in one batched call. Two-factor secrets keep their own key in KV (ADR-031).

**Existing plaintext is sealed by a job, then dropped.** The `user.seal-addresses` job seals every
address, soft-deleted ones included, and empties the plaintext. It can run again without harm. The
plaintext columns go in the release after, once the previous release no longer reads them.

**Bank details are kept for 13 months after the last collection** (#2092), the window in which a
member can dispute a SEPA debit, and then wiped.

**Rotation:** a rotation adds a Transit key version, and a nightly job rewraps every sealed value
onto it (#2088). `min_decryption_version` is raised only to the version current at the oldest
database backup still kept, so a restored backup still opens.

## Consequences

- A database copy holds no address and no bank details without Vault, whose unseal shares are held
  by people and never stored with the backups (#2095).
- Saving an address, or a mandate, needs Vault up. A view without Vault shows the value as none.
- Sealing is per row, in one Vault call per value or batch. A list never pays for it.
- The stand-in means a dev or test database holds values the stand-in opens; that is fine, since
  it holds no real member.

## Related

- [ADR-031: Two-Factor Authentication](ADR-031-two-factor-authentication.md): two-factor secrets keep their app key from KV
- [ADR-033: The API Reads Its Secrets From Vault](ADR-033-the-api-reads-its-secrets-from-vault.md): how the api reaches Vault
- `docs/CONTEXT.md`, **Sealed**
- `platform/docs/vault-bootstrap.md`: where the keys live and which policy reaches them
