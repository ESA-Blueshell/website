# ADR-027: Joining Asks For The Contribution

## Status
Accepted

## Context

A new member was never told how to pay their contribution by this application. The
payment information members remember receiving — the fee, the bank account, the BIC,
the direct-debit mandate, the cash option — was composed by hand in the mail platform
and sent by a board member. No version of this codebase has ever contained it, which
is why nothing broke when it stopped: nothing here was doing it.

What the application did send was worse than nothing. The single-member contribution
reminder told members to pay "via our website", a sentence unchanged since the legacy
Java codebase and false throughout, because the site takes no money. That sentence is
the likeliest reason the association was mailing real instructions by hand in the
first place.

So the question was where an automated ask belongs, and what it may promise.

**Where it hangs.** `MembershipChanged CREATED` is the obvious hook and the wrong one.
It fires for a board member starting a membership on somebody's behalf, which is
administration rather than joining; a treasurer backfilling last year's memberships
would mail thirty people a bill. `SignupCompletionService.completeIfReady` is already
the single documented place a self-service membership is created (ADR-025), and it is
the only place that knows a membership came from the form.

**What it may promise.** The mail members remember carried a deadline — "this fee must
be paid before 8 November" — with a consequence attached. Enforcing that automatically
requires knowing who has not paid, and this system cannot: a `Contribution` row is the
record of payment and is only ever created by a board action. The absence of one means
either "has not paid" or "the treasurer has not typed it in yet", and nothing
distinguishes them. A timer acting on that absence would eventually end the membership
of somebody who paid in cash into postbus 49 three weeks earlier.

**Whether it is recorded.** The payment-emails flow treats every request for money as a
row, one per ask, and the wizard shows the treasurer when each member was last asked.
An ask that writes nothing would leave that column empty for a member asked a fortnight
ago, so the next bulk send would go out as a first request carrying a different,
hand-typed deadline than the one the member already holds.

## Decision

A membership that starts through the signup form asks the new member for their
contribution, in an email sent before anything is owed.

1. **The ask is made from `completeIfReady`**, through a `JoiningContributionAsk` port
   the `contribution` module publishes and `auth` consumes. `auth` decides when
   somebody has joined; `contribution` decides what joining costs and what they are
   told. Board-created memberships ask nothing.

2. **It is priced by the rules that already exist** — the current or latest
   contribution period, and the member's start date against that period's half-year
   cutoff. No new pricing policy, and no amount typed anywhere.

3. **It is recorded as a `ContributionReminder`**, because that is what it is: one
   asking of one member to pay for one period. Which of the two emails a record becomes
   is decided by the job type that renders it, so the distinction never reaches the
   schema.

4. **The two-week deadline is a warning, and nothing more.** It states that the
   membership role is revoked if the fee does not arrive. A board member acts on it.
   No scheduler reads it, and nothing is stored for one to read.

5. **This is the only payment email that may offer a direct-debit mandate as a way to
   pay.** On a reminder, an amount is already due and no debit run exists to collect
   it, so the same offer would invite a member to sign a form instead of paying and
   then be chased for money they believe they have arranged. Both reminders offer the
   mandate for the years after this one instead.

## Consequences

The association's first statement to a new member is now made by the application, in
one voice with the reminders that follow it, and the false instruction to pay on the
website is gone.

The treasurer's "last sent" column is truthful for members who joined mid-year, and a
bulk send to them is visibly a second ask rather than a first.

`auth` now depends on `contribution :: api`. That edge is real — joining costs money —
and it points at a port rather than at the module's internals, but it is a new edge
between two modules that were previously unrelated.

The deadline is a promise the software does not keep on its own. That is deliberate and
is the honest position given what the data can express, but it means members learn
whether it is enforced from how the board behaves, not from the system. Enforcing it
would need a contribution to exist at signup as an unpaid obligation, which is a
different decision about what a `Contribution` row means, and is not made here.

A member who joins when no contribution period exists is asked for nothing. The period
is board work, and in the weeks before a new one is created a joining member hears
nothing about payment until the treasurer's first send. Creating the period earlier is
an operational fix, not a code one.

## Related

- ADR-025: Membership Commit Rendezvous — why `completeIfReady` is the single commit point
- ADR-019: the `EmailContent` anti-corruption layer these builders produce
- `docs/flows/membership-signup/README.md` — the joining ask in the flow it belongs to
- `docs/flows/payment-emails/README.md` — the treasurer's sends, which read the same records
