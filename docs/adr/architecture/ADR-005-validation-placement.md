# ADR-005: Validation Placement

## Status
Accepted

## Context

Retiring the `CommandBus`
([ADR-002](ADR-002-use-case-services-replace-the-command-bus.md)) removes the
`validator.validate(command)` call that ran before every write. That step has to
land somewhere, and looking at what it actually validated shows two different
things wearing one mechanism.

Of 14 `ConstraintValidator` classes, 12 are field-level — phone number format,
country code, file size, allowed content types, dates. They need nothing but the
value.

Two are not. `@UniqueUserCommand` and `@ValidEventSignUpCommand` **run database
queries inside bean validation** — outside the transaction that follows, with a
window between the check and the insert in which another request can take the
username. The gap is small and real, and no unique index closes it today.

[ADR-024](../api/ADR-024-scoped-signup-continuation-tokens.md) already records a
case where the declarative form broke down: `/signup/details` checks uniqueness
inside the handler, because the validator needs a subject id to exclude the
account from its own uniqueness check, and that id is only known after the token
resolves — which happens after validation has run.

## Decision

**Field constraints stay declarative on the input type. Rules that need the
database move into the use case, inside the transaction, with a database
constraint as the actual guard.**

### Field constraints

The 12 field-level validators move to the merged input type from
[ADR-002](ADR-002-use-case-services-replace-the-command-bus.md) and run via
Spring's `@Valid` on the way in. No change beyond the type they annotate.

### Uniqueness and cross-entity rules

`@UniqueUserCommand` and `@ValidEventSignUpCommand` become explicit calls inside
the use-case method, within the transaction. The check remains — it produces the
good error message naming the field — but it is no longer what makes the
guarantee. **A unique index does.** The constraint violation is caught and
translated to a 409 through the existing Problem Details handling
([ADR-008](../api/ADR-008-exception-handling-strategy.md)).

This fixes the race rather than relocating it. A pre-check inside a transaction
still races another transaction; only the index is atomic. The check exists for
the message, the index exists for the truth.

It also generalises the precedent ADR-024 set: uniqueness needs the subject, the
subject is resolved by the use case, so the use case is where the rule belongs.

### Deferred payloads

Payloads dispatched through `runIn` or `runAsync`
([ADR-004](ADR-004-deferred-execution-surface.md)) never pass through `@Valid`,
because no HTTP request carries them. They are constructed in code, so their
constructor is the validation boundary — required fields are non-nullable, and
anything else is checked by the use case that receives them.

## Implementation status

Decided, not built.

- Unique indexes are required on the columns `@UniqueUserCommand` guards before
  its pre-check can be demoted to a message-producing convenience. Without the
  index this ADR makes the guarantee weaker, not stronger, so **the migration
  must precede the validator change.**
- Existing data may violate the indexes being added. That has to be surveyed
  before the migration, not discovered by it.
- `shared/validation/date` has no cross-module consumer and is absorbed under
  [ADR-003](ADR-003-package-topology-and-placement-rules.md) rule 2.

## Consequences

### Positive
- **The race is closed rather than moved.** A unique index is the only thing
  that was ever going to make uniqueness true.
- **Validation stops querying the database from an annotation**, which is
  surprising wherever it appears and untestable without a context.
- **One rule for where a check goes:** if it needs only the value, it is
  declarative; if it needs the database, it is in the use case.

### Negative
- **Error messages get worse if the pre-check is skipped.** A bare constraint
  violation names an index, not a field. The pre-check must be kept for message
  quality even though it no longer guarantees anything.
- **Two places to look** for the rules governing one input.
- **A migration that can fail on real data**, and uniqueness violations in
  production data are discovered at the worst moment.

### Neutral
- **`@ValidEventSignUpCommand` may not need the database at all.** It is grouped
  here by mechanism; whether it is genuinely a cross-entity rule is settled when
  it moves.

## Related ADRs
- [ADR-002: Use-Case Services Replace the Command Bus](ADR-002-use-case-services-replace-the-command-bus.md) — what removed the validation step
- [API ADR-003: Validation Layer Separation](../api/ADR-003-validation-layer-separation.md) — amended by this record
- [API ADR-024: Scoped Signup Continuation Tokens](../api/ADR-024-scoped-signup-continuation-tokens.md) — the precedent this generalises
- [API ADR-008: Exception Handling Strategy](../api/ADR-008-exception-handling-strategy.md) — how the 409 surfaces
