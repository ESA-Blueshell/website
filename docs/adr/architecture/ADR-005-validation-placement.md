# ADR-005: Validation Placement

## Status
Accepted

> **Corrected.** This record claimed no unique index backed the columns
> `@UniqueUserCommand` guards, and gated the validator change on a migration to
> add them. The constraints exist, and have since
> `V28__constraints-cleanup-hibernate-realignment.sql`. The original claims stay
> below with the correction beside each; *Implementation status* carries the
> migration trail and what it unblocks.

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

> **Correction.** The last clause held for `@ValidEventSignUpCommand` — no
> unique constraint covers an event sign-up — but not for `@UniqueUserCommand`.
> Every column that validator reads is already indexed uniquely; see
> *Implementation status*.

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

Partly built — placement is settled, the demotion is not. The migration this
record gated itself on is not owed: it landed years before the record was
written.

- **Placement is done.** `@UniqueUserCommand` and its validator sit in `user`,
  `@ValidEventSignUpCommand` and its validator sit in `event`, each beside the
  rule it enforces under
  [ADR-003](ADR-003-package-topology-and-placement-rules.md) rule 3. Both are
  applied by an explicit `validator.validate(…)` call in the owning use case
  rather than by the retired dispatcher.
- **`shared/validation` is empty.** Its `date` package was absorbed under
  [ADR-003](ADR-003-package-topology-and-placement-rules.md) rule 2 and its
  group markers moved to `user`, the only module that used them. No package
  remains under it, so rule 2 holds for validation by construction.
- Unique indexes are required on the columns `@UniqueUserCommand` guards before
  its pre-check can be demoted to a message-producing convenience. Without the
  index this ADR makes the guarantee weaker, not stronger, so **the migration
  must precede the validator change.**

  > **Correction.** The indexes were already there. `@UniqueUserCommand` reads
  > four columns — `username`, `email`, `discord`, `phone_number` — and each
  > carries a unique constraint on `(column, deleted_at)`:
  > `uk_users_username_deleted_at`, `uk_users_email_deleted_at`,
  > `uk_users_discord_deleted_at`, `uk_users_phone_number_deleted_at`.
  > `V23__dates-int-to-bigint-timezone-deletedat-and-user-indexes.sql` created
  > all four as standalone unique indexes,
  > `V27__fk-renames-and-index-cleanup.sql` dropped them by name, and
  > `V28__constraints-cleanup-hibernate-realignment.sql` re-added them as named
  > table constraints on the same column pairs. No migration after V28 touches
  > them. **The gate is open: the validator demotion is unblocked and can be
  > done on its own.**

- Existing data may violate the indexes being added. That has to be surveyed
  before the migration, not discovered by it.

  > **Correction.** Moot. No index is being added, and these have been rejecting
  > duplicates since V28 — data that would violate them was reconciled by that
  > migration and has been unwritable since. There is nothing left to survey.

- **The demotion is what remains.** Neither pre-check has been demoted; both
  still decide the outcome rather than producing the message for a constraint
  that does. Nothing is owed before the `@UniqueUserCommand` half — the
  constraints are in place, so it can be done on its own. The
  `@ValidEventSignUpCommand` half has no constraint to fall back on yet, so it
  waits on one being written.

### Why the constraints are composite

Each is keyed on `(column, deleted_at)` rather than the column alone because
`User` is soft-deleted: a deleted row stays in the table with `deleted_at` set
to the deletion timestamp, and a live row carries the sentinel
`9999-12-31 23:59:59`. A plain unique index on `discord` would put a handle out
of reach forever once the account holding it was deleted. Keying on the pair
admits one live row plus any number of deleted ones, which is the guarantee this
record asks for. The composite shape is the constraint working, not a weakened
form of it, and narrowing it to the bare column would break account deletion.

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

  > **Correction.** Not a cost this record carries for the user columns. That
  > migration is V28, it ran, and the risk was spent then. It stands for
  > `@ValidEventSignUpCommand`, whose constraint has yet to be written.

### Neutral
- **`@ValidEventSignUpCommand` does need the database.** It was grouped here by
  mechanism, with the question left open until it moved. It loads the event to
  read the sign-up deadline, the sign-up limit and the form's questions, so it
  is a cross-entity rule and belongs in `event` on merit, not by association.

## Related ADRs
- [ADR-002: Use-Case Services Replace the Command Bus](ADR-002-use-case-services-replace-the-command-bus.md) — what removed the validation step
- [API ADR-003: Validation Layer Separation](../api/ADR-003-validation-layer-separation.md) — amended by this record
- [API ADR-024: Scoped Signup Continuation Tokens](../api/ADR-024-scoped-signup-continuation-tokens.md) — the precedent this generalises
- [API ADR-008: Exception Handling Strategy](../api/ADR-008-exception-handling-strategy.md) — how the 409 surfaces
