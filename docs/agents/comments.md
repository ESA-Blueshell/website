# Comments

What a comment in this repo is for, and what it is not.

A comment earns its place by saying something the code cannot: a constraint, a trap, or a
reason. Everything else is noise a reader has to wade through to reach the code.

## Budget

- A class or module doc is a summary line and **at most one paragraph** after it.
- A method doc is a summary and **at most one paragraph**. Most need only the summary.
- A block that runs longer must be carrying several distinct invariants. `ShippedBoardArt`,
  `Game` and `BrevoListAdapter` are the standing exceptions; adding to that list is a decision,
  not a default.

## What survives

- A constraint somebody would otherwise break. *"A filled slot is never overwritten."*
- A trap with a cost. *"Reading a name off a hundred lazy members costs two queries each."*
- A reason that is invisible in the code. *"Not a regex: the pattern backtracks quadratically
  once a 180KB base64 token is spliced in."*
- A rule mirrored on the other side of the wire, naming its twin. *"Change one, change the
  other."* These are the cheapest defence against the two halves drifting.

## What goes

- **History.** Never *"this was X, now it's Y"*, *"used to be"*, *"the previous heuristic"*, or
  a record of rules that were deleted. Git holds that. Write the present tense.
- **Restatement.** If the line below says it, the comment says nothing.
- **Section labels and ASCII dividers.** `// Computed properties`,
  `// ── Handlers ──────────`. The code beneath is the heading.
- **`@param` / `@return` blocks that restate a Kotlin signature.** The types already say it.
  Keep only what they cannot, such as the shape of a string.

Note that *"no longer"* is usually **not** history: *"an answer it is no longer waiting on"*
describes runtime state. Read the sentence before cutting it.

## Placement

A statement about the type belongs on the class, not on one of its methods. An enum documents
each constant on the constant, not as a bulleted list in the class doc. Where a method is
documented and its class is not, give the class a one-line opener.

Watch for a doc stranded above the wrong member: inserting a method directly beneath an
existing doc silently re-points it. There were three of these in the api when this was written.

## When a comment is a symptom

Sometimes a comment exists only because the code is opaque — a bare boolean argument, a magic
number, a chain that needs a sentence to parse. The fix is a rename or an extraction, not a
better comment. Leave the comment, and fix the code in its own change.

## Untouchable

Comments the toolchain reads: `eslint-disable`, `@ts-expect-error`, `noinspection`, detekt
suppressions. And `TODO` / `FIXME` that name work still open.
