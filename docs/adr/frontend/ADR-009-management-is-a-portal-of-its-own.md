# ADR-009: Management Is a Portal of Its Own

## Status
Proposed

## Context

The board's tools grew one page at a time into the public site's frame: a user manager, an
address manager, a recovery manager, jobs, emails and four cohort pages, reached from a
dropdown behind a mark in the site's bar. Each carries the public header, footer and hero
band, and most of the work happens in dialogs, some opening further dialogs. The next tools
(platforms, an inbox, alerts, exceptions, approvals, committee and board admin) would add a
second row of navigation under the site's own, and on a phone the tools lose actions the
desktop has.

## Decision

**Management is a portal of its own under `/management`, with its own bar and navigation,
entered and left through the account menu.**

- Anyone holding a granted role (board, treasurer, admin) sees "Switch to management" in
  the account menu; inside, the same account button offers "Back to the site". The
  management mark leaves the site's bar, and the alert count sits on the account button.
- A sidebar grouped by job to do: Dashboard and Alerts; Members; Content; Mail; Platforms;
  System. A phone gets a bottom bar with Dashboard, Alerts, Members and More. What a reader
  cannot open is not listed, and every route still carries its role in `meta` (ADR-005).
- The same island parts in a working mode: no hero bands or grain, compact page heads, a
  Management wordmark in the bar.
- A record opens its own page rather than a dialog; acting on many is a task page whose
  check step says what will happen and what is left out; a dialog only confirms one act
  that cannot be taken back.
- Editors the site already has (committee, board, game, event) render inside Management
  with a way to the public page; the public pages keep their Edit button for committee
  members.
- Today's addresses redirect to their new homes, so bookmarks and old emails keep working.

Alternatives considered:

- **A second navigation row inside the site's frame.** Two bars on every page, and the
  public header and footer around work that is not public.
- **A separate application or subdomain.** A second build, session and deployment for pages
  that share the site's components, api client and sign-in.

## Consequences

- Management pages are designed and built in the portal frame; a page stays in the public
  frame only if the public reads it.
- The router gains a management layout route whose children are the portal's pages; the
  guard keeps evaluating each child's own role.
