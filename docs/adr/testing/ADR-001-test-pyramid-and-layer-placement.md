# ADR-001: The Test Pyramid and Layer Placement

## Status
Accepted

## Context

The repository runs five distinct test suites. Nothing wrote down what each one is
for, so the boundaries drifted: two suites drive Playwright over the same pages,
tests requiring a Spring context sit in the source set reserved for tests that
have none, and the Cucumber features are absent from every description of how the
project is tested.

Drift of this kind is not cosmetic. A layer whose purpose is undefined cannot be
gated, because there is no principle deciding what belongs in it — and a test in the
wrong layer costs the wall-clock time of the layer it landed in while providing the
confidence of the layer it belonged to.

Two rival models were considered before settling on a pyramid. The
[Testing Trophy](https://kentcdodds.com/blog/write-tests) widens the integration
band on the argument that modern browser tooling has removed the brittleness that
motivated Cohn's shape. Google's *Software Engineering at Google* replaces scope
with **size** — small, medium, large, defined by whether a test may use a thread,
the filesystem, or the network — precisely because "unit" has no agreed meaning;
Fowler concedes the same ambiguity when he distinguishes
[solitary from sociable](https://martinfowler.com/articles/practical-test-pyramid.html)
unit tests and declines to prefer either.

The pyramid is kept, but Google's insight is taken with it: **each layer here is
defined by the resources it may use, not by how many classes it happens to touch.**
That is the only definition a build can check.

## Decision

Five layers. Each answers one question and is bounded by what it may reach for.

| Layer | Source set | Answers | May use |
|-------|-----------|---------|---------|
| Unit | `services/api/src/test` | Does this class do what it says? | Nothing outside the JVM. **No Spring context.** |
| Integration | `services/api/src/integrationTest` | Do these classes, modules and events combine to produce the required result? | Spring context, real database, real event publication |
| Acceptance | `tests/system` `acceptanceTest` | Does the product do what was agreed, in the domain's language? | The running stack over HTTP. No browser |
| System | `tests/system` `test` | Does the assembled system hold together? | The running stack and a browser |
| Frontend unit / e2e | `services/frontend` `tests/unit`, `tests/e2e` | Does the SPA behave? | jsdom / a browser against a stubbed API |

### The unit layer may not use Spring

A unit test constructs its subject directly. If a test needs `@SpringBootTest`,
`@DataJpaTest` or `@WebMvcTest`, it is an integration test wherever its file
happens to sit.

This was violated 32 times, and counting them by annotation under-reported it.
Twenty-six classes carried `@SpringBootTest` directly. Six more —
`EmailServiceIntegrationTest`, `EmailJobHandlersTest`, `RecoveryEventListenerTest`,
`ContributionReminderServiceTest`, `EventSignUpServiceEmailTest` and
`EventJobsListenerTest` — inherited it from `ServiceTestSupport` in `testFixtures`
without naming it, so a textual search missed them entirely.

**The rule is therefore about the context, not the annotation:** a test needing a
Spring context is an integration test however it acquires one. The guard that
enforces this walks the superclass chain rather than grepping.

One class stays in `src/test` by design. `OpenApiSpecGeneratorTest` carries
`@SpringBootTest` but is excluded from the `test` task by its `openapi-gen` tag
and is run only by `openApiGenTest`, which reads `sourceSets["test"]`. It never
contaminates the unit run, and moving it breaks spec generation.

The consequence was measurable rather than theoretical: before the move,
`:services:api:test` without a database failed 461 of 1253 tests, 455 of them
`ApplicationContext failure threshold (1) exceeded`. After it, the unit suite
runs to completion with no database at all, and unit branch coverage reads
40.8% — down from a 52.6% that was never a unit number.

### The system layer is bounded by real-stack risk

A system test earns its place only when **the failure it catches requires the real
stack, and the assertion — not merely the setup — is what requires it.**

Seeding a row over JDBC before driving the UI is setup, and setup through a
convenience helper does not make a test a system test. The question is what the
assertion reads.

- `CsrfSystemTest` qualifies. It asserts a 403 on a cross-origin state-changing
  request and compares the CSRF value in the body against the one in `Set-Cookie`.
  Nothing stubbed can prove that.
- `JobManagerPageSystemTest` qualifies. After driving retry through the UI it reads
  the job row back and asserts `attempts > initialAttempts`, proving the retry
  reached the runner.
- `UserManagerPageSystemTest` does not, on its delete path. It asserts
  `deleteResponse.status() == 204`, which a stubbed API demonstrates equally well.

Everything reachable with a stubbed API belongs in frontend e2e. The overlap this
resolves is real: `job-manager-trigger`, `management-recovery-lifecycle` and
`user-manager-table-surfaces` already drive the same pages as their system-test
counterparts.

Existing system tests are **not** migrated in bulk. A class moves when its page is
next touched. Bulk churn in the slowest and least stable suite would buy a tidier
inventory at the cost of the confidence the suite exists to provide.

### Acceptance features are specification, not coverage

Cucumber's authors are explicit that it is
[a collaboration tool, not a testing tool](https://cucumber.io/blog/collaboration/the-worlds-most-misunderstood-collaboration-tool/),
and that scenarios written after implementation, without business input, turn
imperative and lose their documentation value.

The nine feature files are therefore judged on whether they state a business rule
in the domain's language — not on what they cover. They carry no coverage
obligation, and they are excluded from every gate in
[ADR-003](ADR-003-coverage-counters-thresholds-and-ratchet.md). A feature that
reads as a click-by-click transcript is a defect in that feature regardless of
whether it passes.

## Implementation status

Decided, not yet enforced.

- The 32 Spring-context files under `src/test/kotlin` must move to
  `src/integrationTest/kotlin`. This is sequenced **before** the unit branch gate
  becomes blocking; until it lands the unit exec measures a contaminated set and
  its number means nothing.
- `VaultTransitJwksPlaywrightTest` sits in `tests/system` tagged only
  `@Tag("vault-oidc-live")`, so the `includeTags("system")` filter never selects
  it. Either it gains the tag or the policy records why live-credential tests are
  exempt.
- No check enforces the source-set rule. An ArchUnit test asserting that no class
  under `src/test` references a Spring test annotation would.

## Consequences

### Positive
- **Every layer has a falsifiable admission test.** "Does the assertion need the
  real stack?" and "does this need a Spring context?" are answerable in review
  without appeal to taste.
- **The unit layer becomes runnable without infrastructure**, which is the entire
  reason the layer exists.
- **The system suite stops growing linearly with pages.** It grows with
  integration risk, which is a much slower-growing quantity.

### Negative
- **The migration is deferred, so the boundary is aspirational at the top.** Until
  page-level system tests move, the overlap persists and CI keeps paying for it.
- **"Requires the real stack" is a judgement call at the margin.** The CSRF and job
  cases are clear; a test asserting a redirect after login is arguable.
- **Five layers is more taxonomy than a small team may want.** The acceptance layer
  in particular has to keep justifying itself against the system layer.

### Neutral
- **The pyramid is a heuristic, not a law.** Its emphasis on unit tests is
  [criticised](https://martinfowler.com/articles/practical-test-pyramid.html) for
  undervaluing failures only visible in assembled systems. No target ratio between
  layers is set here, deliberately — the admission rules decide the shape.

## Related ADRs
- [ADR-002: Coverage Gates Apply to Changed Code](ADR-002-coverage-gates-apply-to-changed-code.md) — what the gates bind to
- [ADR-003: Coverage Counters, Thresholds and the Ratchet](ADR-003-coverage-counters-thresholds-and-ratchet.md) — the numbers per layer
- [ADR-004: The Public Surface Is the Unit of Test](ADR-004-public-surface-is-the-unit-of-test.md) — what a test addresses
- [API ADR-011: Testing Strategy](../api/ADR-011-testing-strategy.md) — superseded by this set
