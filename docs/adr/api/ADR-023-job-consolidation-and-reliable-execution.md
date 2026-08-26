# ADR-023: Job Consolidation and Reliable Execution

## Status

Accepted


> **Amended** by [architecture ADR-004](../architecture/ADR-004-deferred-execution-surface.md):
> a `scheduledFor` column and the `runAsync` / `runIn` surface are added;
> `AsyncCommandDispatcher` is removed.

## Context

The job system used RabbitMQ for asynchronous job dispatch. This introduced several problems:

1. **Dual-write race**: `JobDispatcher` persisted a DB row then sent to RabbitMQ. If Rabbit failed or the consumer read before the TX committed, jobs were lost.
2. **Thread-blocking retries**: `Thread.sleep()` in `JobConsumer` blocked the RabbitMQ consumer thread.
3. **No deduplication**: Rapid user updates (e.g., editing an event repeatedly) created multiple identical jobs.
4. **No exception classification**: All exceptions were retried identically, including non-retryable ones like `IllegalArgumentException`.
5. **Redundant job types**: Calendar had 3 jobs (add/sync/remove) when `CalendarAdapter.syncEvent()` already handles all cases; contact had 5 jobs when 3 suffice.
6. **Infrastructure overhead**: RabbitMQ for ~9 job types on a student association website.

## Decision

### Replace RabbitMQ with `@Async` + `RetryTemplate`

Jobs are dispatched by writing a DB row (`JobExecution`) then calling `JobExecutor.executeAsync()`. The executor runs on a Spring `@Async` thread pool (`taskExecutor`: core=2, max=10, queue=500). `RetryTemplate` handles exponential backoff retries on the async thread, not the request thread.

### Exception Classification

- **Non-retryable exceptions** (`NonRetryableJobException`, `IllegalArgumentException`, `NullPointerException`, `ClassCastException`) immediately mark the job as `DEAD`.
- **Retryable exceptions** are retried with exponential backoff up to `maxRetries`. If exhausted, the job is marked `FAILED`.
- A new `DEAD` status distinguishes permanently failed jobs from those that exhausted retries.

### Job Deduplication

`JobDefinition<T>` gains a `dedupKey(payload): String?` method. When non-null, `createQueued()` checks for active jobs (QUEUED/RUNNING) with the same `(jobType, dedupKey)`. Duplicates are suppressed (return null).

### Job Type Consolidation

| Before (9 types) | After (7 types) | Change |
|---|---|---|
| `calendar.add-event` | _(removed)_ | Absorbed into `calendar.sync-event` |
| `calendar.sync-event` | `calendar.sync-event` | Enhanced: handles soft-deleted events |
| `calendar.remove-event` | _(removed)_ | Absorbed into `calendar.sync-event` |
| `contact.sync` | `contact.sync` | Added dedup key |
| `contact.delete` | `contact.delete` | Added dedup key |
| `contact.add-to-list` | _(removed)_ | Absorbed into `contact.sync-list-membership` |
| `contact.remove-from-list` | _(removed)_ | Absorbed into `contact.sync-list-membership` |
| `contact.create-period-list` | _(removed)_ | Absorbed into `contact.sync-list-membership` |
| `email.*` (3 types) | `email.*` (3 types) | No change, no dedup |

### Dedup Key Strategy

| Job Type | Dedup Key | Rationale |
|---|---|---|
| `calendar.sync-event` | `event={eventId}` | Rapid event edits collapse |
| `contact.sync` | `user={userId}` | Rapid user updates collapse |
| `contact.sync-list-membership` | `user={userId}:period={periodId}` | Contribution changes collapse |
| `contact.delete` | `user={userId}` | Safety guard |
| `email.*` | `null` | Each email is unique |

### Stale Job Recovery

A `@Scheduled` task (`StaleJobRecovery`) periodically checks for:
- RUNNING jobs where `startedAt` is older than `staleThresholdMinutes` → reset to QUEUED and re-execute
- QUEUED jobs where `queuedAt` is older than `staleThresholdMinutes` → re-execute

This handles jobs orphaned by app crashes.

## Consequences

### Positive
- **Simpler infrastructure**: No RabbitMQ dependency. One fewer service to deploy, monitor, and maintain.
- **No dual-write race**: Job row is written and dispatched in the same request flow.
- **Non-blocking retries**: Backoff happens on async threads, not consumer threads.
- **Deduplication**: Rapid updates don't create redundant work.
- **Fewer job types**: 7 instead of 9, with clearer responsibilities.
- **Better error classification**: Non-retryable errors are immediately marked DEAD.

### Negative
- **No message persistence**: If the app crashes between DB write and async dispatch, the job sits as QUEUED until `StaleJobRecovery` picks it up (within `staleThresholdMinutes`).
- **Bounded thread pool**: At most 10 concurrent jobs. Sufficient for current scale but needs monitoring.

### Neutral
- **Migration**: Existing QUEUED/RUNNING jobs from before the migration will be picked up by `StaleJobRecovery`.
- **Old job types** (`calendar.add-event`, etc.) in the database remain readable but their handlers no longer exist.

## Related ADRs
- [ADR-006: Event-Driven Architecture](ADR-006-event-driven-architecture.md)
- [ADR-019: Anti-Corruption Layers](ADR-019-anti-corruption-layers-for-external-integration.md)
- [ADR-022: Platform, Infrastructure, and Shared Organization](ADR-022-platform-infrastructure-shared-organization.md)
