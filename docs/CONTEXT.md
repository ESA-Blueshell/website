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
