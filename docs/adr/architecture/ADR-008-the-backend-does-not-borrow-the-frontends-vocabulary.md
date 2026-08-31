# ADR-008: The Backend Does Not Borrow the Frontend's Vocabulary

## Status
Accepted

## Context

The esports module called a game a *game page*. The entity was `GamePage`, the
table was `game_page`, and the service, the repository, the responses and the
read models all followed. So did the prose: comments explained backend decisions
in terms of pages, slices, bands, strips, dialogs, a season switcher and the
esports index.

None of that is what the module holds. A row says what a game is — its
identity, its name, the address it answers to, the art it carries, where it sits
among the others. It says so whether or not a page ever renders it, and it would
go on saying so if the site were replaced tomorrow.

The borrowing was not an accident of one commit. The records were extracted from
the frontend, where six near-identical components each hardcoded one game, so
the first name for the new table was the name of the thing being deleted. It
then spread by ordinary consistency: a `GamePage` entity gets a
`GamePageService`, whose KDoc says "every game has a page", read by the next
person as the vocabulary of the module.

It also cost accuracy in a way that is easy to miss. The column holding
`VALORANT` was called `game`, on a table called `game_page` — so the entity's
own name was `GamePage.game`, and the thing that was actually a code could not
be called one, because the word "game" had been spent on the presentation
wrapper. Every service in the module worked around it: `codes()`,
`codeFor(name)`, and a `contentsOf` that read `requireGame(game).game` into a
variable it named `code`.

## Decision

**The backend names what it holds. It does not name the surface that displays
it, in identifiers or in prose.**

Three rules follow.

**A record is named for what it is, not where it appears.** A game is `Game`. A
member's consent that their real name may be published is `nameOnRosters`, not
`nameOnTeamPages` — a roster is a thing the association keeps, and a page is one
way of looking at one.

**Identity is distinguished from name.** Where a record carries both a stable
handle and a human name, the handle is `code` and the name is `name`. On the
record's own contract the field is `code`; on a record referencing it, the field
is named for the thing referenced (`team_season.game`, `?game=`), because there
it answers *which*, not *what is this*.

**Comments name the act or the caller, not the widget.** "The public read draws
every team at once" rather than "the page draws every team at once". Where the
frontend is genuinely the other side of a decision, it is called the frontend —
that is its name, not its furniture.

`@Schema` descriptions carry no rendering detail. They are read by every client;
how one of them lays the value out is not part of the contract.

### What this does not cover

User-facing copy. A validation message, a refusal sentence and a form label are
addressed to a person who is, in fact, looking at a page, and telling them about
"rosters" or "reads" would be worse for them. The identifier names the fact; the
copy describes where they will see it.

Genuine pagination. Spring's `Page` and `Pageable`, and `$.page.totalElements`
on the wire, are not our word.

Prose outside the module that happens to mention a page for its own reasons —
`file/api/PublicImages` explaining why an image is public, the `oidc` comment
about friendly error pages — is about those things, not about esports.

## Consequences

**Positive.** The schema, the contract and the Kotlin agree about what a thing
is. A reader who has never seen the frontend can read the module. A component
renamed in the frontend cannot silently make a backend comment wrong.

**Negative, and accepted.** Some comments lost explanatory force. "The page
draws every team at once, and lazy loading them would be one query per team"
justified a `JOIN FETCH` by naming the consumer that made it necessary; "one
read answers with every team at once" says the same thing less vividly. The
alternative was a rule with an exception for prose, which is not a rule — the
`GamePage` entity was itself introduced with careful, accurate comments that
happened to say "page", and it is exactly that combination which made the word
look like the module's own.

**Cost paid once.** Renaming after release is expensive: a column rename is a
migration and a field rename is a breaking contract change. This one was free
because the migrations were unreleased. The rule exists so the next one does not
have to be paid for at all.

## Related Documentation
- [ADR-003: Package Topology and Placement Rules](ADR-003-package-topology-and-placement-rules.md)
- [API ADR-010: Database Migrations with Flyway](../api/ADR-010-database-migrations-with-flyway.md)
- [API ADR-012: API Documentation with OpenAPI](../api/ADR-012-api-documentation-with-openapi.md)
