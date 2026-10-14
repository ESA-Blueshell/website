# ADR-040: A Membership Type Is Dated, and a Mandate Decides Direct Debit

## Status
Accepted

## Context

A membership carries a member type (regular, alumni, honorary or a leftover NONE) and, until
#2460, an `incasso` flag. Becoming alumni or honorary has no transition: the board either edits
the type in place, which loses when it changed, or ends the membership and starts another the
same day, so one person collects back-to-back memberships that say nothing about a gap.

#2460 moved incasso to the person as payment details with a mandate, but kept a separate
"pays by direct debit" switch beside the mandate. The two can disagree: someone switched on
with no mandate, whom an incasso run then has to leave out. Payment emails could also send an
incasso notification from the wizard, apart from any collection, and choose it per member from
that switch.

Fees follow the member type of the membership that overlaps the period, so a member who turns
alumni or honorary mid-year has no clear answer for the year they changed in.

## Decision

**A membership is one unbroken spell; what kind of member someone is lives inside it as a dated
membership type. Whether someone pays by direct debit is whether they have a mandate.**

- A **membership type** is regular, alumni or honorary, each from a date. A membership has one or more,
  one after the other. Only the board records them. NONE goes, as regular.
- A new membership starts only after a real gap. Back-to-back memberships already on file are
  merged into one, with a membership type per old row.
- The fee a member is asked follows their membership type on the day they are asked. What they owe for
  a period is the latest ask's amount, or before any ask what their membership type today would be
  asked. Honorary owes nothing from its first day, which drops the current period's unpaid
  contribution; earlier unpaid periods stay owed.
- A **mandate**, online or paper, is the only "pays by direct debit". The separate switch goes.
  A mandate ends when the member stops it, when the bank account it was given for is replaced or
  deleted, or when the membership ends. A member can sign one while signing up.
- Memberships flagged for incasso with no mandate on file become paper mandates without bank
  details, which an incasso run lists as needing them.
- Only an **incasso run** sends incasso notifications, at least 14 days before its collection
  date. The treasurer may pick another fee type for a member in a run, never an amount. A
  collected contribution counts as paid on the collection date. A **storno** makes it unpaid
  again and leaves the mandate in place.
- The payment-emails wizard sends only contribution reminders. Members with a mandate start
  unticked with a warning, to be ticked on purpose after a storno or for an older period.
- The person's one **bank account** serves both a mandate and declarations, so the 13-month
  wipe of ADR-038 counts from its last use, a collection or a declaration paid to it, and does
  not run while a mandate stands.

Alternatives considered:

- **A new membership per change of type.** Today's habit: simple rows, but a person's history becomes
  a chain of memberships that hides real gaps, and the end of one and the start of the next
  must always be kept in step.
- **Alumni and honorary dates on the person.** Independent of membership spells, so an
  "alumni since" could fall outside any membership and fees would have to reconcile two
  timelines.
- **The membership type at the start of the period decides the fee.** Predictable, but the
  association wants a member asked after turning alumni to be asked the alumni fee.
- **A switch beside the mandate**, as #2460 built. Two facts for one question, and the
  disagreement between them needs its own refusal.

## Consequences

- The memberships, payment details and incasso run already on `integration/management` change
  in order: membership types first, then the mandate as the only switch, then the runs' 14-day rule,
  paid-on-collection and storno.
- A merge of back-to-back memberships rewrites history for those members; the merge keeps every
  old start date as the start of a membership type, so nothing that was known is lost.
- Anything that reads the membership type now reads it on a day, and must say which day.
- ADR-038's retention line is amended to count declarations as a use of the bank account.

## Related Documentation
- [ADR-036 A membership is pending until its first contribution](ADR-036-a-membership-is-pending-until-its-first-contribution.md)
- [ADR-038 Private details are sealed by Vault Transit](ADR-038-private-details-are-sealed-by-vault-transit.md)
- [ADR umbrella index](../ADR-INDEX.md)
- [AGENTS.md](../../../AGENTS.md)
