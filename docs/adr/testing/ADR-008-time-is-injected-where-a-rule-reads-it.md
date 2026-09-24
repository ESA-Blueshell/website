# ADR-008: Time Is Injected Where a Rule Reads It

## Status
Accepted

## Context

The api reads the time directly: `Instant.now()` and `System.currentTimeMillis()` in
`JwtTokenUtil`, `RecoveryToken.isExpired`, `RoleGrantUseCases` and elsewhere. Only three
Discord classes take a `Clock`, and their tests swap in `Clock.fixed`.

Two-factor and the sign-in record (api ADR-030, ADR-031) are made of rules about time:
a code's thirty-second step and the one step of skew either side, the refusal of a step
already used, a five-minute challenge, a five-minute rotation with sixty seconds of grace,
thirty days absolute and fourteen idle, a ten-minute step-up, links that last a day or
three.

Tests of such rules against the real clock go one of two ways. They sleep until a fresh
TOTP window begins, which a studied reference implementation does, and which makes the
suite slow and still flaky near a boundary. Or they only test the middle of each window,
leaving the edges — the rules — untested.

## Decision

**Code that decides anything by the time takes an injected `Clock` or `InstantSource`.
Its tests fix the clock and move it; they never sleep and never read the wall clock.**

- The api exposes one `Clock` bean. The two-factor and sign-in code reads time only
  through it, and so does code those features change.
- A unit test constructs the class under test with `Clock.fixed` or a mutable test clock,
  and asserts both sides of every boundary: the last accepted instant and the first
  refused one.
- TOTP codes in every layer are computed from the secret and the test clock's instant,
  using RFC 6238's published vectors as the unit suite's anchor.
- The integration and system suites run against a clock the test profile lets them set,
  so "the challenge has expired" and "thirty days have passed" are a move of the clock
  rather than a wait.

Code outside these features keeps reading the time directly until something changes it.
This is a rule for new and touched code, not a sweep.

## Consequences

A time rule gets its boundary tested as cheaply as its middle, which is where the
security properties sit.

A settable clock in the test profile is a lever that must never reach production. It is
bound only under the `test` profile, like `/test-support/**`, and a test asserts it is
absent otherwise.

Valkey expiry runs on Valkey's clock, not the api's. A rule that leans on a key's TTL
cannot be moved by the test clock, so those rules store their own instant and compare it,
leaving the TTL only as cleanup.

The system tests are the exception. Many tests share one running api there, and moving its
clock would move every other test's sign-in with it, so a system test that needs a second
code waits for the next thirty-second step instead.

## Related

- [ADR-001: The Test Pyramid and Layer Placement](ADR-001-test-pyramid-and-layer-placement.md)
- [api ADR-030](../api/ADR-030-a-sign-in-is-a-server-side-record.md) and [api ADR-031](../api/ADR-031-two-factor-authentication.md) — the rules this is for
