# ADR-026: A Refused Write Carries a Code, Not a Sentence

## Status
Accepted

## Context

ADR-008 settled that the api reports errors as RFC 7807 Problem Details from domain exception
hierarchies handled by `@RestControllerAdvice`. It did not settle what goes in `detail`, and the
answer drifted: `detail` became the sentence the user reads.

In the esports module that produced two concrete problems.

**The api composed prose from data.** Twelve refusals carried finished English, and two of them
pluralised and joined clauses:

```kotlin
throw ResponseStatusException(
    HttpStatus.CONFLICT,
    "${page.name} holds $held team${if (held == 1L) "" else "s"} and " +
        "$players roster place${if (players == 1L) "" else "s"}. " +
        "Everything it played stays readable, and it leaves the pages that show what " +
        "the association plays by not being entered in a season.",
)
```

Plural forms and joining words are presentation. Putting them in Kotlin means a copy change is a
backend deploy, and it means the branch that produces `1 teams` has to be proven through a Spring
context and a database.

**And the same sentence existed twice.** `GameDialog.vue` asks what a game holds *before* the
removal, reading counts from `GET /games/{game}/contents`, and composed that paragraph itself in
TypeScript. The api composed it again for the refusal *after* the removal. Neither knew about the
other, and editing one silently left the other behind.

The api already had two error shapes to choose between:

- `BulkSelectionProblemDetailsAdvice` — an `errors` array of
  `{objectName, field, message, code, values, refs}`, mirrored client-side by
  `utils/bulkRejection.ts`. Built for a violation per field over a selection of rows, and it
  keeps a composed `message`
- `ExternalIdConflictAdvice` — a flat `ProblemDetail` with named properties via `setProperty`

## Decision

**A refused esports write answers a `code` and the facts about the refusal as named properties.
`detail` is fixed per code. The frontend composes the sentence.**

`EsportsRefusal` is a sealed class carrying the status, the code, a fixed summary and a map of
facts. `EsportsRefusalAdvice` has one handler for the whole hierarchy: it puts the code and each
fact on a flat `ProblemDetail`. `esports/refusals.ts` maps a code to a sentence, and that map is
the only place esports refusal copy lives.

```
409
{
  "title": "Conflict",
  "detail": "That game cannot be removed.",
  "instance": "/esports/games/VALORANT",
  "code": "GameHoldsHistory",
  "gameName": "Valorant",
  "teams": 3,
  "players": 14
}
```

### The flat shape, not the `errors` array

An esports refusal is one fact about one game, one season or one team. Forced through
`values`/`refs` the counts arrive as `values[0]` and `values[1]` with nothing naming them, and
which is which becomes a convention two codebases have to remember. The bulk shape stays right
for what it was built for — a violation per field, over many rows.

### `detail` is not a display string

It is the same sentence for every occurrence of a code. It interpolates nothing and pluralises
nothing, so it carries no presentation decision, and it keeps a log line and a non-browser caller
readable. It is also the frontend's fallback: a code the frontend has not been taught falls
through to `detail` rather than to nothing.

### Codes are PascalCase

Matching `BulkRejectionCode`'s values (`UnknownUserIds`, `DeletedUserIds`) rather than the api's
enum convention, because they are wire values a TypeScript map mirrors.

### Rows that are not there keep their sentences

`Season with id 7 not found` and its two siblings stay as `ResponseStatusException`. No dialog can
provoke one — reaching it takes a hand-built request naming a row that does not exist — so there
is no copy for a reader to meet, and a code would buy a frontend branch nothing can reach.

## Consequences

**Good.** Copy changes stop being backend deploys. The plural branches are unit-testable with no
Spring and no database. The question asked before an act and the answer given after it read one
function, so they cannot drift. The frontend keeps display decisions, per the display-layer rule
and frontend ADR-002.

**The cost is a mirrored map.** `EsportsRefusal.kt` declares the codes and
`esports/refusals.ts` gives each a sentence, and a code added to one needs the other. Both name
their twin in a comment. The alternative — shipping the sentence — is what this replaces, and a
missing sentence degrades to `detail` rather than to a blank.

**It is scoped to esports.** The other advices keep their shapes. A module adopting this pattern
should follow it rather than invent a third; a module with no user-facing refusals needs nothing.

## Implementation status

`EsportsRefusal`, `EsportsRefusalAdvice` and `esports/refusals.ts` are in place, with
`EsportsRefusalIT` asserting each code and its properties over http and `refusals.test.ts`
covering the sentences. Nothing enforces that a new code gets a sentence: that is the mirrored
comment's job, and the fallback to `detail` is what makes the omission survivable.

## Related

- ADR-008 — Problem Details and the advice convention this narrows
- ADR-012 — the OpenAPI workflow; refusal properties are `ProblemDetail` extensions and are not in
  the generated schema
- Frontend ADR-002 — the api client boundary, where a refusal becomes a sentence
