# Context

The project's glossary: the words this codebase uses for the things it models, and
which of them differ between what a reader sees and what the code says.

A term belongs here once its meaning has been argued over and settled. The glossary
starts with esports, which is where the language was first pinned down; other areas
join it as their terms are settled rather than all at once.

This file is a glossary. It holds no decisions, no plans and no implementation
detail — those live in `docs/adr/` and in the issues.

## Esports

### Game

A competitive title the association fields teams in. A game is a record the board
edits, not a fixed set decided when the software was built.

A game carries three separate identifiers, and conflating them is the mistake this
section exists to prevent:

- **Name** — what the game is called. Editable. Shown wherever the game is named
- **Address** — what the game's page answers to, the last segment of its url.
  Editable, and changing it changes where the page is reached
- **Code** — what everything else files the game under. Taken from the name when
  the game is added, and never changes afterwards

**Code**, not ID. A game also has a numeric database key, so "ID" would name two
different values. On screen and in conversation the immutable identifier is the
code.

### Season

A named stretch of time with a start and an end, which the esports history is
organised into. Seasons do not overlap.

### Fielding

A team playing one game in one season. The unit the history is actually made of: a
team fielded in five seasons is five fieldings, and dropping one leaves the other
four alone.

### Fielded

A game is fielded in a season once a team plays it there. Distinct from a game
merely being *entered* in a season, which the board may do before any team exists.
The pages answer to fielded, not to entered.

### Currently played

The games the association plays now: those fielded in the most recent season. Where
that season has nothing fielded in it yet, the season before it answers instead, so
a season being built one team at a time is never shown half-finished.

Derived, never stored. A game is not marked as played or retired — it is played
because a team plays it.

### Team

A named group the association fields. The pool of teams is shared across games: one
team may play more than one game, and more than one season.

### Line-up

The people a team fields in one game in one season — that is, the people on one
fielding. A team does not have a line-up; a fielding does.

**"Line-up" is the word on screen.** The code says **roster** for the same
collection. The two are the same thing and the divergence is deliberate: "line-up"
is what a board member calls it, "roster" is what the code has always called it,
and renaming either would cost more than the inconsistency does.

### Roster entry

One person's place in one line-up, with the role they played and how they are
credited.

**A roster entry is not a person.** The same member holds several across the seasons
they play, so the two cannot be equated. The code keeps the precise term.

**On screen a roster entry reads as a "person", counted as "people".** A board
member asked whether to remove a game is told it holds three teams and fourteen
people, because that is what they need to know. "Roster place" is jargon and appears
nowhere a reader can see.

**On the wire the count of them is `players`.** A third word, kept because it is the
one the api already answers with: renaming it would put a refusal out of step with the
endpoint the dialog reads before it asks.

### Banner and Icon

The two pictures a thing carries. A **banner** is the large picture drawn behind a
slice; an **icon** is the small one that identifies the thing beside its title.

Four of them exist — a game banner, a game icon, a team banner, a team icon — and a
person on a line-up carries a **roster icon**. These five names are used
identically in the code, in the issues and on screen. Neither picture is a
"background image", a "mark", a "logo" or a "poster".

## Contributions

### Contribution period

The year a contribution is owed for, carrying the three fees and the **half-year
cutoff** that selects between them. Every period-relative action in the manager is
anchored to one, and there is nothing to act on without one.

Not a "season" — that word belongs to esports and means something else.

### Fee type

Which of the period's three fees applies to a member: the **full-year fee**, the
**half-year fee** or the **alumni fee**. It is the thing chosen; the amount follows
from it and the period. An amount is never typed and never stored as a choice.

`BulkFeeType` in the code, because it arrived with the bulk vocabulary and the
generated client publishes it under that name.

### Half-year cutoff

The date on a contribution period that decides between the full-year and half-year
fee. A regular membership starting **after** it pays the half year; one starting on it
or before pays the full year. Policy for the year, so it lives on the period rather
than being retyped on each send.

### Fee cycle

The one operation that asks every member of a period who has not paid for what they
owe. It is opened for a period, not for a selection: who is asked follows from the
period, which is what makes "has everybody been asked exactly once" answerable.

Not a "bulk reminder" and not two sends. One cycle, one confirmation.

### The partition

The split of a fee cycle into the two groups that receive different statements,
decided by the `incasso` flag on the membership each decision is judged against.
Called a **group** on the wire and in the code — `FeeCycleGroup`, whose values are
`DIRECT_DEBIT` and `TRANSFER` — and "the partition" only in prose about the pair.

Because the flag decides the side, not having it is not a warning. There is no wrong
side to be selected for.

### Payment request and pre-notification

The two statements a fee cycle sends. A **payment request** asks a member paying by
transfer to pay what they owe by a due date. A **pre-notification** tells a member
paying by direct debit what will be taken and on what date, and asks for nothing.

Different statements, so different records: the treasurer's question is which one a
member received. Neither quotes an amount without the reason that amount applies.

An **incasso** is the direct debit itself; the record of a pre-notification is an
`IncassoNotification`, which is the Dutch word the association uses for the mandate.
