# ADR-025: Membership Commit Rendezvous

## Status
Accepted

## Context

Membership requires two facts about an applicant, and they are established by two
unrelated actions:

- **The email address is confirmed** — the applicant opened a link sent to it.
- **The membership application is submitted** — an address is on file, a member
  profile exists, and the membership conditions have been accepted.

The confirmation email is dispatched when the account is created, at the first step
of the form. The application is completed at the third. So the confirmation link is
live for the whole time the applicant is still filling things in, and the two actions
can complete **in either order**. An applicant who sees the email arrive and clicks
it straight away is behaving reasonably, not exceptionally.

A membership is not a cosmetic flag. It grants `Role.MEMBER`, enters the member list,
feeds cohort synchronisation and the newsletter, and represents a declared obligation
to the association under its Statutes and Domestic Regulations. It must never exist
for an email address nobody has proven they can read, or without a recorded
acceptance of those conditions.

Three placements for the commit were considered.

**At application submission.** Create the membership when the form completes, and
reconcile later. This is what most consumer signup flows do. It means a stranger can
enrol somebody else's address into a membership and the association only discovers it
if someone runs a reconciliation job. It converts a hard invariant into a scheduled
apology.

**At email confirmation.** Create the membership inside the activation transaction.
This holds the invariant when the application was finished first — but when
confirmation arrives first there is nothing to commit, and the applicant is left with
an enabled account, no membership, and no second chance at the commit. Pairing it
with retiring the signup token at activation, which is the natural reading, actively
strands them: the fast path is gone and the only way to finish is to sign in, which
is the interruption the whole flow exists to remove.

**At whichever action completes the set.** Neither action owns the commit; the
transition belongs to the pair.

## Decision

The membership is created by a **rendezvous**: one idempotent function evaluates all
preconditions and creates the membership at most once, and it is invoked at the end of
every write that could have supplied the last missing one. Whichever write completes the
set is the one that commits.

```kotlin
@Service
class SignupCompletionService(
    private val users: UserService,
    private val memberships: MembershipService,
    private val signupTokens: SignupTokenService
) {
    /**
     * Creates the membership when every precondition holds. Safe to call after any
     * write that could have supplied the last missing one; a no-op otherwise.
     */
    @Transactional
    fun completeIfReady(userId: Long): SignupOutcome
}
```

Called from exactly three places:

- `POST /signup/apply`, after stamping the acceptance — the new applicant's submission
- `POST /memberships`, after stamping the acceptance — the signed-in applicant's
  submission ([ADR-024](ADR-024-scoped-signup-continuation-tokens.md) explains why that
  path needs no token)
- `POST /recovery/user/activate`, after enabling the account

Two submission routes exist because an account and a membership are separate things: an
applicant may already have an account and be joining now. They differ only in how the
caller is authorised, and both end in the same function, so neither can drift from the
other.

### Preconditions, read from the database

Every precondition is re-read inside the transaction. None is taken from a token, a
request body, or a JWT claim.

1. No active membership already exists — this is what makes the function idempotent.
2. `users.enabled` is true. For a signed-in applicant this is true on arrival, because
   `UserAuthenticationProvider` rejects a disabled account, so their rendezvous
   completes the moment the application is submitted.
3. A `MemberProfile` exists.
4. `conditionsAcceptedAt` is set on that profile.
5. An `Address` is on file.

If all five hold, a `Membership` is created with `startDate = today` and
`memberType = REGULAR`, `MembershipChanged(CREATED)` is published, and
`MembershipEventListener` grants `Role.MEMBER`. The signup continuation token is then
retired ([ADR-024](ADR-024-scoped-signup-continuation-tokens.md)).

Reading preconditions from the database rather than the principal also fixes a
latent problem in the authenticated route: `principal.addressId` and
`principal.personDetailsId` come from a cached principal minted before the address
existed, so they can be stale at exactly the moment they are consulted.

### The signup token survives confirmation

Confirming an email address enables the account. It does not retire the signup token.
An enabled account and a finished application are different states, and an applicant
who reads their email promptly must be able to carry on in the tab they are already
in. The token is retired only when the membership starts, or when it expires.

### Acceptance of the conditions becomes a stored fact

`member_profiles.conditions_accepted_at` is added — one nullable timestamp on the
record that already *is* the membership application, keyed on the user id. Making it
precondition 4 gives it a reason to be checked on every path.

### Both callers report the outcome

Either action can be the one that commits, so neither response can assume its own
ending:

- `POST /signup/apply` returns `{ emailConfirmed, membershipStarted }`
- `POST /memberships` returns the same shape, so both submission routes share one
  contract and the stepper reads one field regardless of which path it is on
- `POST /recovery/user/activate` returns `{ membershipStarted }`

The frontend composes the wording from those flags — "your membership has started"
or "confirm your email address" — so no polling is needed to discover a state the
server already knows.

## Consequences

### Positive
- **The invariant is order-independent.** No arrival sequence, race, or retry can
  produce a membership without a confirmed email address and a recorded acceptance.
- **One place to read, one place to test.** The rule is a single function rather than a
  condition duplicated across three call sites that can drift apart.
- **Idempotent by construction.** The "already a member" check is the first
  precondition, so double submissions and repeated activations are inert.
- **Stronger than trusting the principal.** Preconditions are re-read from the
  database, so a stale cached principal cannot admit a membership.
- **The acceptance is real.** It is persisted and enforced rather than living in a
  frontend variable.

### Negative
- **The commit point is not obvious from a call site.** Neither controller reads as
  "this is where somebody becomes a member". The name `completeIfReady` and this ADR
  are the mitigation; the flow doc's decision diagram is where a reader should land.
- **A no-op call is the common case.** Most invocations do nothing, which reads as
  wasted work until the reason is understood.
- **One migration.** `V82__member_profile_conditions_accepted.sql`.

### Neutral
- **A signed-in applicant's rendezvous is settled on arrival.** Half the rule is
  already satisfied before they start, so `completeIfReady` commits on their first and
  only submission. The rule is not bypassed, just already half met.
- **Plain account creation never completes the rendezvous.** `/account/create` makes
  no member profile, so precondition 3 fails permanently. Those accounts confirm
  their email and stop, which is the intended ending rather than a special case.
- **An enabled non-member is a normal resting state.** Confirming an address says
  nothing about wanting to join.
- **Abandoned applications persist.** An account with an address and an acceptance
  but no confirmation stays that way. Retention is a separate decision.

## Related ADRs
- [ADR-024: Scoped Signup Continuation Tokens](ADR-024-scoped-signup-continuation-tokens.md) — the credential this retires
- [ADR-006: Event-Driven Architecture](ADR-006-event-driven-architecture.md) — `MembershipChanged` and the role grant
- [ADR-010: Database Migrations with Flyway](ADR-010-database-migrations-with-flyway.md) — the acceptance column
- [ADR-018: Data Ownership in Modular Monolith](ADR-018-data-ownership-in-modular-monolith.md) — why the acceptance lives on `MemberProfile`

## Related documentation
- [Membership signup flow](../../flows/membership-signup/README.md)
