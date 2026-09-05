# ADR-008: Printed figures stand until the records answer

## Status
Accepted

## Context
The pages that sell the association state facts about it: how many committees run, how many
teams stand this season, how often something happens here. These were prose, and they went
stale — the about us page said "10+ committees" while the association's own article said
fifteen.

`GET /statistics/association` answers six of them, and arrives after the page mounts. That
raises the usual question of what the page shows meanwhile, and a less usual one: the member
count cannot be read at all, because reading members needs a permission and these pages are
read by visitors who have none.

## Decision
Two rules, and they are different rules for different reasons.

**A figure the api can count starts on a published floor and is replaced when the count lands.**
The floors in `numbers.ts` are not guesses: each is a number the association's own 2025
partnership overview or its article in De Appel already publishes. A floor is drawn with a `+`
after it and a count is drawn bare, so nothing on the page ever claims a count it does not
have. The page keeps its height, never shows a grey box where a number goes, and still says
something true when the read fails — which is why nothing reports an error.

**A figure nothing can count is a claim, and never becomes exact.** The member count and the
Discord figure are both claims: one is permission-gated, and the other belongs to a service
this site cannot count. They are built the same way as the counts so the band draws one kind
of thing, and they simply never flip to exact.

A page names the figures it wants; it does not get a fixed set.

## Alternatives rejected

**A skeleton while the numbers load.** Consistent with the esports and board pages, which pulse
a grey block. Rejected here: those pages draw records a reader came to see, and a marketing
page whose first paint is a row of grey boxes has lost the argument before making it.

**Render nothing until the numbers arrive.** Cleanest first paint, but the page visibly reflows
and a failed read silently drops the strongest thing on it.

**Make the member count readable.** The obvious fix to the asymmetry, and the one to reject
hardest: opening a members list to anonymous callers to put a number on a marketing page is a
poor trade, and it is not even the interesting number — the Discord figure is five times larger
and belongs to a service that publishes it anyway.

## Consequences
- Someone will notice that some figures are live and others never are, and this is the file that
  says why.
- The floors are copy, and they go stale exactly as hardcoded prose did — more slowly, because
  they are only seen before the records answer or when the read fails, but they are still a
  thing to check when the association reprints its material.
