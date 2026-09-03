# Board photographs

The board photographs and portraits the site ships with. One file per picture,
aspect ratio untouched and nothing upscaled beyond its original. Resized with
ImageMagick's Lanczos filter where the ceiling made a resize necessary, then
encoded with `cwebp`; nothing else was changed.

Cut from `services/frontend/src/assets/board5..board9/`, which is where the board
page has always read them from and which stays the source of truth until #935
drops those columns. Where that directory holds two copies of a picture — board 6
ships both `amber.jpg` and `amberBIG.jpg` — the **larger** one is the master here,
because a portrait is enlarged when a member opens and resolution thrown away
cannot come back.

These are the masters. Each is stored on first start and served at the widths its
kind lists, so what a browser downloads is derived from the file here rather than
being another file to keep in step with it.

## Two kinds, two ceilings

A **board photo** is a group photograph drawn full-bleed across a band, so it is
treated exactly as a game banner is: fitted inside 2560x1440 at quality 82, and
served at `320, 640, 960, 1280, 1920, 2560`.

A **portrait** is one person, drawn as a thumbnail beside their name and enlarged
only when the member opens. Its longest edge is 960 and it is encoded at quality
85, so a 2:3 photograph comes out 640 wide — the widest width the kind is stored
at. Nothing on the page draws a portrait wider than that, and a portrait carrying
banner-sized copies would be twenty-one more group photographs to download.

## What is here

| Board | Board photo | Portraits |
|-------|-------------|-----------|
| 5 Bobshell | `board5` | none recorded |
| 6 Don't starve together | `board6` | `board6-amber`, `board6-thomas`, `board6-jelle`, `board6-jonas`, `board6-roos`, `board6-thijs` |
| 7 Overcooked | `board7` | `board7-reini`, `board7-max`, `board7-jesse`, `board7-mitchell`, `board7-sanne` |
| 8 Wasted | `board8` | `board8-michal`, `board8-joris`, `board8-chris`, `board8-yannick` |
| 9 Eeveelutions | `board9` | `board9-emma`, `board9-viktor`, `board9-taha`, `board9-sylwia`, `board9-boris`, `board9-rene` |

Boards 1 to 4 and board 10 have no photograph at all: the first four predate
anybody keeping one, and the tenth has not sat yet. Their rows in `boards.csv`
name no art, and the page draws the board's own colour instead.

`board7-reini` is `board7/reinout.jpg`, which is the file the page has always
drawn for Reini Strating. Named for the member rather than for the asset, because
the member is what the row is about.

A row's `photo` or `portrait` cell **is** the file's name here, with `.webp` for
an extension, so there is no table between the two to fall out of step.

Every name here is unique across `boards.csv` and `members.csv`, and that is a
rule rather than a coincidence: storage is content-addressed, `boards` and
`board_members` each carry a unique key on `picture_id`, and two rows naming one
file would resolve to one `File` row that only one of them could hold.
`ShippedBoardArtFilesTest` fails the build if a name is used twice, if a row names
art nobody committed or if a file here is named by no row.

## Sizes

| File | Its original | Size |
|------|--------------|------|
| `board5` | `board5/board5.jpg`, 2441x2164 | 1624x1440 |
| `board6` | `board6/board6.jpg`, 2400x1600 | 2160x1440 |
| `board7` | `board7/board7.jpg` | 1728x1201 |
| `board8` | `board8/board8.jpg` | 1000x667 |
| `board9` | `board9/board9.jpg` | 1300x827 |
| `board6-*` | `board6/*BIG.jpg`, 3283x4924 to 3803x5705 | 640x960 |
| `board7-*`, `board8-*`, `board9-*` | `board7..board9/*`, 600 wide | 600x814 – 600x928 |

Two board photos are narrower than 2560 because they are taller than 16x9, so
fitting them inside 2560x1440 is the height binding rather than the width. Three
are narrower because their originals are, and nothing is upscaled — boards 7, 8
and 9 were kept at web sizes long before this, and a larger copy would have to be
rescanned rather than recomputed.

Fifteen of the twenty-one portraits are 600 wide for the same reason, so their
ladder stops at 320 rather than 640. That is the art's limit, not the kind's:
board 6's six come from originals large enough to fill all three rungs, and a
portrait uploaded through the api reaches 640 as well.

## Rights

These are the association's own photographs of its own boards, taken by or for the
association and published on its own board page since the year each board sat.
Nothing here is publisher art and none of the notes in `db/seed/esports/art` apply.

Each photograph is of identifiable people who are or were members. Somebody asking
for their portrait to come off the page is asking for a row to be edited and a file
to be removed from here, which is an edit and a deploy — or a picture the board
replaces from the page itself.
