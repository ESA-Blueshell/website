# Game art

The art the site ships with. One file per image, fitted inside 2560x1440, aspect
ratio untouched and nothing upscaled beyond its original. Converted to WebP at
quality 82; nothing else was changed.

Cut from the originals in `gameart/` at the repository root, which are the source of
truth. Each file here is the largest its own original allows, and where a game had a
higher-resolution image spare, that is the one used.

These are the masters. Each is stored on first start and served at the widths its
kind lists, so what a browser downloads is derived from the file here rather than
being another file to keep in step with it.

## What is here

| Game | Page banner | Team posters |
|------|-------------|--------------|
| Valorant | `valorant-banner` | `valorant-1` – `valorant-7` |
| Counter-Strike 2 | `cs2-1` | `cs2-2`, `cs2-3` |
| League of Legends | `lol-1` | `lol-1` – `lol-8` |
| Rocket League | `rocket-league-1` | `rocket-league-1` – `rocket-league-3` |
| GeoGuessr | `geoguessr-2` | `geoguessr-1` |
| Trackmania | `trackmania-1` | `trackmania-2` |
| CS:GO | `csgo-2` | `csgo-1`, `csgo-3` |
| Super Smash Bros. | `smash-1` | `smash-2` |

`csgo-1` and `csgo-3` are Counter-Strike 2 images on a CS:GO team's poster. CS:GO has
two originals of its own and fields three pictures, and the two used here are 4K where
the remaining CS:GO original is 1920 — so the franchise's newer art is what carries
them. Change the row in `teams.csv` if the era matters more than the resolution.

League of Legends and Rocket League field more teams than they have art, so a
picture there carries more than one team. CS:GO and Super Smash Bros. field nobody
any more and keep their art for the seasons they played.

Only what `teams.csv` and `banners.csv` name is here. Art held back rather than
dropped — six further Valorant wallpapers, the smaller Rocket League logo and the
remaining Counter-Strike, Smash and Trackmania images — stays in `gameart/` at the
repository root, ready for a row that names it. The Overwatch images stay there too
and cannot be used at all until `OVERWATCH` is a game the association records.

## Sizes

Every file fits inside 2560x1440, which is 1440p. Nothing here is 4K: the site has no
plan to serve it, and a master wider than the widest width a kind is stored at is
bytes that are only ever thrown away.

Ten are narrower than 2560, for one of two reasons.

Four because their originals are, and nothing is upscaled:

| File | Size | Its original |
|------|------|--------------|
| `geoguessr-1` | 1536x1024 | `GeoGuessr.jpeg`, 1536x1024 |
| `lol-4` | 1920x1149 | `lol4.jpg`, 1920x1149 |
| `lol-7` | 1920x1080 | `lol7.png`, 1920x1080 |
| `lol-8` | 1920x1080 | `lol8.jpg`, 1920x1080 |

Six because they are taller than 16x9, so fitting them inside 2560x1440 is the height
binding rather than the width: `geoguessr-2` at 2327x1440, `lol-5` at 2411x1440,
`lol-6` at 2259x1440, `rocket-league-3` at 2304x1440, and `valorant-4` and
`valorant-5` a handful of pixels off 2560. Fitted rather than cropped, because a
collapsed slice is a portrait strip and uses the height a crop would take.

`ShippedArtCeilingTest` holds the ceiling, so a file added over it fails the build
rather than being noticed in a page's weight.

## Rights

This is publisher art. Valorant and League of Legends art is Riot Games';
Counter-Strike art is Valve's; Rocket League art is Psyonix's; Trackmania art is
Nadeo's; Super Smash Bros. art is Nintendo's; the GeoGuessr images are GeoGuessr AB's.
None of it is offered under a Creative Commons licence, and none of it was made by or
for the association.

Each publisher licenses its art through its own press kit and content policy, which
permit fan and community use on specific terms and reserve everything else. The
association is establishing the terms that apply to each file above. Until that is
written down here, treat this table as the record of what is published and not as a
statement that it may be.

A team's own poster, uploaded by whoever made it, is not affected by any of this.
