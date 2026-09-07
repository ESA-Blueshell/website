#!/usr/bin/env bash
#
# The island's one repeating pattern, cut from the source artwork.
#
# There is a single pattern and a single tile. What varies between bands is the wash over it and
# which half of the theme the band is in — never the pattern, which runs unbroken down the page.
#
# The tile is four steps across and four rows down, and each row is rolled sideways by its own
# fraction of a step: none, a half, a quarter, three quarters. Repeated, no row lines up with the
# one above it, so the pattern never resolves into columns the way a single half-step stagger
# does. It carries only the shell's silhouette, filled white, so CSS colours it through
# `mask-image` and the page owns the colour.
#
# Run from services/frontend: ./scripts/build-motif-tiles.sh
set -euo pipefail

cd "$(dirname "$0")/../src/assets/motif"

SOURCE=blueshell_motif_mono.png
OUT=motif-shell.webp

# The tile, at twice the width it is drawn at, so it stays sharp on a dense screen.
TILE_WIDTH=720
# How tall a step is against its width, as a percentage: under 100, so a run of shells has more
# room across than down.
STEP_RATIO=61
# How much of a step the shell fills, across and down.
FILL_X=48
FILL_Y=75
# How far each row is rolled sideways, as a percentage of a step.
ROW_OFFSETS=(0 50 25 75)

step_w=$((TILE_WIDTH / 4))
step_h=$((step_w * STEP_RATIO / 100))
fit_w=$((step_w * FILL_X / 100))
fit_h=$((step_h * FILL_Y / 100))

alpha="$(mktemp -t motif-alpha).png"
cell="$(mktemp -t motif-cell).png"
row="$(mktemp -t motif-row).png"
rows=()

# The silhouette, filled white, centred on a transparent step.
magick "$SOURCE" -alpha extract -filter Lanczos -resize "${fit_w}x${fit_h}" "$alpha"
magick -size "$(identify -format '%wx%h' "$alpha")" xc:white "$alpha" -alpha off \
  -compose copy_opacity -composite \
  -background none -gravity center -extent "${step_w}x${step_h}" "$cell"

# A row is that step four times over; each row is then rolled by its own offset.
magick "$cell" "$cell" "$cell" "$cell" +append "$row"
for offset in "${ROW_OFFSETS[@]}"; do
  rolled="$(mktemp -t motif-rolled).png"
  magick "$row" -roll "+$((step_w * offset / 100))+0" "$rolled"
  rows+=("$rolled")
done

magick "${rows[@]}" -append \
  -define webp:lossless=true -define webp:alpha-quality=100 "$OUT"

rm -f "$alpha" "$cell" "$row" "${rows[@]}"

printf '%s  %s  %s bytes  step %sx%s  shell %s\n' \
  "$OUT" "$(identify -format '%wx%h' "$OUT")" "$(wc -c <"$OUT" | tr -d ' ')" \
  "$step_w" "$step_h" \
  "$(magick "$OUT" -crop "${step_w}x${step_h}+0+0" +repage -trim -format '%wx%h' info:)"
