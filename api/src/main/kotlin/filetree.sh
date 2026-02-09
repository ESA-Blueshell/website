#!/usr/bin/env bash
set -euo pipefail

# Usage:
#   ./filetree.sh [path] [depth] [output_file]
# Example:
#   ./filetree.sh . 8 filetree.txt

ROOT="${1:-.}"
DEPTH="${2:-8}"
OUT="${3:-filetree.txt}"

EXCLUDES=(
  ".git" ".idea" ".vscode"
  "target" "build" "out"
  ".gradle" ".mvn"
  "node_modules"
  ".DS_Store"
  "dist" "coverage" "logs"
)

sanitize_to_spaces() {
  # Replace UTF-8 NBSP (0xC2 0xA0) with normal space (0x20)
  # macOS sed supports hex escapes via printf piped through.
  perl -pe 's/\x{00A0}/ /g'
}

echo "Generating ASCII file tree for: $ROOT (depth=$DEPTH) -> $OUT"

if command -v tree >/dev/null 2>&1; then
  IGNORE_PATTERN="$(IFS='|'; echo "${EXCLUDES[*]}")"

  # --charset ascii ensures no Unicode box-drawing chars
  # --noreport removes the summary line
  # -a includes hidden files (excluding ignored patterns)
  tree -a --dirsfirst -L "$DEPTH" --charset ascii --noreport -I "$IGNORE_PATTERN" "$ROOT" \
    | sed "s|^$ROOT|.|" \
    | sanitize_to_spaces \
    > "$OUT"
else
  echo "Note: 'tree' not found. Using 'find' fallback (ASCII by nature)."
  echo "Tip: brew install tree"

  FIND_PRUNE=()
  for ex in "${EXCLUDES[@]}"; do
    FIND_PRUNE+=(-name "$ex" -o)
  done
  unset 'FIND_PRUNE[${#FIND_PRUNE[@]}-1]'

  {
    echo "."
    find "$ROOT" \
      \( "${FIND_PRUNE[@]}" \) -prune -o \
      -maxdepth "$DEPTH" \
      -print \
    | sed "s|^$ROOT|.|" \
    | sed 's|[^/]*/|  |g'
  } | sanitize_to_spaces > "$OUT"
fi

echo "Done. Preview (first 60 lines):"
echo "--------------------------------"
head -n 60 "$OUT"
