# ADR-031: Two-Factor Authentication

## Status
Accepted

## Implementation status

Decided, not built. The two-factor epic delivers it on top of ADR-030.

## Context

A password is the only thing between a stranger and an account, and the accounts that
hold a granted role reach further than the site: forward-auth opens Traefik, Vault and
Headlamp to admins and Stalwart to the board, and OIDC signs admins in to Vault and
Headlamp. Two-factor is optional for everybody and required for anybody holding board,
treasurer or admin.

A reference implementation of TOTP that was studied showed which parts are easy to get
wrong. Its "code pending" state was a signed JWT that the resource server also accepted
as a full bearer token, so the password alone reached every authenticated endpoint. Its
codes could be replayed within their thirty seconds, attempts were unlimited, the secret
was stored in plaintext and mandatory enrolment was a client-side redirect.

## Decision

### Factors

**An authenticator app (TOTP, RFC 6238, SHA-1, six digits, thirty seconds) and ten
single-use backup codes. Never email, never SMS.** The inbox already resets the password,
so an emailed code is the same factor twice. SMS can be taken over by moving the number
and costs money per message. WebAuthn is left for a later slice; the model holds a list of
factors so it lands without reshaping what exists.

Backup codes are ten characters in two groups of five, about fifty bits each, and are
stored as SHA-256 hashes. That entropy puts guessing out of reach without a slow hash,
the way `GuestAccessTokenCodec` already treats its tokens. They are shown once;
regenerating replaces all ten.

Two-factor turns on when the person confirms the backup codes are saved, and that
confirmation is stored. The first right code only proves the secret. Until the
confirmation, nothing is on, so a person who closes the tab before saving the codes is
not left with a factor and no way round losing it; set-up starts over.

### The secret

**Encrypted with AES-GCM under an application key held in Vault KV, with the key's id
stored on each row.**

- **Plaintext** was rejected: a database dump would be every second factor.
- **Vault Transit** keeps the key inside Vault, but the api's policy allows signing only.
  It would need a new key, a new policy and new client methods, and every sign-in would
  call Vault.

The key arrives with `secret/data/api` like `JWT_SECRET` does, and the hardening guard
refuses a missing or short key outside dev and test. The key id lets a new key encrypt
while an old one still decrypts, so rotating it is a re-encryption job rather than an
outage.

### The challenge

**The state between a correct password and a correct code is server-side, single-use and
no kind of credential.** It lasts five minutes and allows five codes, then the person
starts again from the password. It carries no authority to anything but answering itself,
which is the property the reference implementation lost by making it a JWT.

A code is refused if its time step is at or before the last one that person used, so a
code seen over a shoulder cannot be used twice. One step of clock skew is allowed either
way.

Wrong codes count per account in Valkey, where every replica sees them: ten in fifteen
minutes stops further codes for that account and sends a security notification, since
whoever is guessing already has the password. Nothing locks the account outright, because
that would hand anybody holding the password a way to lock the owner out.

### Mandatory two-factor

**A granted role held without two-factor is dormant: the row exists, and nothing it would
allow is allowed.** The api, forward-auth and OIDC all read it that way, so no route sees
the role early.

- **Refusing the grant** would make an admin chase the person before ticking a box, and
  would still leave the question of a role held when two-factor is later reset.
- **A grace period** would give board powers to a password alone for its length.

A person with a dormant role signs in, is sent to set up two-factor, and has the role the
moment they finish. On the release that ships this, every current holder's roles go
dormant together.

### Losing the factor

**An admin resets another person's two-factor; the reset signs them out everywhere and
emails a re-enrolment link.** The person needs the password and the link to get back in.
Clearing the factor alone was rejected: somebody holding the password who talks one admin
round would be in on the password alone. An admin cannot reset their own. A reset needs a
reason and a step-up, and is recorded.

There is no self-service path around a lost phone and lost backup codes. Every such path
makes the inbox a slow way past the second factor.

When no admin can act — the last admin has lost both, or is locked — an operator with
cluster access runs a command in the api image that performs the same reset on a named
person. It is recorded with the operator as actor and tells every admin.

### The lock link

**Every security notification carries a link that locks the account, and none undoes the
change it reports.** A common reason to change an email address is losing the old inbox.
Were the link sent there able to revert the change, whoever took that inbox would get the
account back with one click. A lock is the worst that link can do, and a lock only
inconveniences; an admin unlocks after hearing from the person.

Lock links are the exception to "one live link per kind" that `recovery_tokens` otherwise
keeps: issuing one never retires another. Once somebody has changed the address, every
later notification goes to them, so a new link must not retire the one the owner holds on
the old address.

## Consequences

Two-factor state is data the erasure path has to reach. Erasure removes the secret, the
codes and the trusted browsers, and a restored account comes back without two-factor.

Security events get a table of their own, like `role_changes`, kept for twelve months, and
the privacy policy gains an entry for it.

Everything that reads the time for these rules takes an injected clock, which is testing
ADR-008.

A person with a dormant role who never enrols keeps member powers indefinitely. The role
panel shows the role as dormant so an admin can see why.

## Related

- [ADR-030: A Sign-In Is a Server-Side Record](ADR-030-a-sign-in-is-a-server-side-record.md) — where the factor state lives
- [ADR-028: A Derived Role Is Not Hand-Assignable](ADR-028-a-derived-role-is-not-hand-assignable.md) — the granted roles this makes demanding
- [ADR-024: Scoped Signup Continuation Tokens](ADR-024-scoped-signup-continuation-tokens.md) — `recovery_tokens` and the selector scheme the new links reuse
- [testing ADR-008](../testing/ADR-008-time-is-injected-where-a-rule-reads-it.md) — how these rules are tested against time
- `docs/CONTEXT.md`, **Access** and **Two-factor**
