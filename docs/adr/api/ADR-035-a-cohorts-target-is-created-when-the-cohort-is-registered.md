# ADR-035: A Cohort's Target Is Created When the Cohort Is Registered

## Status
Proposed

## Context

Registering a **cohort** writes its **target** rows with no external id, and #369 made
creating the list an operator's step, so that membership sync would stop creating lists as
a side effect. The step was never taken for the newest contribution period: creating a period
registers nothing, the paid cohort appeared with no list, every push to it failed for good,
and "create new" was refused because the unlinked row already counted as a target.

A cohort without a target is a cohort nobody can mail, ping or share with. The board reads
that as the site being broken, not as a step it forgot.

## Decision

**Registering a cohort queues the creation of its target on every platform the cohort type
mirrors to.** Creating a contribution period or a committee registers its cohorts.

- Creation claims the cohort's target before calling the provider, so a retried job finds
  the claim and never makes a second list, role or group.
- A new Brevo list goes into the folder named for its cohort type, created if missing.
- An unlinked target can always be created by hand as well; being unlinked is not having
  one.
- Cohorts that are unlinked when this lands are filled by a one-off backfill.

What #369 guarded against was creation hidden inside another operation. Here creation is
its own job, named in the job list and visible on the cohort's page, so a list appearing in
Brevo always has a cause the board can see.

## Consequences

- A period created by mistake leaves lists behind in Brevo. Archiving them is the board's
  cleanup, the same as for any list.
- Which platforms a cohort type mirrors to is a fixed map in code. The board adds or removes a
  target per cohort after the fact, never before registration.
