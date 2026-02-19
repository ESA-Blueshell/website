#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat <<'USAGE'
Usage:
  scripts/merge-coverage.sh --jacoco <jacoco-xml-list> --frontend-json <coverage-final.json> --out <output-dir>

Arguments:
  --jacoco         Semicolon-separated list of JaCoCo XML paths.
  --frontend-json  Path to frontend Istanbul coverage JSON (coverage-final.json).
  --out            Output directory for merged reports.
  --backend-prefix Backend file prefix to keep (default: api/src/main/kotlin/net/blueshell/api).
USAGE
}

JACOCO_REPORTS=""
FRONTEND_COVERAGE_JSON=""
OUTPUT_DIR=""
BACKEND_PREFIX="api/src/main/kotlin/net/blueshell/api"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --jacoco)
      JACOCO_REPORTS="${2:-}"
      shift 2
      ;;
    --frontend-json)
      FRONTEND_COVERAGE_JSON="${2:-}"
      shift 2
      ;;
    --out)
      OUTPUT_DIR="${2:-}"
      shift 2
      ;;
    --backend-prefix)
      BACKEND_PREFIX="${2:-}"
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

if [[ -z "$JACOCO_REPORTS" || -z "$FRONTEND_COVERAGE_JSON" || -z "$OUTPUT_DIR" ]]; then
  usage
  exit 2
fi

IFS=';' read -r -a jacoco_array <<< "$JACOCO_REPORTS"
for report in "${jacoco_array[@]}"; do
  if [[ ! -f "$report" ]]; then
    echo "Missing JaCoCo report: $report" >&2
    exit 1
  fi
done

if [[ ! -f "$FRONTEND_COVERAGE_JSON" ]]; then
  echo "Missing frontend coverage JSON: $FRONTEND_COVERAGE_JSON" >&2
  exit 1
fi

mkdir -p "$OUTPUT_DIR"

if ! command -v yarn >/dev/null 2>&1; then
  echo "Yarn is required to run the Node-based coverage merger." >&2
  exit 1
fi

to_abs_path() {
  local path_input="$1"
  if [[ "$path_input" = /* ]]; then
    echo "$path_input"
    return 0
  fi
  echo "$(pwd -P)/${path_input#./}"
}

ABS_JACOCO_REPORTS=()
for report in "${jacoco_array[@]}"; do
  ABS_JACOCO_REPORTS+=("$(to_abs_path "$report")")
done
ABS_JACOCO_REPORTS_JOINED="$(IFS=';'; echo "${ABS_JACOCO_REPORTS[*]}")"
ABS_FRONTEND_COVERAGE_JSON="$(to_abs_path "$FRONTEND_COVERAGE_JSON")"
ABS_OUTPUT_DIR="$(to_abs_path "$OUTPUT_DIR")"

yarn --cwd frontend node ./scripts/merge-system-coverage.mjs \
  --jacoco "$ABS_JACOCO_REPORTS_JOINED" \
  --frontend-json "$ABS_FRONTEND_COVERAGE_JSON" \
  --out "$ABS_OUTPUT_DIR" \
  --backend-prefix "$BACKEND_PREFIX"
