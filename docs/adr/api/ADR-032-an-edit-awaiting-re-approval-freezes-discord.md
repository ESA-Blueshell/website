# ADR-032: An Edit Awaiting Re-Approval Freezes What the Bot Has Out

## Status
Proposed

## Context

Only a board member approves an event, and an edit by anybody else sends an approved
event back to the board. Committee members may edit their own events, and the board wants
to see what they change before it reaches everybody.

Until now the Discord side read one fact, approved or not. An event sent back by a
committee member's edit looked exactly like one the board unapproved, so the bot took down
its events-info post, its events-calendar post and its Discord event. When the board
approved the edit, the bot posted everything again and notified the pinged roles a second
time. A typo fix cost the server a deleted announcement and a repeated ping.

## Decision

An event sent back by an edit is **awaiting re-approval**, a state the event records apart
from unapproved. While it waits, what the bot has out is frozen: it stays as last approved,
is neither edited nor notified again, and still comes down on time, by the event's times
as they now stand. Re-approved, it is brought up to date without notifying. Unapproved by
the board, it comes down as it does today.

The event page is hidden from visitors while the event waits, so the frozen posts link to a
page they cannot open until the board decides. That is accepted: the wait is short in
practice, and the alternative is versioned event content.

## Considered Options

- **Take everything down while it waits** (the behaviour before this decision). Keeps
  unreviewed content off Discord, but deletes a public announcement and pings twice for
  every committee edit.
- **Keep editing while it waits.** Publishes a committee's unreviewed change on Discord,
  which is what sending it back to the board is meant to prevent.
- **Show visitors the last approved version while an edit waits.** Removes the dead link,
  but needs every event field versioned.

## Consequences

- The event needs a field that tells "awaiting re-approval" apart from "never approved"
  and "unapproved by the board", set by the edit that sends it back.
- The bot's jobs skip every edit and every new post for a frozen event, and say so.
  Removal by time is the only change they make.
