# Proposal: Membership management redesign

Status: Draft (for review)
Scope: `services/api` (membership domain), `services/frontend` (management UI), global mobile styling

## Summary

Membership management is reworked around an explicit, honest interval model.
The board-facing Member Manager becomes a single wide table on laptops and a
purpose-built stacked layout on mobile, and per-user membership editing moves
into a dedicated modal that shows a user's full membership history and offers
create / end / reopen / correct / delete (with admin restore). The ad-hoc
Start / End / Resume buttons and the generic "update any field" membership
endpoint are replaced by intent-revealing operations guarded by server-enforced
invariants.

## Background and current state

A membership is a row in the `memberships` table with a `startDate` and a
nullable `endDate`; a null `endDate` means the membership is currently active.
A user may hold several memberships over time, and those intervals may be
disjoint (a member leaves and later returns). Membership is distinct from
contribution tracking: the `contributions` table (`user_id`,
`contribution_period_id`) records whether fees were paid for a given academic
year and is owned by the treasurer. This proposal does not touch contribution
data beyond reading it.

Observations about the code as it stands:

- The backend already permits disjoint memberships:
  `NoExistingMembershipForUserValidator` blocks only a second *active*
  membership (`existsActiveMembershipByUserId`), so a new interval may be
  created once the previous one is ended.
- The frontend does not model history. `MemberManager.vue` builds
  `membershipsByUserId` as one membership per user (last-write-wins) and splits
  users into "Members" / "Non-members" by whether an active membership exists.
- "Resume" reopens the existing row by clearing `endDate`; "End" stamps
  `endDate = today`; "Start" creates a membership through
  `StartMembershipDialog`.
- Membership mutation goes through a generic `PUT /memberships/{id}` that can
  set `startDate`, `endDate`, `memberType`, and `incasso` at once, with an
  optimistic-locking `version`.
- There is no membership `DELETE` endpoint exposed, and no way to correct a
  wrong `startDate` short of delete-and-recreate.
- `MembershipEventListener` toggles the `MEMBER` role from a single event's
  `active` flag (`saved.endDate == null`), reacting to the changed row rather
  than recomputing from the user's full set.
- No `v-data-table` is used anywhere; `v-table` is the house table component
  (Event sign-ups, Circuit Showdown, cohort drift panel). Management pages
  otherwise use `v-list`.
- The app's mobile/desktop boundary is `lgAndUp` (desktop) vs `mdAndDown`
  (mobile), as used by `App.vue` navigation and `TopBanner`.
- Management pages hardcode `mx-3` wrappers around cards that add their own
  `px-5`, so the content is inset roughly 32px per side on a phone. The rest of
  the app leans on `v-container`, which already drops to `padding: 8px` on `xs`
  via one rule in `housestyle.scss`.

## Goals

- Represent disjoint membership history honestly and make it visible to the
  board.
- Replace Start / End / Resume and the generic update with intent-revealing
  operations that cannot produce nonsensical data.
- Present a dense, information-rich table on laptops and a layout designed for
  phones rather than a degraded table.
- Let the board see when a user first became a member.
- Reduce excessive page padding on mobile across the site.

## Domain model and invariants

A membership remains a dated interval; the schema is unchanged. The following
invariants are enforced server-side, per user, on every mutation:

1. **At most one active membership** (`endDate == null`).
2. **No overlapping intervals.**
3. **No future dates:** `startDate <= today` and `endDate <= today`.
4. **Strictly positive span:** `startDate < endDate` (no zero- or one-day
   memberships).
5. **Role recomputed from the set:** after any change to a user's memberships,
   the `MEMBER` role is recomputed as `active = ∃ membership with endDate ==
   null` for that user, and set or cleared accordingly. This replaces the
   per-event toggle and fixes the drift where editing an old ended interval
   removed the role from a currently active member.

"Member since" is derived as the earliest `startDate` across all of a user's
memberships (first-ever start). It is stable across renewals and returns.

## Operations

The generic update is replaced by intent-revealing commands, consistent with
the existing command/handler style:

- **Create** — `boardCreateMembership` (existing `POST
  /users/{userId}/memberships`). Creates an active membership when `endDate` is
  omitted, or backfills a closed historical interval when both dates are given.
  Guarded by the invariants.
- **End** — new `EndMembershipCommand(id)`. The server stamps `endDate = today`;
  the client never supplies the date, so future ends are impossible. When
  `today == startDate` the membership has a zero-length span and End is
  rejected: the correct action is Delete (the membership should not exist).
- **Reopen** — new `ReopenMembershipCommand(id)`. Clears `endDate`; permitted
  only when the user has no other active membership. Covers accidental ends and
  changed-mind-about-leaving.
- **Correct** — `CorrectMembershipCommand(id, startDate, endDate?, memberType,
  incasso, version)`. The only path that sets a specific *past* `endDate` (for
  example, fixing a mis-recorded end). Full invariant validation; optimistic
  locking via `version`.
- **Delete** — new `DELETE /memberships/{id}`. Soft delete via the existing
  `@SQLDelete` (`deleted_at`); allowed directly on an active membership; guarded
  by a confirmation dialog in the UI.
- **Restore** — new admin-only `POST /memberships/{id}/restore`. Resets
  `deleted_at` to the not-deleted sentinel. Requires a fetch path that includes
  soft-deleted rows, bypassing the entity's `@SQLRestriction`.

Genuine returns after a gap are modelled as a **new interval** (Create), not by
reopening an old one; reopening is reserved for corrections where no real gap
existed.

### Distinct actions, reconciled

- End always stamps today; Correct is the only path that sets an arbitrary past
  end date. This keeps the everyday action safe and quarantines free-form date
  editing.
- Overlaps are forbidden entirely, so the active membership is necessarily the
  latest by `startDate`; End / Reopen / status derivation never conflict.

## Read model — Member Manager table

The page loads: all users (already unpaginated via `findUsers`), **all
memberships** (bounded, since a continuous membership is one row spanning years
— it grows with members × stints, not members × years), and **only the selected
contribution period's contributions** (contributions grow with members × years,
so all-contributions is deliberately avoided). Everything else is derived
client-side.

Columns: **Name · Username · Role · Status · Member since · Type · Incasso ·
Paid/Unpaid · Actions.**

- **Status** (Current / Former / Never) derives from membership data: Current =
  has an active membership; Former = has a past membership but none active;
  Never = no memberships.
- **Member since** = first-ever `startDate`.
- The **latest** membership feeds Type, Incasso, and Status.
- **Type** and **Incasso** render as small icons with tooltips, shown only for
  notable states (Honorary, Alumni, incasso-on); ordinary states stay visually
  quiet so exceptions stand out.
- **Paid/Unpaid** reflects the selected contribution period; the period selector
  defaults to the most recent period.
- Existing search (`filterUsers`) is kept; client-side column sort is added on
  Name / Member-since / Status (default: Name).

Per-row actions render as compact icons: **Manage membership** (opens the
modal), **Edit profile** (opens the user form in its own dialog rather than an
inline row expansion), **Delete user** (existing confirmation dialog).
**Add user** becomes a single primary button above the table.

## Edit-membership modal

- Follows the styling of the other membership pages. Fetches a single user's
  memberships unscoped (at most ~20 rows realistically).
- Read-only interval list with contextual actions. The active / most-recent
  interval offers **End**; an ended interval offers **Reopen**. A per-row
  **Edit** (pencil) reveals fields for **Correct**, and **Delete** lives inside
  that edit affordance rather than beside End, to avoid confusion.
- Admins additionally see soft-deleted intervals, visually distinct (greyed /
  struck-through, "deleted" chip), each with **Restore**. Non-admins never see
  them.
- Create form: shown directly when the user has zero memberships; otherwise
  behind a **New membership** button that reveals the form. The button is
  disabled with a hint when the user already has an active membership.

## Responsive and styling

- `lgAndUp` renders the dense `v-table`; `mdAndDown` renders a stacked
  card-per-user layout sharing the same computed row data, with the row actions
  as buttons.
- Global mobile padding: the container rule in `housestyle.scss` is extended to
  reduce the page gutter at `smAndDown`, the management pages are migrated off
  hardcoded `mx-3` onto the standard container so they inherit it, and card
  inner padding is trimmed on mobile. Because this is a global change, it
  includes a visual pass across the main screens (home, events, management,
  forms).

## Out of scope

- Explicit "merge memberships" — covered by Delete + Correct.
- Future-dated ends — the whole application treats an end date as immediate;
  changing that is a separate, riskier thread.
- Any write to contribution or payment data from this feature.

## Delivery

The work is filed as a series of issues under a tracking epic, ordered by
dependency: backend invariants and lifecycle first, then delete/restore and
query endpoints, then the frontend table, modal, profile/actions, mobile
layout, and the global padding pass. See the epic issue for the current list
and status.
