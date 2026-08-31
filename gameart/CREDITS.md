# Game art

Default art for the esports pages, held here as the source each upload is made
from. One file per image, capped at 2560 pixels wide, aspect ratio untouched and
nothing upscaled beyond its original. Converted to WebP; nothing else was changed.

## What is here

| Game | Files |
|------|-------|
| CS2 | `cs2-1` – `cs2-5` |
| CS:GO | `csgo-1` – `csgo-3` |
| GeoGuessr | `geoguessr-1`, `geoguessr-2` |
| League of Legends | `lol-1` – `lol-8` |
| Overwatch | `overwatch-1` – `overwatch-4` |
| Rocket League | `rocket-league-1` – `rocket-league-4` |
| Super Smash Bros. | `smash-1` – `smash-4` |
| Trackmania | `trackmania-1` – `trackmania-3` |
| Valorant | `valorant-1` – `valorant-7`, `valorant-banner`, `valorant-boys-dark`, `valorant-boys-rainbow`, `valorant-boys-white`, `valorant-girls-rainbow`, `valorant-girls-white`, `valorant-cypher-phoenix-white` |

Every game the association records a team in has art here. Overwatch is not one of
them: it has no value in the `Game` enum, so nothing can point at those four files
until it does.

## Sizes

These are the originals, at whatever size they came in — up to 8889x5000. What the
site ships is cut from them and fitted inside 2560x1440; the sizes of those masters
are recorded beside them, in the art directory's own credit file.

Four games have an original the shipped art cannot reach 2560 from, because the
original itself is narrower: `GeoGuessr.jpeg` at 1536x1024, `lol4.jpg` at 1920x1149,
and `lol7.png` and `lol8.jpg` at 1920x1080.

`rocket-league-4` is the exception at 460x215. It is a logo rather than a
photograph and its original is that size, so it is fine as a small tile and too
small for a hero. Left here rather than dropped, because which of these is used
where is a choice made at upload.

## home-page/

The art the home page's competitive tiles were drawn with before they started
reading the game records. Eight files, kept at their original bytes rather than
converted, because these are small and already lossy and a re-encode would only
lose more.

| File | Size | Was |
|------|------|-----|
| `cs2.png` | 256x256 | Counter-Strike 2 tile logo |
| `cs2bg.png` | 900x405 | Counter-Strike 2 tile background |
| `geoguessrlogo.webp` | 224x232 | GeoGuessr tile logo |
| `geoguessrbg.jpg` | 1280x720 | GeoGuessr tile background |
| `league.png` | 256x256 | League of Legends tile logo |
| `leaguebg.jpg` | 620x349 | League of Legends tile background |
| `rocketleague.png` | 256x256 | Rocket League tile logo |
| `rocketleaguebg.jpg` | 620x349 | Rocket League tile background |

The four logos survive elsewhere as well: each is pixel-identical to the icon of
the same game in the seed's art directory, and `geoguessrlogo.webp` is
byte-identical to `geoguessr-icon.webp`. They are here so the set the home page
used is complete in one place.

**The four backgrounds survive nowhere else.** They are not crops of anything in
this directory — the closest original to each is a different picture of the same
game — so this is the only copy, which is the reason this folder exists.

## Rights

This is publisher art. Valorant and League of Legends art is Riot Games';
Counter-Strike art is Valve's; Rocket League art is Psyonix's; Trackmania art is
Nadeo's; Super Smash Bros. art is Nintendo's; Overwatch art is Blizzard
Entertainment's; the GeoGuessr images are GeoGuessr AB's. None of it is offered
under a Creative Commons licence, and none of it was made by or for the association.

Each publisher licenses its art through its own press kit and content policy, which
permit fan and community use on specific terms and reserve everything else. Whether
these files may stay is that question, and not this file's to answer — the rows
above are here so whoever asks it knows what they are looking at.

A team's own poster is an upload made by whoever made it, and is not affected by
any of this.
