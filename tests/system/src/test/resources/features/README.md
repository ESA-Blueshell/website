# Acceptance features

Business-readable specifications for the account and membership flows, executed
by Cucumber against the running compose stack.

```bash
# against a running stack (see .github/scripts/start-system-test-stack.sh)
./gradlew :tests:system:acceptanceTest

# one area
./gradlew :tests:system:acceptanceTest -PcucumberTags="@membership"
```

CI runs them as the `acceptance-features` job, separate from the sharded
`system-tests` job, with its own HTML report as a build artifact.

## What belongs here, and what does not

These features describe **what the system guarantees**, in the language the board
would use. They drive the stack over HTTP and assert against the database and the
mail outbox, with no browser — which is what makes the suite fast enough to be its
own CI step.

They are deliberately not UI tests. Whether a page renders and a button is
reachable is covered by the Playwright classes in
`src/test/kotlin/net/blueshell/api/system/frontend/`. Keeping the two apart means a
CSS change cannot fail a rule about who may become a member, and a rule change
cannot be papered over by a passing click-through.

## Conventions

**Declarative, not imperative.** A step says what the applicant is doing, not how
the screen does it. `When they submit their membership application`, never
`When they click the "Complete membership" button`. UI wording and HTTP paths
change constantly; what the association guarantees does not.

**Roles, not personas.** Steps talk about "an applicant" and "they". A named person
adds nothing and invites scenarios that read as anecdotes.

**One behaviour per scenario, named as the behaviour.** The scenario name is the
sentence that appears in the report, so it should state the rule being defended —
`Submitting the same application twice does not join twice`, not `Test membership
twice`.

**`Given` is state, `When` is the one action under test, `Then` only asserts.** A
`Then` that changes state makes a failure impossible to place. A scenario with two
`When` blocks is usually two scenarios — the exception is a sequence where the
ordering *is* the behaviour, as in the two arrival orders of the rendezvous.

**Push variation into `Examples`.** A new precondition should mean a new table row,
not a new step definition. `membership-eligibility.feature` is the model: one step
resolves every omission.

**No transport in the feature files.** Status codes, cookies, headers, paths and
SQL live in step definitions. `AcceptanceApi` is the single place that knows the
flow is driven over HTTP, so swapping the driver touches one file and no feature.

**Scenarios are independent and self-cleaning.** Every scenario creates its own
account; the `@After` hook in `Hooks.kt` erases whatever it made. Never rely on
another scenario's leftovers, and never assume execution order.

**State is injected, never static.** `AcceptanceWorld` is constructed per scenario
by cucumber-picocontainer and passed into each step class. Static mutable state
would make the suite order-dependent the moment it runs in parallel.

## Tags

| Tag | Meaning |
|-----|---------|
| `@system` | Every feature carries it; required by the project-wide JUnit tag filter |
| `@account` | Creating an account and confirming its address |
| `@signin` | What the sign-in gate answers, and what it refuses to reveal |
| `@membership` | Applying for and holding a membership |
| `@security` | What a credential may and may not do |
| `@harness` | The self-check that proves the harness itself runs |

A `@pending` tag is how a specification is allowed to run ahead of the code: bind
the step so the feature reads as finished, throw `PendingException`, and exclude the
tag from the default filter. Drop it as the behaviour lands. Nothing is pending now.

`@harness` exists so a green job cannot be mistaken for a job that ran nothing: if
every other feature were filtered out, the self-check would still have to pass.

## Layout

```
features/                       the specifications
steps/AccountSteps.kt           registering and confirming
steps/SignInSteps.kt            the sign-in gate
steps/MembershipSteps.kt        the application, its preconditions, what is held after
steps/EmailSteps.kt             what was delivered
steps/SignupSessionSteps.kt     the token-carried steps
steps/EmailCorrectionSteps.kt   correcting a mistyped address
steps/PaymentEmailSteps.kt      the treasurer's sends, and what each member received
steps/BulkContributionSteps.kt  recording contributions in bulk
steps/RecoveryEmailSteps.kt     password recovery mail
steps/JoiningContributionSteps.kt  what a new member is told they owe
steps/HarnessSelfCheckSteps.kt  the self-check that proves the harness runs
steps/ActorSteps.kt             who a scenario acts as, where two features need the same person
steps/ResponseSteps.kt          what the api answered
AcceptanceWorld.kt              per-scenario state and the cleanup registry
AcceptanceApi.kt                the only file that knows this is HTTP
Inbox.kt                        waiting for an email to arrive, or for none to
Hooks.kt                        per-scenario cleanup
```

The flow these features describe is documented in
[`docs/flows/membership-signup/README.md`](../../../../../../docs/flows/membership-signup/README.md).

## Step ownership

Cucumber resolves glue at runtime and by step text, not by class. A duplicate or a
missing definition therefore breaks the *whole* glue rather than the scenarios that
use it, and neither `compileTestKotlin` nor a single-tag run says so: on #921 one
collision produced 33 failures across four unrelated features.

Two rules keep that knowable.

**A step used by exactly one feature lives with that feature's steps.** Reading the
class tells you who calls it.

**A step used by more than one feature has one owning class, named for the concern
rather than for a feature.** `ActorSteps` owns the people a scenario signs in as when
two features need the same one; `ResponseSteps` owns what the api answered. Nothing
about bulk contribution marking may own a step that recovery emails also says.

The steps borrowed by more than one feature today, and who says them:

| Step | Owner | Features |
|---|---|---|
| `an applicant who has registered an account` | `AccountSteps` | account-creation, sign-in |
| `an applicant with an account they can sign in to` | `AccountSteps` | membership-eligibility, membership-join-with-account, sign-in |
| `their account is not yet usable` | `AccountSteps` | account-creation, membership-join-new-applicant |
| `their account is usable` | `AccountSteps` | account-creation, membership-join-new-applicant |
| `they confirm their email address` | `AccountSteps` | account-creation, membership-join-new-applicant, sign-in |
| `they have confirmed their email address` | `AccountSteps` | account-creation, signup-session-scope |
| `a board member signed in to the user manager` | `ActorSteps` | bulk-contribution-marking, payment-emails |
| `they correct their email address` | `EmailCorrectionSteps` | membership-join-new-applicant, signup-session-scope |
| `a contribution period covering today` | `JoiningContributionSteps` | membership-join-new-applicant, membership-join-with-account |
| `they are told what they owe and how to pay it` | `JoiningContributionSteps` | membership-join-new-applicant, membership-join-with-account |
| `they are a member` | `MembershipSteps` | membership-join-new-applicant, membership-join-with-account |
| `they are not a member` | `MembershipSteps` | account-creation, membership-eligibility, membership-join-new-applicant, sign-in |
| `they have an address on file` | `MembershipSteps` | membership-eligibility, membership-join-with-account |
| `they have completed their member profile` | `MembershipSteps` | membership-eligibility, membership-join-with-account |
| `they have exactly one membership` | `MembershipSteps` | membership-eligibility, membership-join-new-applicant |
| `they submit their membership application` | `MembershipSteps` | membership-eligibility, membership-join-with-account |
| `the request is forbidden` | `ResponseSteps` | bulk-contribution-marking, recovery-emails |
| `the request is refused` | `ResponseSteps` | account-creation, membership-join-new-applicant, signup-session-scope |
| `the request is refused as invalid` | `ResponseSteps` | bulk-contribution-marking, recovery-emails |
| `an applicant who is not signed in` | `SignupSessionSteps` | account-creation, membership-join-new-applicant, signup-session-scope |
| `their first name is {string}` | `SignupSessionSteps` | account-creation, membership-join-new-applicant |
| `they change their first name to {string}` | `SignupSessionSteps` | account-creation, membership-join-new-applicant |
| `they have accepted the membership conditions during signup` | `SignupSessionSteps` | membership-join-new-applicant, signup-session-scope |
| `they have begun a membership signup` | `SignupSessionSteps` | account-creation, membership-join-new-applicant, signup-session-scope |
| `they have saved their address during signup` | `SignupSessionSteps` | membership-join-new-applicant, signup-session-scope |
| `they save their address during signup` | `SignupSessionSteps` | membership-join-new-applicant, signup-session-scope |

`ResponseSteps` holds the transport vocabulary the README forbids in the features
themselves. It is written to be deleted: as #965 and #966 rewrite the two features
that still assert status codes, its steps lose their callers, and what is left when
nothing calls it is the whole of the debt.

`SignupSessionSteps` also binds `they begin a membership signup` as a `@When` alias of
a `@Given` every feature uses in its past tense. No feature says it. It is left alone
here because this ticket moves steps and deletes none.
