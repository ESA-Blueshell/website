#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

usage() {
  cat <<'USAGE'
Usage:
  ./merge-coverage.sh [options]

Compatibility wrapper around scripts/merge-coverage.sh.
If no explicit inputs are provided, coverage reports are auto-discovered.

Options:
  --jacoco <file-or-list>         JaCoCo XML path or semicolon-separated list. Repeatable.
  --jacoco-test <file>            Backward-compatible alias for JaCoCo unit/integration XML.
  --jacoco-system <file>          Backward-compatible alias for JaCoCo system XML.
  --jacoco-combined <file>        Optional JaCoCo combined XML input.
  --frontend-json <file-or-list>  Frontend coverage-final.json path or semicolon-separated list. Repeatable.
  --frontend-system-json <file>   Backward-compatible alias for frontend system coverage JSON.
  --frontend-test-json <file>     Backward-compatible alias for frontend unit-test coverage JSON.
  --out <dir>                     Output directory (default: coverage/merged)
  -h, --help                      Show help

Environment overrides:
  JACOCO_REPORTS
  JACOCO_TEST_XML
  JACOCO_SYSTEM_XML
  JACOCO_COMBINED_XML
  FRONTEND_COVERAGE_JSON_LIST
  FRONTEND_COVERAGE_JSON
  FRONTEND_SYSTEM_COVERAGE_JSON
  FRONTEND_TEST_COVERAGE_JSON
  MERGED_COVERAGE_OUT
USAGE
}

JACOCO_INPUTS=()
FRONTEND_INPUTS=()
OUTPUT_DIR="${MERGED_COVERAGE_OUT:-coverage/merged}"

append_unique_jacoco() {
  local candidate="$1"
  local existing
  for existing in "${JACOCO_INPUTS[@]-}"; do
    if [[ "$existing" == "$candidate" ]]; then
      return 0
    fi
  done
  JACOCO_INPUTS+=("$candidate")
}

append_unique_frontend() {
  local candidate="$1"
  local existing
  for existing in "${FRONTEND_INPUTS[@]-}"; do
    if [[ "$existing" == "$candidate" ]]; then
      return 0
    fi
  done
  FRONTEND_INPUTS+=("$candidate")
}

append_split_values() {
  local callback="$1"
  local raw_values="$2"
  local split_values=()
  local value=""
  local trimmed=""
  local old_ifs="$IFS"

  IFS=';'
  read -r -a split_values <<< "$raw_values"
  IFS="$old_ifs"

  for value in "${split_values[@]-}"; do
    trimmed="${value#"${value%%[![:space:]]*}"}"
    trimmed="${trimmed%"${trimmed##*[![:space:]]}"}"
    if [[ -n "$trimmed" ]]; then
      "$callback" "$trimmed"
    fi
  done
}

if [[ -n "${JACOCO_REPORTS:-}" ]]; then
  append_split_values append_unique_jacoco "${JACOCO_REPORTS}"
fi
if [[ -n "${JACOCO_TEST_XML:-}" ]]; then
  append_unique_jacoco "${JACOCO_TEST_XML}"
fi
if [[ -n "${JACOCO_SYSTEM_XML:-}" ]]; then
  append_unique_jacoco "${JACOCO_SYSTEM_XML}"
fi
if [[ -n "${JACOCO_COMBINED_XML:-}" ]]; then
  append_unique_jacoco "${JACOCO_COMBINED_XML}"
fi
if [[ -n "${FRONTEND_COVERAGE_JSON_LIST:-}" ]]; then
  append_split_values append_unique_frontend "${FRONTEND_COVERAGE_JSON_LIST}"
fi
if [[ -n "${FRONTEND_COVERAGE_JSON:-}" ]]; then
  append_unique_frontend "${FRONTEND_COVERAGE_JSON}"
fi
if [[ -n "${FRONTEND_SYSTEM_COVERAGE_JSON:-}" ]]; then
  append_unique_frontend "${FRONTEND_SYSTEM_COVERAGE_JSON}"
fi
if [[ -n "${FRONTEND_TEST_COVERAGE_JSON:-}" ]]; then
  append_unique_frontend "${FRONTEND_TEST_COVERAGE_JSON}"
fi

while [[ $# -gt 0 ]]; do
  case "$1" in
    --jacoco)
      append_split_values append_unique_jacoco "${2:-}"
      shift 2
      ;;
    --jacoco-test)
      append_unique_jacoco "${2:-}"
      shift 2
      ;;
    --jacoco-system)
      append_unique_jacoco "${2:-}"
      shift 2
      ;;
    --jacoco-combined)
      append_unique_jacoco "${2:-}"
      shift 2
      ;;
    --frontend-json|--frontend-system-json)
      append_split_values append_unique_frontend "${2:-}"
      shift 2
      ;;
    --frontend-test-json)
      append_unique_frontend "${2:-}"
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

if [[ ! -x "scripts/merge-coverage.sh" ]]; then
  chmod +x "scripts/merge-coverage.sh"
fi

cmd=(./scripts/merge-coverage.sh --out "$OUTPUT_DIR")
for report in "${JACOCO_INPUTS[@]-}"; do
  cmd+=(--jacoco "$report")
done
for report in "${FRONTEND_INPUTS[@]-}"; do
  cmd+=(--frontend-json "$report")
done

"${cmd[@]}"
