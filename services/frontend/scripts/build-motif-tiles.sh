#!/usr/bin/env bash
#
# Repeating tiles for the island's motif grounds, from the source artwork.
#
# The source art is thousands of pixels wide and no use to a browser. This cuts it down to the
# sizes a page repeats, in two shapes and two kinds:
#
#   mask       the silhouette only, taken from the artwork's alpha and filled white, so CSS
#              colours it through `mask-image` and the page owns the colour
#   colour     the artwork's own colours, for a ground that is not being tinted
#   even       shells spaced the same across as down
#   wide       shells spaced further across than down, which is how a run of them reads best
#
# Every tile carries two shells, one in each of two rows, the second half a step across from the
# first. Repeated, that staggers the rows against each other instead of lining them up in a
# grid. So a tile is two steps wide and two steps tall, and the step is what the spacing is.
#
# Run from services/frontend: ./scripts/build-motif-tiles.sh
set -euo pipefail

cd "$(dirname "$0")/../src/assets/motif"

# The variants, as <name>:<source stem>:<square fill>:<wide fill across>:<wide fill down>.
#
# A source's `_mono` cut is what the masks come from, because only its alpha matters; `_colour`
# is what the colour tiles come from. The fills are how much of a cell the artwork takes up,
# and they are per variant because the variants are not the same shape: the mascot is nearly
# twice as wide as it is tall, so a fill that suits the shell leaves it drawn half the height
# and reading as the smaller mark. Its fills are raised until it carries the same weight.
VARIANTS=(
  "shell:blueshell_motif:30:26:46"
  "eyes:blueshell_motif_%s_eyes:30:26:46"
  "notext:blueshell_motif_%s_notext:40:38:60"
)

# The width of a whole tile, which is two steps across.
EVENS=(100 200 300)
WIDES=(340 680)

# How much taller a step is than it is wide, as a percentage: 62 makes the space across a run
# half again the space down it.
WIDE_STEP_RATIO=62

source_for() {
  local pattern="$1" cut="$2"
  if [[ "$pattern" == *"%s"* ]]; then
    # shellcheck disable=SC2059 # the pattern is ours, and names the cut's position
    printf "$pattern.png" "$cut"
  else
    printf "%s_%s.png" "$pattern" "$cut"
  fi
}

# One shell on a transparent step, then two steps staggered into the tile that repeats.
#
# `draw` is what puts the artwork on a step; the tile is that step twice over, once at the top
# left and once half a step across and a step down, with the rest left clear.
tile_from() {
  local draw="$1" src="$2" step_w="$3" step_h="$4" fit_w="$5" fit_h="$6" out="$7"
  local cell blank row_top row_foot
  cell="$(mktemp -t motif-cell).png"
  blank="$(mktemp -t motif-blank).png"
  row_top="$(mktemp -t motif-top).png"
  row_foot="$(mktemp -t motif-foot).png"

  "$draw" "$src" "$step_w" "$step_h" "$fit_w" "$fit_h" "$cell"
  magick -size "${step_w}x${step_h}" xc:none "$blank"
  magick "$cell" "$blank" +append "$row_top"
  magick "$blank" "$cell" +append "$row_foot"
  magick "$row_top" "$row_foot" -append \
    -define webp:lossless=true -define webp:alpha-quality=100 "$out"

  rm -f "$cell" "$blank" "$row_top" "$row_foot"
}

# The silhouette, filled white, on a transparent step of the given size.
draw_mask() {
  local src="$1" width="$2" height="$3" fit_w="$4" fit_h="$5" out="$6"
  local alpha
  alpha="$(mktemp -t motif-alpha).png"
  magick "$src" -alpha extract -filter Lanczos -resize "${fit_w}x${fit_h}" "$alpha"
  magick -size "$(identify -format '%wx%h' "$alpha")" xc:white "$alpha" -alpha off \
    -compose copy_opacity -composite \
    -background none -gravity center -extent "${width}x${height}" "$out"
  rm -f "$alpha"
}

# The artwork as it is, on a transparent step of the given size.
draw_colour() {
  local src="$1" width="$2" height="$3" fit_w="$4" fit_h="$5" out="$6"
  magick "$src" -filter Lanczos -resize "${fit_w}x${fit_h}" \
    -background none -gravity center -extent "${width}x${height}" "$out"
}

for variant in "${VARIANTS[@]}"; do
  IFS=: read -r name pattern even_fill wide_fill_x wide_fill_y <<<"$variant"
  mono="$(source_for "$pattern" mono)"
  colour="$(source_for "$pattern" colour)"

  for width in "${EVENS[@]}"; do
    step=$((width / 2))
    fit=$((step * even_fill / 100))
    tile_from draw_mask "$mono" "$step" "$step" "$fit" "$fit" "motif-${name}-mask-${width}.webp"
    tile_from draw_colour "$colour" "$step" "$step" "$fit" "$fit" "motif-${name}-colour-${width}.webp"
  done

  for width in "${WIDES[@]}"; do
    step_w=$((width / 2))
    step_h=$((step_w * WIDE_STEP_RATIO / 100))
    fit_w=$((step_w * wide_fill_x / 100))
    fit_h=$((step_h * wide_fill_y / 100))
    tile_from draw_mask "$mono" "$step_w" "$step_h" "$fit_w" "$fit_h" \
      "motif-${name}-mask-wide-${width}.webp"
    tile_from draw_colour "$colour" "$step_w" "$step_h" "$fit_w" "$fit_h" \
      "motif-${name}-colour-wide-${width}.webp"
  done
done

printf '%-40s %-12s %s\n' FILE SIZE BYTES
for f in motif-*.webp; do
  printf '%-40s %-12s %s\n' "$f" "$(identify -format '%wx%h' "$f")" "$(wc -c <"$f" | tr -d ' ')"
done
