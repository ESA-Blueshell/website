# ADR-030: A Sign-In Is a Server-Side Record

## Status
Accepted

## Implementation status

Decided, not built. The two-factor epic delivers it; until then the sign-in works as
`docs/flows/sign-in` describes.

## Context

Two credentials each authenticate a request on their own. The `BSH_AUTH` cookie holds a
JWT that `JwtAuthFilter` accepts from the cookie or from an `Authorization` header, and
the Valkey `SESSION` cookie re-authenticates through `HttpSessionSecurityContextRepository`
with no JWT at all. The JWT is also handed to the SPA in the `/auth` response body, which
keeps it in memory and sends it as a bearer token.

Two-factor, and the hardening agreed alongside it, needs state that belongs to one sign-in
and is read on every request:

- whether the second factor was passed, and when (step-up, OIDC)
- which copy of the cookie is current, so a stolen older copy is noticed
- when the sign-in began and when it was last used, so it can end on both
- which browser it began in
- whether the person has since been signed out everywhere

A JWT alone cannot carry any of it past the moment it is minted, and the two credentials
would each need the same checks, in two places that can drift.

**Keep both and write the state into each** was rejected for that reason: every rule is
two implementations and a bypass wherever one is forgotten.

**Drop the JWT and use the session cookie** was rejected because `/oauth2/forward-auth`
gates Traefik, Vault, Headlamp and Stalwart off a cookie scoped to `esa-blueshell.nl`,
and widening the session cookie to every subdomain moves the same problem rather than
solving it.

**Keep the JWT stateless and rotate it harder** was rejected because rotation without a
record of the current copy cannot tell a stale copy from a stolen one.

## Decision

**A sign-in is a record in Valkey. The `BSH_AUTH` JWT is a view of it, and is refused
whenever the record disagrees.**

- The JWT carries the sign-in's id as `sid`. Every request looks the record up; a JWT
  whose `sid` has no live record is refused, whatever its signature and expiry say.
- The record holds the person, the factors passed and when, the current and previous
  `jti`, when the sign-in began and was last used, the browser family and platform it
  began in, and the person's security stamp at the time.
- **The cookie is the only carrier.** The token leaves the `/auth` response body and the
  SPA's store; a bearer header is refused for a browser sign-in. A script on the page
  can no longer read the credential.
- **The `SESSION` cookie no longer authenticates.** The servlet session survives for what
  Spring Authorization Server keeps in it during an authorize round trip, and nothing else.
- **The cookie rotates.** A request made with a credential older than five minutes is
  answered with a new one. The previous `jti` is still accepted for sixty seconds, so
  parallel calls and other tabs holding it do not sign the reader out.
- **A copy older than that ends the sign-in.** It means two holders, and the record cannot
  tell which is the owner, so both lose it. The person is sent a security notification.
- **Thirty days absolute, fourteen days idle.** The sign-in ends thirty days after it
  began however much it is used, and after fourteen days without a request.
- **The sign-in is pinned to a browser family and platform**, read coarsely from the
  User-Agent at sign-in. A request from another family or platform ends it like a stale
  copy. The version is left out, so a browser update does not sign anybody out.
- **Every person has a security stamp.** Signing out everywhere, a password change or
  reset, a two-factor change or reset and a lock all bump it. A record carrying an older
  stamp is dead. This is the per-person revocation the `jti` store never offered.
- **Forward-auth and OIDC read the same record.** An admin tool behind forward-auth sees
  exactly the authority the site sees, including a dormant role (ADR-031).
- **An OIDC refresh token lives only as long as the sign-in behind it.** Every authorize
  asks for a fresh code; a Vault or Headlamp refresh token then renews without one, while
  that sign-in lives. Its end, a lock, a two-factor reset and signing out everywhere kill
  the refresh tokens issued under it.

## Consequences

Every authenticated request costs a Valkey read it did not cost before, and a rotation
costs a write every five minutes per active sign-in. An unreachable Valkey now signs
readers out rather than reading as "not revoked", which inverts the fail-open read
`JwtRevocationService` chose; a sign-in whose state cannot be read cannot be trusted.

The browser pin is a speed bump. A thief who copies the cookie can copy the User-Agent
string too. Rotation and the stale-copy rule are what bound a stolen cookie; the pin only
catches the careless case. Device Bound Session Credentials would bind the sign-in to the
machine and are left for a later decision, since not every browser supports them.

Sixty seconds of grace means a stolen copy used within a minute of theft is not noticed
until the owner's next rotation, at which point the sign-in ends for both.

`UserTestSupport.bearer` mints a token with no record behind it. The integration suites
move to a helper that creates a sign-in and sends its cookie.

The sign-in list on the security page is a read of these records, so it costs nothing
beyond them.

## Related

- [ADR-009: JWT Authentication Strategy](ADR-009-jwt-authentication-strategy.md) — superseded
- [ADR-031: Two-Factor Authentication](ADR-031-two-factor-authentication.md) — what the record's factor state is for
- [ADR-024: Scoped Signup Continuation Tokens](ADR-024-scoped-signup-continuation-tokens.md) — a capability that stays outside the sign-in
- `docs/CONTEXT.md`, **Two-factor** — sign-in, trusted browser, locked account
