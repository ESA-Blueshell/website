#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat <<'USAGE'
Usage:
  scripts/generate-policy-pdfs.sh [--dir <policy-dir>] [--image <docker-image>]

Options:
  --dir    Directory containing policy markdown files (.md).
           Default: docs/policies
  --image  Docker image used for Pandoc + LaTeX.
           Default: pandoc/latex:3.1
  -h, --help
USAGE
}

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
POLICY_DIR_REL="docs/policies"
IMAGE="pandoc/latex:3.1"
BR_FILTER_REL="scripts/pandoc-html-br.lua"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dir)
      POLICY_DIR_REL="${2:-}"
      shift 2
      ;;
    --image)
      IMAGE="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage
      exit 2
      ;;
  esac
done

if [[ "$POLICY_DIR_REL" = /* ]]; then
  POLICY_DIR_ABS="$POLICY_DIR_REL"
else
  POLICY_DIR_ABS="$ROOT_DIR/$POLICY_DIR_REL"
fi

if [[ ! -d "$POLICY_DIR_ABS" ]]; then
  echo "Policy directory not found: $POLICY_DIR_ABS" >&2
  exit 1
fi

BR_FILTER_ABS="$ROOT_DIR/$BR_FILTER_REL"
if [[ ! -f "$BR_FILTER_ABS" ]]; then
  echo "Pandoc BR filter not found: $BR_FILTER_ABS" >&2
  exit 1
fi

POLICY_FILES=()
while IFS= read -r file_path; do
  POLICY_FILES+=("$file_path")
done < <(find "$POLICY_DIR_ABS" -maxdepth 1 -type f -name '*.md' | sort)

if [[ ${#POLICY_FILES[@]} -eq 0 ]]; then
  echo "No markdown policy files found in: $POLICY_DIR_ABS" >&2
  exit 1
fi

echo "Generating PDFs for ${#POLICY_FILES[@]} markdown file(s) from: $POLICY_DIR_ABS"

for md_abs in "${POLICY_FILES[@]}"; do
  md_rel="${md_abs#$ROOT_DIR/}"
  pdf_rel="${md_rel%.md}.pdf"

  echo " - $pdf_rel"
  docker run --rm \
    -v "$ROOT_DIR":/work \
    -w /work \
    "$IMAGE" \
    "$md_rel" \
    -o "$pdf_rel" \
    --lua-filter="$BR_FILTER_REL" \
    --pdf-engine=xelatex \
    -V geometry:"left=0.55in,right=0.55in,top=1in,bottom=1in" \
    -V fontsize=11pt \
    -V linestretch=1.15 \
    -V colorlinks=true \
    -V linkcolor=blue
done

echo "Done."
