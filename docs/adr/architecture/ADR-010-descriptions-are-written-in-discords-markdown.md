# ADR-010: Descriptions Are Written in Discord's Markdown

## Status
Accepted

## Implementation status
Landing in four slices under one epic. Until the last one merges, the renderer
still reads GFM and the api's outputs pass descriptions through raw.

## Context

Every **description** on the site is shown twice: on the site, and in the
Discord server the bot posts events to. Members write them the way they write
Discord messages, with server emoji, mentions and `__underline__`. The site
read them as GitHub-flavoured markdown, which disagrees with Discord in places:

- `__x__` is bold in GFM and underlined in Discord.
- `~x~` is struck through in GFM and literal in Discord.
- `||x||` and `-# x` mean nothing in GFM.
- `<:name:id>` and `<@id>` are escaped by the site and drawn by Discord.
- An emoji shortcode such as `:sparkles:` is expanded by the site but sent to
  Discord as text, because Discord expands shortcodes only in its own client.

Alternatives considered:

- **Keep GFM and translate for Discord.** Every Discord-only form would need a
  site equivalent. The same text would still read differently in the two places.
- **Store shortcodes and resolve them when shown.** Discord, Google Calendar,
  email and link previews would each need the resolver. A renamed server emoji
  would stop resolving.

## Decision

**A description is written in Discord's markdown, and holds Discord's own form
of what it contains.**

- The dialect is Discord's: `__underline__`, `||spoiler||`, `-# subtext`, and
  only `~~` strikes through. Tables and images are the site's own additions.
- A finished emoji is stored as the emoji. A standard emoji is its Unicode
  character. A server emoji is `<:name:id>` or `<a:name:id>`. Shortcodes are
  typed in Discord's names and exist only while typing.
- Mentions and timestamps are stored as `<@id>`, `<@&id>`, `<#id>` and
  `<t:unix:style>`.
- The site draws standard emoji in one self-hosted set, Noto Emoji. The set is
  Apache 2.0, and its licence ships beside the files rather than on the page.
- Every place a description leaves the site follows the dialect. Google
  Calendar, link previews and email name emoji and mentions in words, and never
  show a spoiler's text.
- A description is capped at 4096 characters, Discord's limit for an embed
  description, counted as stored. The event post carries its links as buttons,
  so the embed holds the whole description.

## Consequences

- A description stored with `__x__` as bold now reads as underlined. No
  migration rewrites it.
- Shortcodes stored before this decision keep being expanded when shown, so old
  text does not regress. Discord still shows them as text until the description
  is edited.
- Server emoji load from Discord's CDN by id. A deleted emoji falls back to its
  `:name:`.
- A server emoji counts about thirty characters towards the cap, and the
  counter shows that.

## Related Documentation
- [Architecture ADR index](ADR-INDEX.md)
- `docs/CONTEXT.md`: Description, Emoji, Mention, Events-info post
