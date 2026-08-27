# ADR-004: Deferred Execution Surface

## Status
Accepted

## Context

Two mechanisms exist for work that should not block a request, and one of them
is dead.

`AsyncCommandDispatcher` returns a `CompletableFuture`, propagates the security
context and defers submission to `afterCommit`. Its own annotation says
*"Prefer `TrackedJobDispatcher` for reliable async work (persistence + retry)"*,
and it has **zero** production callers.

`TrackedJobDispatcher` writes a `JobExecution` row and is what the 17
`JobDefinition` types use. It is durable, retried, deduplicated by key, and
visible in the job manager UI. It cannot defer work to a chosen time.

The proposal that opened this work was for `run`, `run_async` and `run_in` on
commands. `run` is dropped in
[ADR-002](ADR-002-use-case-services-replace-the-command-bus.md) — synchronous
execution is a service call. The other two describe capability the codebase
does not have, and they contain a contradiction worth naming: **a promise and
durability are mutually exclusive.** A `CompletableFuture` dies with the JVM,
so a future-returning API cannot survive a deploy. And a caller that awaits the
future has not made the work asynchronous; it has moved it to another thread and
blocked on it.

## Decision

**Two verbs on an injected dispatcher. Payloads stay plain records.**

```kotlin
jobs.runAsync(SendReminder(userId))                 // durable, returns a handle
jobs.runIn(Duration.ofMinutes(5), SendReminder(userId))
```

Verbs live on the dispatcher, not on the payload. A payload with behaviour would
need its collaborators from somewhere — a context parameter, a service locator,
or Spring-managed construction that stops it being a serialisable record. The
dispatcher makes the dependency visible at the call site and keeps the payload a
data class that Jackson can round-trip.

Naming is `runAsync` and `runIn`; Kotlin is camelCase.

### `runAsync` returns a handle, not a value

The return is the `JobExecution` identifier. The caller gets something it can
poll, log, or surface in the job manager, and the work survives a restart. A
caller that needs the result must await the outcome explicitly, which makes the
blocking visible rather than hiding it behind a future that looks free.

This is the same trade `TrackedJobDispatcher` already makes, and the reason
`AsyncCommandDispatcher` was deprecated. Reinstating a future-returning API
would re-adopt the mechanism this codebase already abandoned.

Where the work is a consequence of something that happened rather than a task to
schedule, the idiomatic form under
[ADR-001](ADR-001-application-modules-replace-layers.md) is publishing a domain
event and consuming it with `@ApplicationModuleListener` — durable through the
Event Publication Registry, with no dispatcher call at all. `runAsync` is for
work the caller is deliberately deferring; an event is for work another module
decides to do.

### `runIn` gets its own column

`JobExecution` already carries `nextAttemptAt`, and `StaleJobRecovery` already
polls it every 30 seconds via `findDueScheduledRetries`. A future-dated row is
therefore picked up when due with no new machinery.

It is nonetheless a **new nullable `scheduledFor` column**, not a reuse of
`nextAttemptAt`. That field means retry backoff: `StaleJobRecovery` relies on
`next_attempt_at = NULL` to tell a crash-orphaned row from a scheduled retry,
retry counting is anchored to it, and an operator looking at the job manager
needs to distinguish "deferred until Tuesday" from "backing off after a
failure". Due time becomes `max(scheduledFor, nextAttemptAt)`.

### The contract is "not before"

Poll interval is 30 seconds (`app.jobs.retry-check-interval-ms`), so
`runIn(5.seconds)` may not fire for 30. `runIn` guarantees the work does not run
*earlier* than requested and promises no upper bound. Sub-minute precision is
not something a database-backed queue should claim, and shortening the interval
would multiply polling queries for a case that may never arise.

## Implementation status

Decided. Split by whether anything calls it.

Landed, because both were free and neither was speculative:

- `TrackedJobDispatcher.enqueue` is now `runAsync`, as are `JobQueue.enqueue`,
  its `JobDispatcher` implementations and every caller. `enqueueFromActor`
  became `runAsyncFromActor` and `EnqueueableJob` became `AsyncJob` with
  `runAsyncOn`. The operator-facing `JobManagementController.enqueue` /
  `JobCatalogService.enqueue` trigger endpoint is a different surface and keeps
  its name.
- `StaleJobRecovery` logged `nextAttemptAt` under the label `scheduledFor={}`.
  The placeholder now names the field it prints.

Deferred until a caller exists, on this record's own reasoning that it would
otherwise be "a schema change for a feature with no current caller":

- the `scheduledFor` column and its Flyway migration;
- `findDueScheduledRetries` selecting on `max(scheduledFor, nextAttemptAt)`;
- `runIn` itself;
- the job manager column, without which the field's purpose is invisible to the
  operators it exists for.

Contribution and event reminders are the likeliest first caller. When one
arrives, all four land together — a column with no reader and no writer is worse
than no column.

Already done: `AsyncCommandDispatcher`, `AbstractCommandJobHandler` and
`CommandJobDefinition` were deleted in phase 1 of
[ADR-006](ADR-006-migration-sequencing.md).

## Consequences

### Positive
- **Deferred work is durable by default.** Nothing survives a deploy today
  unless it went through the job table; now that is the only path.
- **`runIn` is nearly free** — the poller and the retry loop already exist, so
  the change is a column and a parameter.
- **The API stops lying about cost.** A handle cannot be mistaken for a cheap
  value the way a future can.

### Negative
- **No in-request parallelism.** Genuinely volatile fan-out — three independent
  reads in one request — has no verb, and would need a future-returning path
  that this ADR declines to provide.
- **A handle is less convenient than a value.** Callers wanting the result write
  more code, which is the intent but is still friction.
- **Two ways to defer work.** `runAsync` and publishing an event overlap, and
  "task versus consequence" is a judgement call.
- **A schema change for a feature with no current caller.** `scheduledFor` is
  built for a capability that has been asked for but not yet used.

### Neutral
- **30-second granularity bounds every use.** Adequate for reminders and
  retries; not for anything user-visible in seconds.

## Related ADRs
- [ADR-002: Use-Case Services Replace the Command Bus](ADR-002-use-case-services-replace-the-command-bus.md) — why `run` does not exist
- [ADR-001: Application Modules Replace Layers](ADR-001-application-modules-replace-layers.md) — the event path this sits beside
- [API ADR-023: Job Consolidation and Reliable Execution](../api/ADR-023-job-consolidation-and-reliable-execution.md) — amended by this record
