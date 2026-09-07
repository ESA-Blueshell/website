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
#   square     one shell in a square cell: the same room beside it as under it
#   wide       one shell in a cell 1.7 times as wide as it is tall, so shells have more room
#              beside them than under them, which is how a run of them reads best
#
# Run from services/frontend: ./scripts/build-motif-tiles.sh
set -euo pipefail

cd "$(dirname "$0")/../src/assets/motif"

# The variants, as <name>:<source stem>. A source's `_mono` cut is what the masks come from,
# because only its alpha matters; `_colour` is what the colour tiles come from.
VARIANTS=(
  "shell:blueshell_motif"
  "eyes:blueshell_motif_%s_eyes"
  "notext:blueshell_motif_%s_notext"
)

SQUARES=(50 100 150)
WIDES=(170 340)

# How much of a cell the artwork fills. Short of the edges, or the shells touch when they repeat.
SQUARE_FILL=62
WIDE_FILL_X=46
WIDE_FILL_Y=78

source_for() {
  local pattern="$1" cut="$2"
  if [[ "$pattern" == *"%s"* ]]; then
    # shellcheck disable=SC2059 # the pattern is ours, and names the cut's position
    printf "$pattern.png" "$cut"
  else
    printf "%s_%s.png" "$pattern" "$cut"
  fi
}

# The silhouette, filled white, on a transparent cell of the given size.
mask_tile() {
  local src="$1" width="$2" height="$3" fit_w="$4" fit_h="$5" out="$6"
  local alpha
  alpha="$(mktemp -t motif-alpha).png"
  magick "$src" -alpha extract -filter Lanczos -resize "${fit_w}x${fit_h}" "$alpha"
  magick -size "$(identify -format '%wx%h' "$alpha")" xc:white "$alpha" -alpha off \
    -compose copy_opacity -composite \
    -background none -gravity center -extent "${width}x${height}" \
    -define webp:lossless=true -define webp:alpha-quality=100 "$out"
  rm -f "$alpha"
}

# The artwork as it is, on a transparent cell of the given size.
colour_tile() {
  local src="$1" width="$2" height="$3" fit_w="$4" fit_h="$5" out="$6"
  magick "$src" -filter Lanczos -resize "${fit_w}x${fit_h}" \
    -background none -gravity center -extent "${width}x${height}" \
    -define webp:alpha-quality=100 -quality 92 "$out"
}

for variant in "${VARIANTS[@]}"; do
  name="${variant%%:*}"
  pattern="${variant#*:}"
  mono="$(source_for "$pattern" mono)"
  colour="$(source_for "$pattern" colour)"

  for size in "${SQUARES[@]}"; do
    fit=$((size * SQUARE_FILL / 100))
    mask_tile "$mono" "$size" "$size" "$fit" "$fit" "motif-${name}-mask-${size}.webp"
    colour_tile "$colour" "$size" "$size" "$fit" "$fit" "motif-${name}-colour-${size}.webp"
  done

  for width in "${WIDES[@]}"; do
    height=$((width * 100 / 170))
    fit_w=$((width * WIDE_FILL_X / 100))
    fit_h=$((height * WIDE_FILL_Y / 100))
    mask_tile "$mono" "$width" "$height" "$fit_w" "$fit_h" "motif-${name}-mask-wide-${width}.webp"
    colour_tile "$colour" "$width" "$height" "$fit_w" "$fit_h" "motif-${name}-colour-wide-${width}.webp"
  done
done

printf '%-40s %-12s %s\n' FILE SIZE BYTES
for f in motif-*.webp; do
  printf '%-40s %-12s %s\n' "$f" "$(identify -format '%wx%h' "$f")" "$(wc -c <"$f" | tr -d ' ')"
done
