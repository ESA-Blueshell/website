#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

usage() {
  cat <<'USAGE'
Usage:
  ./merge-coverage.sh [options]

Finds CI-style coverage inputs and runs scripts/merge-coverage.sh.

Options:
  --jacoco-test <file>    Path to jacocoTestReport.xml
  --jacoco-system <file>  Path to jacocoSystemTestReport.xml
  --frontend-json <file>  Path to frontend coverage-final.json
  --out <dir>             Output directory (default: coverage/merged)
  --backend-prefix <path> Backend prefix (default: api/src/main/kotlin/net/blueshell/api)
  -h, --help              Show help

Environment overrides:
  JACOCO_TEST_XML
  JACOCO_SYSTEM_XML
  FRONTEND_COVERAGE_JSON
  MERGED_COVERAGE_OUT
  BACKEND_PREFIX
USAGE
}

find_artifact_file() {
  local base_dir="$1"
  local file_name="$2"

  if [[ ! -d "$base_dir" ]]; then
    return 1
  fi

  find "$base_dir" -name "$file_name" -print -quit
}

pick_first_existing_file() {
  for candidate in "$@"; do
    if [[ -f "$candidate" ]]; then
      printf '%s\n' "$candidate"
      return 0
    fi
  done
  return 1
}

JACOCO_TEST_XML="${JACOCO_TEST_XML:-}"
JACOCO_SYSTEM_XML="${JACOCO_SYSTEM_XML:-}"
FRONTEND_COVERAGE_JSON="${FRONTEND_COVERAGE_JSON:-}"
OUTPUT_DIR="${MERGED_COVERAGE_OUT:-coverage/merged}"
BACKEND_PREFIX="${BACKEND_PREFIX:-api/src/main/kotlin/net/blueshell/api}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --jacoco-test)
      JACOCO_TEST_XML="${2:-}"
      shift 2
      ;;
    --jacoco-system)
      JACOCO_SYSTEM_XML="${2:-}"
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

if [[ -z "$JACOCO_TEST_XML" ]]; then
  JACOCO_TEST_XML="$(find_artifact_file "coverage-inputs/api-test" "jacocoTestReport.xml" || true)"
fi
if [[ -z "$JACOCO_TEST_XML" ]]; then
  JACOCO_TEST_XML="$(pick_first_existing_file "api/build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml" || true)"
fi

if [[ -z "$JACOCO_SYSTEM_XML" ]]; then
  JACOCO_SYSTEM_XML="$(find_artifact_file "coverage-inputs/api-system" "jacocoSystemTestReport.xml" || true)"
fi
if [[ -z "$JACOCO_SYSTEM_XML" ]]; then
  JACOCO_SYSTEM_XML="$(pick_first_existing_file "api/build/reports/jacoco/jacocoSystemTestReport/jacocoSystemTestReport.xml" || true)"
fi

if [[ -z "$FRONTEND_COVERAGE_JSON" ]]; then
  FRONTEND_COVERAGE_JSON="$(find_artifact_file "coverage-inputs/api-system" "coverage-final.json" || true)"
fi
if [[ -z "$FRONTEND_COVERAGE_JSON" ]]; then
  FRONTEND_COVERAGE_JSON="$(pick_first_existing_file "api/build/coverage/frontend-system/coverage-final.json" || true)"
fi

if [[ -z "$JACOCO_TEST_XML" || ! -f "$JACOCO_TEST_XML" ]]; then
  echo "Could not find jacocoTestReport.xml" >&2
  exit 1
fi

if [[ -z "$JACOCO_SYSTEM_XML" || ! -f "$JACOCO_SYSTEM_XML" ]]; then
  echo "Could not find jacocoSystemTestReport.xml" >&2
  exit 1
fi

if [[ -z "$FRONTEND_COVERAGE_JSON" || ! -f "$FRONTEND_COVERAGE_JSON" ]]; then
  echo "Could not find coverage-final.json" >&2
  exit 1
fi

if [[ ! -x "scripts/merge-coverage.sh" ]]; then
  chmod +x "scripts/merge-coverage.sh"
fi

./scripts/merge-coverage.sh \
  --jacoco "${JACOCO_TEST_XML};${JACOCO_SYSTEM_XML}" \
  --frontend-json "$FRONTEND_COVERAGE_JSON" \
  --out "$OUTPUT_DIR" \
  --backend-prefix "$BACKEND_PREFIX"

echo "Merged coverage written to: $OUTPUT_DIR"
