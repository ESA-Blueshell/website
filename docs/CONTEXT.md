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

### Line-up draft

What the editor holds while a line-up is being worked on: the team's name and its
two pictures, the people on it and their parts, and which of them are on their way
off. Nothing in it is written until Save.

The pictures are the exception that proves it. Choosing one stores it straight away,
because the picker has to draw it and cannot draw bytes nobody has stored — but the
draft still holds only the path, and the team carries neither picture until the draft
is published.

### Publish

Writing a line-up draft as one answer rather than as a series of half-finished ones.
The writes run in a fixed order, because a team has to exist before anything can be
written against it, and a rename has to land before rows are written against the
renamed team.

Publishing stops at the first refusal and says what was written before it. It is not
a transaction: the api cannot undo the earlier stages, so a half-published line-up is
reported rather than rolled back. **Publishing a line-up is distinct from saving one
roster entry**, which is one row and answers for itself.

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

### Payment emails

The one operation that asks a selection of members for what they owe for a period.
One send, whichever statements it turns out to need — not two actions the treasurer
picks between, and not a bulk reminder.

"Payment emails" in the interface; `ContributionEmail` in the code, where the noun
sits beside contribution period and contribution reminder.

### Contribution reminder and incasso notification

The two emails a send puts out. A **contribution reminder** asks a member paying by
transfer to pay what they owe by a due date. An **incasso notification** tells a member
paying by direct debit what will be taken and on what date, and asks for nothing.

A contribution reminder is not always a treasurer's doing. A membership starting through
the signup form asks the new member for their contribution in the same breath, and that
ask is a contribution reminder too — same row, same **last payment email**. The member
reads a welcome rather than a chase, but the association has asked them once either way.

Which one a member gets is their `incasso` flag's choice, stated per row as the
member's **kind** — `ContributionEmailKind`, whose values are `REMINDER` and
`INCASSO_NOTIFICATION`. A default rather than a rule: the treasurer may move a member
onto the other one, and a **switched** row says so.

Different statements, so different records: the treasurer's question is which one a
member received. Neither quotes an amount without the reason that amount applies.

### Send to

The checkbox on the first step of the payment-email wizard, and the selection itself: a
member is written to when their box is ticked, and only then. A member the api warns about
starts unticked, and one it cannot write to at all has no box. It replaced **forcibly
include**, which was the same gesture but appeared only on the rows the api had warned
about, so one control now does what two were doing.

`forciblyIncludedUserIds` on the wire still carries the warned rows ticked back in, because
the send re-decides and would otherwise skip them.

### Refusal

A bulk request the api declines whole, naming the request **field** at fault and a stable
**code** rather than a sentence to display. Nothing is written. A **409** means the client's
table is stale; a **400** that a field of the request is wrong. Both arrive in the same
`errors[]`, so one client-side handler reads either and the payment-email wizard can put the
treasurer back on the step that owns the field.

### Ask

One asking of one member to pay for one period — a contribution reminder or an incasso notification.
A row each, not one per member and period: the treasurer chases, so a member can be asked
in September, again in February and again the week after, and each is a thing that
happened. **Last payment email** is the most recent of them whichever kind it was, because
the question it answers is whether this member has been written to at all — a row moved onto
the other kind reading as an untouched member is the mistake that matters, and it costs a
member a second ask they did not need.

Pooling the two does lose something: that a member moved onto direct debit has been reminded
and never pre-notified. That fact is worth a warning rather than a column, so the
confirmation keeps it — it names only the members about to get the very same email again,
and a **duplicate** is what it fires on.

An **incasso** is the direct debit itself; the record of one of these is an
`IncassoNotification`, which is the Dutch word the association uses for the mandate.

### Duplicate

A second ask of the same kind, to the same member, for the same contribution period. Two asks
of different kinds are not duplicates of each other: they say different things, and a member
who has had one may still need the other. The only thing in the interface that reasons per
kind, because it is the only question where the kind changes the answer.

## Boards

### Board

The group that runs the association for one academic year. One board a year,
changing in the autumn, and boards do not overlap — so they are a line rather
than a set, and the association's own history is counted in them.

### Number

A board's ordinal in that line: the ninth board is number 9. A reader sees it as
a Roman numeral, `IX`, so that a board reads as one of a line rather than as a
row in a list.

**Number, not ID.** A board also has a numeric database key, so "ID" would name
two different values — the same trap a game's **code** exists to avoid. On screen
and in conversation a board is identified by its number.

### Academic year

The stretch a board runs, written `2025-2026`. Two calendar years, because a
board takes office in the autumn and hands over in the autumn after.

Derived, never stored. The year follows from the board's dates, so correcting the
dates corrects the year everywhere it is written.

### Name

The board's own name, the one it chose for itself: the third board is Drieden
Board, the seventh Overcooked, the eighth Wasted, the ninth Eeveelutions.

Not every board has a recorded name. A board with none is named from its number,
so no board reads as nameless.

### Cheer

The board's shouted line: the seventh board's "Krijg de tering!", the eighth's
"RNG, Be With Me!". Not every board has a recorded one, and an unrecorded cheer
is usually a forgotten one rather than a board that had none.

Not a motto, not a slogan, not a tagline. A cheer is the thing a board shouts
together, which is why it is never folded into the prose a board writes about
itself.

### Board member

One person's place on one board, with the role they held. The role is written in
the board's own words — "Secretary and Commissioner of the Esports Lounge" —
rather than chosen from a fixed list, because the association has renamed and
combined its roles across nine years.

**A board member is a place, not a person.** The same person may hold several
across the years, so the two cannot be equated: a `BoardMember` row is one
membership of one board rather than the person who filled it. Most of the people
who have held one never had an account here either, and such a membership stands
under its own recorded name.

**"Board member" is the word everywhere.** The class has always been
`BoardMember` and the table `board_members`, and the comments and the copy now
say the same, so there is no divergence here to remember. Where a bare "member"
would read as one of the association's members, or as a `User`, the code says
**board member** or **membership** in full.

### Office

The role a board member held, read as one of a short list: chair, secretary,
treasurer, internal, external, esports. A role is written in the board's own
words, so the office is read out of those words rather than picked from a list:
"Secretary and Commissioner of the Esports Lounge" is a secretary's.

A display rule rather than a fact about a board. The offices in seniority order
are what put a board's members in reading order, so a reader meets the chair
first, and a role naming none of them sorts after all of them in one group.
Nothing is stored and nothing is refused: a board that invents a role still
reads, at the end.

**Not the word "in office" below.** An office is which role somebody held; a
board is in office while the calendar says it runs. Neither sense follows from
the other.

### Nickname

The name a board member is known by, and the membership's own rather than the
person's: `SkyeWolf` in `Roos "SkyeWolf" Kruk`. Most of this history is written
in nicknames, which is why they are recorded at all.

A nickname sits beside the name rather than inside it, the way a roster entry's
**handle** sits beside a person's name, the same grain, belonging to the place
somebody held rather than to the person. A reader sees the two together, quoted.

### Board photo and portrait

The two pictures a board carries. A **board photo** is the group photograph of
the whole board, drawn as the band across the top of its page; a **portrait** is
the picture beside one board member's name.

These join the five picture names in **Banner and Icon** above, and are used the
same way in the code, in the issues and on screen. A board photo is not a "group
shot", and a portrait is not an "avatar" or a "headshot".

### In office

The board running the association now: the one whose dates today falls inside. At
most one board is in office at a time.

Derived, never a flag. A board leaves office by the calendar rather than by
somebody unticking it — the same rule as **currently played**, where a state that
follows from the records is read rather than written.

Not the **office** above, which is the role one board member held. A board holds
no office of its own; it is in office, or it is not.

## User interface

### Island

The site's own design layer: its own reset, its own theme and the diagonal
geometry its bands are cut on, all scoped to a root class so that none of it
reaches the rest of the site.

**Not an esports term.** The island began on the esports pages and was named for
them, and the name has outlived the ownership: it is the site's now, and more
than the esports pages sit on it. It is filed here rather than under a domain
because it names how a page is built rather than anything the association does.

### Shared band names

A band on the island is named, inside and out, for the shape it draws rather
than for the domain that first needed it. `SliceBand`'s classes say `slice`, not
`team-slice`, and `IslandTimeline`'s say `stop` rather than season, though both
components were written for the esports pages and both are now drawn on three pages.

**The same move as the island's own name.** A shared component is read next by
whoever needs its shape, and a name borrowed from its first caller tells that
reader something untrue about what it draws. The public surface followed this
rule when `BannerSlices` became `SliceBand`; the scoped CSS and the locals
inside these two have since caught up.

### Settles / arrives open

A band opens one of its slices as soon as it is drawn. It *settles* when it
decides that where it stands: the slice grows from nothing, which is the visitor
seeing it happen. It *arrives open* when a gesture carried it in: the swipe is
the whole animation, and the slice is open before the band is on screen.

**One opening, two names, told apart by how the band got there.** The visitor
watches a band settle and is meant to; a band the visitor swiped in has already
had its arrival, and growing a slice afterwards asks them to watch the same
thing twice.
