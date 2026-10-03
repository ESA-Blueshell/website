# ADR-011: Platforms Mirror Cohorts, and Report Drift Rather Than Correct It

## Status
Proposed

## Context

The site is about to keep more than Brevo lists in step with who is who: Discord roles and
channels for committees, teams and activists, and Google Workspace groups and shared drives.
Each of these is also edited by hand, by the board on Discord or in Brevo, and by a separate
bot that lets people claim roles. A site that silently overwrites those edits gets switched
off; one that ignores them drifts until nobody trusts it.

## Decision

**Every platform mirrors a cohort through a target, reconciles by recording drift, and
removes people only where an admin has enforced that target.**

- Access on Discord goes through roles. A cohort's target there is a role; a committee,
  team or activists channel grants access to that role, never to a person.
- The site creates or adopts the things a cohort needs. A new committee, game or team gets
  its channel and role created, or linked to an existing one on its form. Existing ones are
  adopted once, in bulk, from a page that proposes matches by name.
- Archiving on the site archives on Discord: the channel moves to the archive category and
  the role empties. Restoring reverses both. Nothing is deleted.
- A reconcile records **missing** and **extra** people and changes neither side. The board
  resolves drift per person or in bulk; an **enforced** target has its extra people removed
  automatically. Targets start out not enforced, and only an admin enforces one.
- The board's role follows the board in office and changes hands the day a new board takes
  office; until then the next board holds **@Kandi**, mirrored as a Brevo list and a Google
  group too.
- A team's role fills from its currently fielded rosters and is never emptied by a season
  ending; removing a team, its role or a player is the board's action.
- The site sets a Games channel's access policy once, when it creates it. Later changes,
  here or on Discord, are shown as a difference and never overwritten.
- A cohort member with no linked account in that system is **unreachable**: counted, asked
  to link, and never treated as drift.

Alternatives considered:

- **The site is the truth everywhere.** Role claims from the other bot and the board's hand
  edits would be undone nightly.
- **Additive only.** A person who leaves a committee would keep its channel forever.
- **Per-person channel permissions.** Every change would touch every channel, and a
  person's access could not be read from their roles.

## Consequences

- The bot needs Manage Roles and Manage Channels, and its role must sit above every role it
  keeps. Roles the claim bot hands out must stay outside the site's.
- Brevo, Discord and Google share one target interface, one drift ledger and one set of
  resolution actions, so the board learns one way to read drift for all three. The
  management pages are arranged by platform, each in its own nouns, and say what each list,
  role or group **follows** rather than naming cohorts.
