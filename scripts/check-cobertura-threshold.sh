#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat <<'USAGE'
Usage:
  scripts/check-cobertura-threshold.sh --file <cobertura.xml> --min-line-rate <0..1>

Arguments:
  --file            Path to Cobertura XML file.
  --min-line-rate   Minimum accepted line-rate (fraction between 0 and 1).
USAGE
}

COBERTURA_FILE=""
MIN_LINE_RATE=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --file)
      COBERTURA_FILE="${2:-}"
      shift 2
      ;;
    --min-line-rate)
      MIN_LINE_RATE="${2:-}"
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

if [[ -z "$COBERTURA_FILE" || -z "$MIN_LINE_RATE" ]]; then
  usage
  exit 2
fi

if [[ ! -f "$COBERTURA_FILE" ]]; then
  echo "Cobertura file not found: $COBERTURA_FILE" >&2
  exit 1
fi

if ! [[ "$MIN_LINE_RATE" =~ ^(0(\.[0-9]+)?|1(\.0+)?)$ ]]; then
  echo "Invalid --min-line-rate value: $MIN_LINE_RATE" >&2
  echo "Expected a number between 0 and 1." >&2
  exit 2
fi

ACTUAL_LINE_RATE="$(
  sed -n '/line-rate="/{s/.*line-rate="\([0-9.]*\)".*/\1/p;q;}' "$COBERTURA_FILE"
)"

if [[ -z "$ACTUAL_LINE_RATE" ]]; then
  echo "Could not extract line-rate from: $COBERTURA_FILE" >&2
  exit 1
fi

if awk -v actual="$ACTUAL_LINE_RATE" -v minimum="$MIN_LINE_RATE" 'BEGIN {exit !(actual + 0 >= minimum + 0)}'; then
  printf 'Coverage threshold passed: line-rate=%s >= minimum=%s\n' "$ACTUAL_LINE_RATE" "$MIN_LINE_RATE"
  exit 0
fi

printf 'Coverage threshold failed: line-rate=%s < minimum=%s\n' "$ACTUAL_LINE_RATE" "$MIN_LINE_RATE" >&2
exit 1
