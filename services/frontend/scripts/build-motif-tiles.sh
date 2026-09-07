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
# A tile is four steps wide and four rows tall, and each row is rolled sideways by a different
# fraction of a step: none, a half, a quarter, three quarters. Repeated, no row lines up with
# the one above it and the pattern never settles into columns, which a single half-step stagger
# still does every other row. The step is what the spacing is.
#
# Run from services/frontend: ./scripts/build-motif-tiles.sh
set -euo pipefail

cd "$(dirname "$0")/../src/assets/motif"

# The variants, as <name>:<source stem>:<even fill>:<wide fill across>:<wide fill down>.
#
# A source's `_mono` cut is what the masks come from, because only its alpha matters; `_colour`
# is what the colour tiles come from. The fills are how much of a cell the artwork takes up,
# and they are per variant because the variants are not the same shape. What is held level is
# the height each is drawn at: fitted by width, the mascot came out half the height of the
# shell and read as a different size of mark. Each variant's fills are set so all three stand
# the same height in the same cell, whatever their width does.
VARIANTS=(
  "shell:blueshell_motif:52:55:75"
  "eyes:blueshell_motif_%s_eyes:47:50:72"
  "notext:blueshell_motif_%s_notext:78:86:72"
)

# The width of a whole tile, which is four steps across.
EVENS=(200 400 600)
WIDES=(360 720)

# How tall a step is against its width, as a percentage: under 100, so a run of shells has more
# room across than down.
WIDE_STEP_RATIO=67

# How far each row is rolled sideways, as a percentage of a step. Four rows, four offsets, none
# of them a repeat of another.
ROW_OFFSETS=(0 50 25 75)

source_for() {
  local pattern="$1" cut="$2"
  if [[ "$pattern" == *"%s"* ]]; then
    # shellcheck disable=SC2059 # the pattern is ours, and names the cut's position
    printf "$pattern.png" "$cut"
  else
    printf "%s_%s.png" "$pattern" "$cut"
  fi
}

# One shell on a transparent step, then four rows of them rolled past each other into the tile.
#
# `draw` is what puts the artwork on a step. A row is that step four times over; each row is
# then rolled sideways by its own fraction of a step, which is what stops the rows lining up.
tile_from() {
  local draw="$1" src="$2" step_w="$3" step_h="$4" fit_w="$5" fit_h="$6" out="$7"
  local cell row rows=()
  cell="$(mktemp -t motif-cell).png"
  row="$(mktemp -t motif-row).png"

  "$draw" "$src" "$step_w" "$step_h" "$fit_w" "$fit_h" "$cell"
  magick "$cell" "$cell" "$cell" "$cell" +append "$row"

  for offset in "${ROW_OFFSETS[@]}"; do
    local rolled
    rolled="$(mktemp -t motif-rolled).png"
    magick "$row" -roll "+$((step_w * offset / 100))+0" "$rolled"
    rows+=("$rolled")
  done

  magick "${rows[@]}" -append \
    -define webp:lossless=true -define webp:alpha-quality=100 "$out"

  rm -f "$cell" "$row" "${rows[@]}"
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
    step=$((width / 4))
    fit=$((step * even_fill / 100))
    tile_from draw_mask "$mono" "$step" "$step" "$fit" "$fit" "motif-${name}-mask-${width}.webp"
    tile_from draw_colour "$colour" "$step" "$step" "$fit" "$fit" "motif-${name}-colour-${width}.webp"
  done

  for width in "${WIDES[@]}"; do
    step_w=$((width / 4))
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
