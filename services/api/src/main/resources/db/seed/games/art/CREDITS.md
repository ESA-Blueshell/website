# Game art

The banner and icon each game ships with; team posters are in `db/seed/esports/art`.
One file per image, fitted inside 2560x1440, aspect ratio untouched and nothing
upscaled beyond its original. Converted to WebP at quality 82; nothing else was changed.

Cut from the originals in `gameart/` at the repository root, which are the source of
truth. Each file here is the largest its own original allows, and where a game had a
higher-resolution image spare, that is the one used.

These are the masters. Each is stored on first start and served at the widths its
kind lists, so what a browser downloads is derived from the file here rather than
being another file to keep in step with it.

## What is here

| Game | Banner | Icon |
|------|--------|------|
| Valorant | `valorant-banner` | `valorant-icon` |
| Counter-Strike 2 | `cs2-1` | `cs2-icon` |
| League of Legends | `lol-1` | `league-icon` |
| Rocket League | `rocket-league-1` | `rocket-league-icon` |
| GeoGuessr | `geoguessr-2` | `geoguessr-icon` |
| Trackmania | `trackmania-1` | `trackmania-icon` |
| CS:GO | `csgo-2` | `csgo-icon` |
| Super Smash Bros. | `smash-1` | `smash-icon` |
| Teamfight Tactics | `teamfight-tactics-1` | `teamfight-tactics-icon` |
| Minecraft | `minecraft-1` | `minecraft-icon` |
| Pokémon | `pokemon-1` | `pokemon-icon` |
| Hero shooters | `hero-shooters-1` | `hero-shooters-icon` |
| Dota 2 | `dota-2-1` | `dota-2-icon` |
| Overwatch | `overwatch-1` | `hero-shooters-icon` |

The casual games' banners and icons are the frontend's old casual band art (`services/frontend/src/assets`,
620 pixels wide, the size it always shipped at) converted to WebP, except `overwatch-1`,
which is cut from `gameart/overwatch-1.webp`. Chess, Fighting games and Wordle have no art
yet and are drawn with their plate until somebody uploads one.

`lol-1`, `rocket-league-1` and `geoguessr-2` are a team's poster as well as the game's banner,
so the same file is in `db/seed/esports/art` too. Each module ships its own copy.

Only what `banners.csv` and `icons.csv` name is here.

## Sizes

Every file fits inside 2560x1440, which is 1440p. `geoguessr-2` is 2327x1440, being taller
than 16x9. `ShippedArtCeilingTest` holds the ceiling, so a file added over it fails the build
rather than being noticed in a page's weight.

## Rights

This is publisher art. Valorant and League of Legends art is Riot Games';
Counter-Strike and Dota 2 art is Valve's; Rocket League art is Psyonix's; Overwatch art is
Blizzard's; Teamfight Tactics art is Riot Games'; Minecraft art is Mojang's; Pokémon art is
The Pokémon Company's; Trackmania art is
Nadeo's; Super Smash Bros. art is Nintendo's; the GeoGuessr images are GeoGuessr AB's.
None of it is offered under a Creative Commons licence, and none of it was made by or
for the association.

Each publisher licenses its art through its own press kit and content policy, which
permit fan and community use on specific terms and reserve everything else. The
association is establishing the terms that apply to each file above. Until that is
written down here, treat this table as the record of what is published and not as a
statement that it may be.

A game's own banner or icon, uploaded by whoever made it, is not affected by any of this.
