# ADR-024: Scoped Signup Continuation Tokens

## Status
Accepted

## Context

Public membership signup collects data across four steps: personal details, address,
acceptance of the membership conditions, and email confirmation. The account is
created at the first step so a confirmation email can be sent, but the remaining
writes target endpoints guarded by `@PreAuthorize`, which requires a session the
applicant does not yet have.

Something has to authorise those writes for an applicant who is provably mid-signup
but not yet authenticated. Three mechanisms were available.

**A JWT with a marker claim.** Mint a normal, normally-signed access token carrying
`signupToken: true`. Every existing guard would accept it unchanged, so no
controller needs modifying.

**A synthetic Spring Security principal.** Convert the token into an
`Authentication` with a narrow role, so `@PreAuthorize` expressions resolve.

**A header-borne capability checked by named endpoints.** The token travels in a
custom header and each accepting endpoint verifies it itself.

The first two are attractive because they require no changes to the address and
membership controllers. They are also the reason this ADR exists: both produce a
credential that satisfies `hasPermission(#principal.id, 'User', 'write')`, which is
the guard on self-service profile updates, event signups and everything else a user
may do to their own account. A credential minted to save an address would silently
carry authority over the whole self-service surface, and nothing in the type system
or the tests would say so. The blast radius is invisible at the point where the
mistake is made.

## Decision

Signup continuation is a **capability presented in an `X-Signup-Token` header and
verified by the three endpoints that accept it**. It is not a JWT, not an
`Authorization` credential, and never becomes a Spring Security principal.

### Token record

The token reuses `RecoveryToken` and its `selector.verifier` scheme: a 128-bit
selector identifies the row, a 256-bit verifier is stored BCrypt-hashed and compared
on presentation. It is distinguished by a new `TokenPurpose` value.

```kotlin
@Schema(enumAsRef = true)
enum class TokenPurpose {
    USER_ACTIVATION,
    MEMBER_ACTIVATION,  // TODO: remove once all members have activated their accounts
    PASSWORD_RESET,
    SIGNUP_CONTINUATION
}
```

The enum is renamed from `ResetType`. A signup continuation is not a reset, and the
persisted strings in `recovery_tokens.type` are the enum *values*, which are
unchanged — so the rename costs a Kotlin rename and an SDK regeneration, and no data
migration.

### Issue and scope

`SignupTokenService` owns the lifecycle and is the only collaborator the signup
endpoints have for authorisation.

```kotlin
@Service
class SignupTokenService(
    private val tokenFactory: RecoveryTokenFactory,
    private val tokenValidator: RecoveryTokenValidator
) {
    /** Issued by POST /signup. Returned in the response body; never emailed. */
    fun issue(user: User): SignupSession

    /** Resolves the account this token speaks for. Throws on any failure. */
    fun resolveUser(rawToken: String): User

    /** Called when the signup completes. */
    fun retire(userId: Long)
}
```

Three properties carry the safety of the design:

- **The account comes from the token, never from the request.** Request bodies on
  token-scoped endpoints do not carry a `userId` field at all, so there is no
  parameter to tamper with and no equality check to forget.
- **The token is never transmitted out of band.** It is returned once in the
  `POST /signup` response body and held in `sessionStorage`. It is not emailed and
  is never placed in a URL, so it cannot leak through mail relays, browser history,
  or a `Referer` header.
- **It is single-purpose, not single-use.** Going back a step, retrying after a
  dropped connection and double-clicking submit all present the same token again.
  A token consumed on first use would break all three. Its bound is a 2-hour TTL
  plus retirement when the signup completes — see
  [ADR-025](ADR-025-membership-commit-rendezvous.md).

### Accepting endpoints

Exactly four, all `@PermitAll`, all in `SignupController`:

| Method | Path | Authorises |
|--------|------|------------|
| `PATCH` | `/signup/details` | Correct that account's own details while it is unconfirmed |
| `POST` | `/signup/address` | Save the address on the token's own account |
| `POST` | `/signup/apply` | Submit that account's membership application |
| `PATCH` | `/signup/email` | Correct that account's email address while it is unconfirmed |

Adding a fifth is an amendment to this ADR, not a routine change. That is the point
of enumerating them.

`/signup/details` was the fourth, added so an applicant waiting on a confirmation
email can fix what they typed rather than abandon the form. It carries two
deliberate exclusions. The email address is not among the fields, because changing
it has to retire the outstanding confirmation link, and that belongs to the one
route that already does it. The password is not either: nobody can sign in to an
unconfirmed account, so a mistyped password costs nothing until the account works,
at which point password reset is the route.

Uniqueness for that route is checked inside the handler rather than by the
`@UniqueUserCommand` validator the other write commands use. The validator needs
the subject's id to exclude the account from its own uniqueness check, and the id
is only known once the token resolves — which happens in the handler, after
validation has run. Left to the validator, an applicant re-submitting the form
without edits would be told their own username was taken.

### What the token cannot do

It cannot read the account back — the four endpoints write and acknowledge, and the
only state they return is the two booleans describing what just happened. It cannot
change a password, act on another account, or satisfy any `@PreAuthorize` expression,
because it never reaches the security context.

### Rate limits

All four paths are declared in `PublicAuthRateLimitFilter` alongside the existing
public auth paths: 10/min for the three writes, 3/10min for the email correction,
which sends mail.

### Who gets a token, and who does not

Only a new applicant. An applicant who is already signed in is issued no token at all:
they join through their session, using `PUT /users/{id}`, `POST /addresses` and
`POST /memberships`, all already guarded by `@PreAuthorize`.

This is not a fallback but the path for a whole class of applicant — somebody who made
an account earlier and is joining now. It is available because signing in requires
`enabled = true`, so a signed-in applicant has already proven the email address the
token exists to work around.

It also means the token's scope never has to widen to cover recovery. An applicant who
loses their tab confirms their email, signs in, and is then simply on the signed-in
path.

## Consequences

### Positive
- **The blast radius is enumerable.** Three endpoints, each verifying the token
  itself. Reviewing the token's authority means reading one controller.
- **No tampering surface for account selection.** With no `userId` in the request,
  cross-account abuse has no parameter to attack.
- **Existing guards are untouched.** No `@PreAuthorize` expression has to learn
  about a second kind of caller, so no existing authorisation rule is weakened.
- **Ordinary progressive-form behaviour works.** Back, retry and double-submit all
  present the same token and all succeed.
- **No schema change.** `recovery_tokens` already has the shape.

### Negative
- **The address write is duplicated.** `POST /signup/address` and `POST /addresses`
  both create an address, differing only in how the account is resolved. Both
  delegate to the same command, so the duplication is a controller method rather
  than logic, but it is duplication.
- **A custom header is off the beaten path.** `X-Signup-Token` is not something a
  Spring developer expects to matter, so it needs the ArchUnit-adjacent equivalent
  of a signpost: the security test named in the flow doc is what stops someone
  "tidying" it into an `Authorization` header.
- **Two hours is a guess.** Long enough for a form with a mail round-trip in the
  middle, short enough that a leaked `sessionStorage` value is stale quickly. It has
  no evidence behind it yet.

### Neutral
- **The token is a bearer credential for its TTL.** Anything with access to the
  browser's `sessionStorage` can finish that one signup. It cannot do anything else,
  and an attacker with that access already has the page.
- **Abandoned signups accumulate.** An unconfirmed account with an address and an
  acceptance stamp persists. There is no reaper; retention is a separate decision.

## Related ADRs
- [ADR-009: JWT Authentication Strategy](ADR-009-jwt-authentication-strategy.md) — the mechanism deliberately not reused here
- [ADR-014: Permission Evaluation Strategy](ADR-014-permission-evaluation-strategy.md) — the guards this token stays outside of
- [ADR-025: Membership Commit Rendezvous](ADR-025-membership-commit-rendezvous.md) — what retires this token
- [ADR-008: Exception Handling Strategy](ADR-008-exception-handling-strategy.md) — how token failures surface

## Related documentation
- [Membership signup flow](../../flows/membership-signup/README.md)
