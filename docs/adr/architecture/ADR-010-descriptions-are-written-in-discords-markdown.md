# ADR-010: Descriptions Are Written in Discord's Markdown

## Status
Accepted

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
  typed in Discord's names, which are JoyPixels', and exist only while typing.
- Mentions and timestamps are stored as `<@id>`, `<@&id>`, `<#id>` and
  `<t:unix:style>`.
- The site draws standard emoji in one self-hosted set, Noto Emoji, one file per
  emoji under `/emoji/`. The set is Apache 2.0, and its licence ships beside the
  files rather than on the page. Only emoji presentation is drawn, so `©` and
  `™` in running text stay text.
- The build writes the files: the emoji from an npm package, and Noto's flags,
  which Noto keeps apart, fetched from the noto-emoji repository at a pinned
  commit. Nothing of either is committed.
- Every place a description leaves the site follows the dialect. Google
  Calendar and link previews name emoji and mentions in words, write
  timestamps as Amsterdam dates, and never show a spoiler's text. No email
  carries a description.
- A description is capped at 4096 characters, counted as stored. The event
  post is plain text, sent as a message laid out in components, which holds
  4000 characters. The title, the details and the mentions come first, and
  the description takes what they leave: whole, but for one near the cap,
  which is cut at a word. The post's button leads to the whole text.

## Consequences

- A description stored with `__x__` as bold now reads as underlined. No
  migration rewrites it.
- Shortcodes stored before this decision are rewritten once into the emoji they
  name, by the `shortcodes-become-characters` changeset. It uses the names the
  site expanded them by, node-emoji's from emojilib 2.4.0, committed beside the
  changelog. It leaves code, addresses and anything in angle brackets alone,
  as the renderer did. Each value it changes is kept as it was in
  `shortcode_rewrites`, which its rollback restores, and it logs how many it
  changed. Old descriptions look the same on the site, and Discord shows their
  emoji. The site no longer expands a shortcode, so it keeps a single emoji
  name list, JoyPixels', which the editor uses to turn a typed shortcode into
  its emoji.
- Server emoji load from Discord's CDN by id. A deleted emoji falls back to its
  `:name:`, and a standard emoji whose picture is missing falls back to the
  character.
- An image build needs GitHub reachable the first time, for the flags. The
  frontend image grows by about 35 MB of emoji.
- A server emoji counts about thirty characters towards the cap, and the
  counter shows that.

## Amendment: one interpreter in the api, names bound where they can be

### Status
Accepted, amending the Decision above.

### Why the Decision was not enough

The Decision holds a mention or an emoji in Discord's form, but only the editor's
lists and the closing colon produce that form. Text pasted from Discord, from a
document or from an older description arrives as `@sitecie`, `#announcements` and
`:link:`, and stays plain text on the site and in Discord. Descriptions written before
mentions existed hold the same plain names, and nobody will save them again.

The dialect was also read in three places: `marked` and `@lezer/markdown` in the
frontend and commonmark-java in the api. Turning written names into mentions in the
editor and again in the api would have added a fourth rule that could drift from the
others.

Alternatives considered:

- **Convert in the editor and in the api, mirrored, and rewrite old text once.** Two
  copies of the rule, plus a background rewrite that collides with ADR-032's frozen
  posts. Rejected for the drift.
- **Interpret at read only, storing exactly what was written.** One rule, but a
  renamed role turns every old `@name` of it back into text.
- **One interpreter compiled to Kotlin and JavaScript.** Resolving `@name` in the
  browser needs the server's member list on every public page, and the frontend and
  api run different versions of the rule during a deploy.

### Decision

**The api alone reads a description. Everything else draws or sends what it reads.**

- One interpreter in the api turns a description into a typed tree, with the source
  range of each node. The site, the editor's preview, the starboard, the Discord post,
  the Discord scheduled event, Google Calendar and link previews are all made from
  that tree. The frontend keeps no markdown parser that decides meaning; the editor
  keeps its grammar only to style formatting while typing.
- The interpreter resolves a written name by one rule. `@name` is the longest role or
  member name the server has, spaces included, regardless of case; a role wins over a
  member, and the first member found wins over the rest. `#name` with no space after
  the `#` is a channel everybody can see, at the start of a line too; `# ` is a
  heading. `:name:` is a standard emoji by JoyPixels' name or one of the association's
  server emoji. A name the server does not know stays text. Code, link targets,
  addresses and email addresses are never read as names.
- A save binds what it can: each name the interpreter resolves is stored in Discord's
  form, as a pick from the editor's list already is. Where the server's lists cannot
  be read, the text is stored as written and the save succeeds. Nothing rewrites a
  stored description afterwards, and old descriptions are not rewritten: their plain
  names are resolved each time they are read.
- Pasted text is bound by the api as it lands, as its own undo step.
- A ledger keeps the last-known name, and a role's colour, of every role, channel and
  emoji, and of each member a stored description points at. It survives restarts and
  Discord being unreachable, and names what has since been deleted. An index records
  which descriptions point at which target.
- A renamed target needs no rewrite: Discord follows it, and the site and the calendar
  read the new name. Upcoming events' calendar entries are synced again. A deleted
  target reads as its last-known name; the posts of upcoming events are edited to show
  it as text, with no ping, and past posts stay. A post frozen under ADR-032 stays
  frozen.
- The cap counts the stored form. A save that binding would push past it is refused
  with a code. Every cut for Discord falls between nodes, never inside a mention.

### Consequences

- The Consequence above that the editor turns a typed shortcode into its emoji stays,
  but the emoji names it uses come from the api.
- The editor's preview, chips and counter wait on the api, about 200 ms behind typing.
- `marked` leaves the frontend; the starboard is drawn from the same tree.
- A member whose name matches a role's is reached only through the editor's list.
- An old plain `@name` follows today's names: once its role is renamed it reads as text.
- A server emoji of another server resolves only when pasted as `<:name:id>`.

## Related Documentation
- [Architecture ADR index](ADR-INDEX.md)
- `docs/CONTEXT.md`: Description, Emoji, Mention, Events-info post
