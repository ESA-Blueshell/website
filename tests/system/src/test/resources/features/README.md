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
steps/HarnessSelfCheckSteps.kt  the self-check that proves the harness runs
AcceptanceWorld.kt              per-scenario state and the cleanup registry
AcceptanceApi.kt                the only file that knows this is HTTP
Hooks.kt                        per-scenario cleanup
```

The flow these features describe is documented in
[`docs/flows/membership-signup/README.md`](../../../../../../docs/flows/membership-signup/README.md).
