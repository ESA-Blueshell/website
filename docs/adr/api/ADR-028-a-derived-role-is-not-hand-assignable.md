# ADR-028: A Derived Role Is Not Hand-Assignable

## Status
Accepted

## Context

The roles a person holds are decided in four places. `GUEST` is the entity default
written when the account is created. `MEMBER` is maintained by `MembershipEventListener`
against the person's active memberships. `COMMITTEE` is maintained by
`CommitteeMembershipChangedListener` against their committee seats. Board, treasurer and
admin are written by hand, and until this feature there was no interface for that: the
only way to make somebody an admin was to write to the `authorities` table.

Giving an admin a panel of tick boxes raises the obvious question of which roles belong
in it. The tempting answer is all of them, since they are all rows in one table and one
endpoint could write any of them.

Three things say otherwise.

**A listener would undo the grant.** Tick `MEMBER` on somebody with no membership and
the row is written. The next `MembershipChanged` for that person — any change to any of
their memberships — recomputes the role from the memberships and removes it. The grant
survives until something unrelated happens, which is worse than being refused, because
the admin has no way to know when it stopped being true.

**Revoking `MEMBER` reaches further than it looks.** `CommitteeSeatRevocationListener`
revokes every committee seat a person holds when they stop being a member. An admin
unticking a box in a role panel would end somebody's committee work, in another module,
with nothing on screen saying so.

**A derived role is already a record.** The question "why is this person a committee
member?" has an answer — their seat — and that seat has its own history. A hand-grant
would put a second, unsourced answer beside it, and the two would disagree.

The alternative considered was a manual-override flag: a granted `MEMBER` that the
listener is told not to touch. That makes the listener's rule conditional on state it
does not own, and it makes "is this person a member?" a question with two answers
depending on who is asking. The association's idea of a member is a membership; the
role is a projection of it.

## Decision

**Only board, treasurer and admin are assignable. The derived roles are read-only, and
the panel names their source.**

- `GrantedRoles.ASSIGNABLE` is the list, and `PUT /users/{userId}/roles` refuses
  anything outside it with a `RoleNotAssignable` code (ADR-026's shape).
- `GrantedRoles.DERIVED` maps `MEMBER` to its membership and `COMMITTEE` to its seat.
  The api answers the source with the role, so the panel can say where to go instead of
  merely disabling a box.
- `GUEST` is shown read-only with the account as its source. It is the entity default and
  is inherited by every role above it, so ticking or unticking it changes nothing
  observable.
- `SYSTEM` is not assignable. It belongs to the service account (V97), which the
  authentication path refuses outright, and handing it out would be handing out something
  that was never meant to leave that account.
- `VEGAN` and `COMPANY` are not assignable either. They are unused, and this decision does
  not make them an enum cleanup.
- A role held only by inheritance is reported as **implied**, never as granted, so a
  deliberate grant and a consequence of one stay distinguishable on screen.

## Consequences

The picker cannot express "this person is a member" without a membership, and that is
the point: the way to make somebody a member is to give them a membership. An admin who
expected the tick box to work is told which record to change instead.

The cascade through `CommitteeSeatRevocationListener` is unreachable from the role panel.
It is still reachable by ending a membership, where it belongs and where the interface
says what is happening.

The assignable set is data the api answers rather than a list the frontend keeps, so
adding a fourth granted role later is a backend change and a regenerated client, not two
edits that can drift apart.

The notification a change sends is handled in `auth` rather than in `user`, which writes the
record. `email` depends on `jobs` and `jobs` depends on `user`, so a `user` that mailed would
close a cycle the module verification refuses. `auth` already owns the mail an account gets about
reaching the site, and already holds every edge the handler needs.

Four roles of the ten are neither granted nor derived. `ANONYMOUS` is the floor and
`SYSTEM` is internal; `VEGAN` and `COMPANY` are unused. Nothing here removes them.

## Related

- ADR-026: A Refused Write Carries a Code, Not a Sentence — the shape of the refusals
- ADR-014: Permission Evaluation Strategy — the `User`/`roles` permission, already admin-only
- `docs/CONTEXT.md`, **Access** — granted, derived, implied and assignable
