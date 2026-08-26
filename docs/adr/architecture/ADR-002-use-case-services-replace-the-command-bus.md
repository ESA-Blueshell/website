# ADR-002: Use-Case Services Replace the Command Bus

## Status
Accepted

## Context

[ADR-002 in the API set](../api/ADR-002-command-pattern-with-command-bus.md)
adopted a `CommandBus` with one handler per command. Measured against the
codebase it produced:

- **110 `handle()` methods. 53 of them are one line.** The median body is three
  lines; 46 do work that could be called orchestration.
- Nearly every one-line handler is a `Find*` or `Delete*` — and that ADR already
  says queries must not use the bus. The rule was written and then ignored.
- The same field list is declared three times for one operation
  (`CreateBlogRequest`, `CreateBlogCommand`, `BlogResponse`) and validated twice:
  Spring's `@Valid` on the request, then the bus's validator on the command.
- `asCommand()` mappers exist only to restate the request as a command, with a
  `!!` on every field to strip the nullability the request DTO declared.

The async half of the pattern is dead. `AsyncCommandDispatcher` is `@Deprecated`
in favour of `TrackedJobDispatcher` and has **zero** production callers.
`AbstractCommandJobHandler` and `CommandJobDefinition` are referenced only by
each other and their own unit tests. No command is ever serialised. The 17
`JobDefinition` types carry their own payloads.

So the bus's remaining contribution is validation that duplicates `@Valid`, and
dispatch to a handler that half the time forwards one call.

A separate domain model — textbook hexagonal, JPA entities demoted to
persistence adapters — was considered. It would require a mirror type and a
mapper per aggregate, turning three declarations of every field list into four.
It is rejected on exactly the grounds that motivated this ADR.

## Decision

**Operations are methods on application services. The `CommandBus`, the
`CommandHandler` interface and all 110 handler classes are deleted.**

### Three shapes, chosen by what the operation does

**Queries and trivial deletes call the service directly.** No command, no
handler. This is what the superseded ADR already required for queries.

```kotlin
@GetMapping("/blogs/{id}")
fun blog(@PathVariable id: Long) = blogService.findById(id).asResponse()
```

**Orchestrating operations become a method on an application service**, taking a
validated input. The 46 handlers with real bodies become roughly fifteen
services' worth of methods.

```kotlin
@Service
class BlogUseCases(private val blogService: BlogService) {
    @Transactional
    fun create(input: CreateBlog): Blog = blogService.create(
        Blog(input.title, sanitizeBlogHtml(input.html), input.publishedAt),
    )
}
```

`@Transactional` works here because a service is a Spring bean. It would not
have worked on a method of a command, which is constructed with `new` and never
proxied — one of the reasons behaviour is not moved onto commands.

**Deferred operations keep a payload type**, because durable work must be
serialisable. Those are plain records with no behaviour, and
[ADR-004](ADR-004-deferred-execution-surface.md) governs them.

### Request and Command collapse

One validated input type per operation, named for the operation. The request
DTO's nullable-plus-`@NotNull` shape is what survives, since it is the one that
has to tolerate absent JSON fields; the `asCommand()` mappers and the second
validation pass go with the command.

### Why not put `run()` on the command

The proposal that opened this work was for commands to execute themselves via
`run`, `runAsync` and `runIn`. `run()` is dropped: with the bus gone,
synchronous execution *is* `blogUseCases.create(input)`, and a `run()` wrapper
would reintroduce the indirection this ADR exists to remove. It also has no good
answer for dependencies — a command is constructed by a mapper, so its
constructor is spoken for by data, leaving only a context parameter, a service
locator, or Spring-managed commands that are no longer data classes.

`runAsync` and `runIn` survive, on a dispatcher rather than on the payload. They
are about scheduling, not about where handler code lives, and they are the part
of the original proposal that adds capability rather than moving it.

## Implementation status

Decided, none of it built. Sequenced in
[ADR-006](ADR-006-migration-sequencing.md), phase 1.

- Deleting the bus touches the 19 controllers that inject it.
- `AsyncCommandDispatcher`, `AbstractCommandJobHandler`, `CommandJobDefinition`
  and their two unit tests are removed outright — verified unused.
- `shared/command` (3 files, 13 consumer modules) disappears with the bus.
- `BaseModelService` survives phase 1 unchanged; whether generic CRUD services
  remain once use-case services exist is deliberately left open.

## Consequences

### Positive
- **Roughly 160 files are deleted rather than relocated** — 110 handlers, the
  53 pass-through commands, the bus and the dead async bridge.
- **One declaration of each field list instead of two on the write path**, and
  one validation pass instead of two.
- **Transactions land where they are proxied.** Six handlers carry
  `@Transactional` today; on a service the annotation does what it appears to do.
- **The call site says what happens.** `blogUseCases.create(input)` is
  navigable; `commandBus.dispatch(command)` requires a registry lookup to follow.

### Negative
- **The uniform seam is gone.** A single `dispatch` was one place to add
  logging, metrics or auditing. None of those are implemented there today, but
  the opportunity was real and is being given up.
- **Three shapes instead of one.** "Direct call, use-case method, or deferred
  payload" is a judgement per operation, where the bus asked nothing.
- **Discoverability drops.** `CommandHandler` implementations were a complete
  list of write operations; methods across fifteen services are not.
- **A large, broad diff.** Nineteen controllers change in phase 1.

### Neutral
- **Command replay and audit-trail arguments were never realised.** The
  superseded ADR listed both as benefits; no code stores or replays commands,
  and the mechanism that would have (`AbstractCommandJobHandler`) has no callers.

## Related ADRs
- [ADR-001: Application Modules Replace Layers](ADR-001-application-modules-replace-layers.md) — the boundaries these services sit inside
- [ADR-004: Deferred Execution Surface](ADR-004-deferred-execution-surface.md) — what happened to runAsync and runIn
- [ADR-005: Validation Placement](ADR-005-validation-placement.md) — where the bus's validate step went
- [API ADR-002: Command Pattern with CommandBus](../api/ADR-002-command-pattern-with-command-bus.md) — superseded by this record
