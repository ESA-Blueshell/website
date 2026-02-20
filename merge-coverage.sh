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
  --frontend-json <file>  Path to frontend system-test coverage-final.json
  --frontend-test-json <file>  Path to frontend test-suite coverage-final.json (optional)
  --out <dir>             Output directory (default: coverage/merged)
  -h, --help              Show help

Environment overrides:
  JACOCO_TEST_XML
  JACOCO_SYSTEM_XML
  FRONTEND_COVERAGE_JSON       (frontend system-test coverage)
  FRONTEND_TEST_COVERAGE_JSON  (frontend test-suite coverage, optional)
  MERGED_COVERAGE_OUT
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
FRONTEND_SYSTEM_COVERAGE_JSON="${FRONTEND_COVERAGE_JSON:-}"
FRONTEND_TEST_COVERAGE_JSON="${FRONTEND_TEST_COVERAGE_JSON:-}"
OUTPUT_DIR="${MERGED_COVERAGE_OUT:-coverage/merged}"

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
      FRONTEND_SYSTEM_COVERAGE_JSON="${2:-}"
      shift 2
      ;;
    --frontend-test-json)
      FRONTEND_TEST_COVERAGE_JSON="${2:-}"
      shift 2
      ;;
    --out)
      OUTPUT_DIR="${2:-}"
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

if [[ -z "$FRONTEND_SYSTEM_COVERAGE_JSON" ]]; then
  FRONTEND_SYSTEM_COVERAGE_JSON="$(find_artifact_file "coverage-inputs/api-system" "coverage-final.json" || true)"
fi
if [[ -z "$FRONTEND_SYSTEM_COVERAGE_JSON" ]]; then
  FRONTEND_SYSTEM_COVERAGE_JSON="$(pick_first_existing_file "api/build/coverage/frontend-system/coverage-final.json" || true)"
fi

if [[ -z "$FRONTEND_TEST_COVERAGE_JSON" ]]; then
  FRONTEND_TEST_COVERAGE_JSON="$(find_artifact_file "coverage-inputs/frontend-tests" "coverage-final.json" || true)"
fi
if [[ -z "$FRONTEND_TEST_COVERAGE_JSON" ]]; then
  FRONTEND_TEST_COVERAGE_JSON="$(pick_first_existing_file "frontend/coverage/coverage-final.json" || true)"
fi

if [[ -z "$JACOCO_TEST_XML" || ! -f "$JACOCO_TEST_XML" ]]; then
  echo "Could not find jacocoTestReport.xml" >&2
  exit 1
fi

if [[ -z "$JACOCO_SYSTEM_XML" || ! -f "$JACOCO_SYSTEM_XML" ]]; then
  echo "Could not find jacocoSystemTestReport.xml" >&2
  exit 1
fi

if [[ -z "$FRONTEND_SYSTEM_COVERAGE_JSON" || ! -f "$FRONTEND_SYSTEM_COVERAGE_JSON" ]]; then
  echo "Could not find frontend system coverage-final.json" >&2
  exit 1
fi

if [[ -n "$FRONTEND_TEST_COVERAGE_JSON" && ! -f "$FRONTEND_TEST_COVERAGE_JSON" ]]; then
  echo "Configured frontend test coverage file does not exist: $FRONTEND_TEST_COVERAGE_JSON" >&2
  exit 1
fi

if [[ ! -x "scripts/merge-coverage.sh" ]]; then
  chmod +x "scripts/merge-coverage.sh"
fi

FRONTEND_COVERAGE_INPUTS=("$FRONTEND_SYSTEM_COVERAGE_JSON")
if [[ -n "$FRONTEND_TEST_COVERAGE_JSON" ]]; then
  FRONTEND_COVERAGE_INPUTS+=("$FRONTEND_TEST_COVERAGE_JSON")
fi
FRONTEND_COVERAGE_JSON_LIST="$(IFS=';'; echo "${FRONTEND_COVERAGE_INPUTS[*]}")"

./scripts/merge-coverage.sh \
  --jacoco "${JACOCO_TEST_XML};${JACOCO_SYSTEM_XML}" \
  --frontend-json "$FRONTEND_COVERAGE_JSON_LIST" \
  --out "$OUTPUT_DIR"

echo "Merged coverage written to: $OUTPUT_DIR"
