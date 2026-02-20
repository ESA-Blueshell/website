#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "$ROOT_DIR"

usage() {
  cat <<'USAGE'
Usage:
  scripts/merge-coverage.sh [options]

Merges backend JaCoCo XML reports and frontend Istanbul JSON reports into one coverage bundle.
If no inputs are provided, reports are auto-discovered from known local/CI paths.

Options:
  --jacoco <file-or-list>        JaCoCo XML file path or semicolon-separated list. Repeatable.
  --frontend-json <file-or-list> Frontend coverage-final.json path or semicolon-separated list. Repeatable.
  --out <dir>                    Output directory (default: coverage/merged)
  -h, --help                     Show help

Environment overrides:
  JACOCO_REPORTS                 Semicolon-separated JaCoCo XML paths.
  FRONTEND_COVERAGE_JSON_LIST    Semicolon-separated frontend coverage JSON paths.
  MERGED_COVERAGE_OUT            Output directory override.
USAGE
}

JACOCO_REPORT_PATHS=()
FRONTEND_JSON_PATHS=()
OUTPUT_DIR="${MERGED_COVERAGE_OUT:-coverage/merged}"

append_unique_jacoco() {
  local candidate="$1"
  local existing
  for existing in "${JACOCO_REPORT_PATHS[@]-}"; do
    if [[ "$existing" == "$candidate" ]]; then
      return 0
    fi
  done
  JACOCO_REPORT_PATHS+=("$candidate")
}

append_unique_frontend() {
  local candidate="$1"
  local existing
  for existing in "${FRONTEND_JSON_PATHS[@]-}"; do
    if [[ "$existing" == "$candidate" ]]; then
      return 0
    fi
  done
  FRONTEND_JSON_PATHS+=("$candidate")
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

collect_named_files() {
  local base_dir="$1"
  local filename="$2"
  local callback="$3"

  if [[ ! -d "$base_dir" ]]; then
    return 0
  fi

  while IFS= read -r found_file; do
    "$callback" "$found_file"
  done < <(find "$base_dir" -type f -name "$filename" -print | sort)
}

discover_jacoco_reports() {
  local source_dirs=(
    "coverage-inputs/api-test"
    "coverage-inputs/api-system"
    "coverage-inputs/api-combined"
    "coverage-inputs"
    "api/build/reports/jacoco"
  )
  local filenames=(
    "jacocoTestReport.xml"
    "jacocoSystemTestReport.xml"
    "jacocoCombinedReport.xml"
  )
  local dir=""
  local name=""

  for dir in "${source_dirs[@]-}"; do
    for name in "${filenames[@]-}"; do
      collect_named_files "$dir" "$name" append_unique_jacoco
    done
  done
}

discover_frontend_json_reports() {
  local source_dirs=(
    "coverage-inputs/api-system"
    "coverage-inputs/frontend-tests"
    "coverage-inputs/frontend-unit"
    "coverage-inputs"
    "api/build/coverage/frontend-system"
    "frontend/coverage/unit"
    "frontend/coverage"
  )
  local dir=""

  for dir in "${source_dirs[@]-}"; do
    collect_named_files "$dir" "coverage-final.json" append_unique_frontend
  done
}

maybe_convert_frontend_raw_coverage() {
  local raw_dir="api/build/coverage/frontend-system/raw"
  local out_dir="api/build/coverage/frontend-system"
  local out_file="${out_dir}/coverage-final.json"

  if [[ ${#FRONTEND_JSON_PATHS[@]} -gt 0 ]]; then
    return 0
  fi

  if [[ ! -d "$raw_dir" ]]; then
    return 0
  fi

  if [[ -f "$out_file" ]]; then
    append_unique_frontend "$out_file"
    return 0
  fi

  if ! command -v yarn >/dev/null 2>&1; then
    echo "Frontend raw coverage exists in '$raw_dir', but yarn is unavailable to convert it." >&2
    return 0
  fi

  echo "Converting frontend raw coverage from '$raw_dir'..."
  yarn --cwd frontend coverage:system:report \
    --raw-dir "../${raw_dir}" \
    --out-dir "../${out_dir}"

  if [[ -f "$out_file" ]]; then
    append_unique_frontend "$out_file"
  fi
}

validate_existing_files() {
  local label="$1"
  shift
  local report=""
  local has_missing=0
  for report in "$@"; do
    if [[ ! -f "$report" ]]; then
      echo "Missing ${label}: $report" >&2
      has_missing=1
    fi
  done
  if [[ "$has_missing" -ne 0 ]]; then
    exit 1
  fi
}

to_abs_path() {
  local input_path="$1"
  if [[ "$input_path" = /* ]]; then
    printf '%s\n' "$input_path"
    return 0
  fi

  printf '%s/%s\n' "$ROOT_DIR" "${input_path#./}"
}

join_with_semicolon() {
  local old_ifs="$IFS"
  IFS=';'
  printf '%s' "$*"
  IFS="$old_ifs"
}

if [[ -n "${JACOCO_REPORTS:-}" ]]; then
  append_split_values append_unique_jacoco "${JACOCO_REPORTS}"
fi
if [[ -n "${FRONTEND_COVERAGE_JSON_LIST:-}" ]]; then
  append_split_values append_unique_frontend "${FRONTEND_COVERAGE_JSON_LIST}"
fi

while [[ $# -gt 0 ]]; do
  case "$1" in
    --jacoco)
      append_split_values append_unique_jacoco "${2:-}"
      shift 2
      ;;
    --frontend-json|--frontend-jsons)
      append_split_values append_unique_frontend "${2:-}"
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

if [[ ${#JACOCO_REPORT_PATHS[@]} -eq 0 ]]; then
  discover_jacoco_reports
fi
if [[ ${#FRONTEND_JSON_PATHS[@]} -eq 0 ]]; then
  discover_frontend_json_reports
fi
maybe_convert_frontend_raw_coverage

if [[ ${#JACOCO_REPORT_PATHS[@]} -eq 0 ]]; then
  echo "No JaCoCo XML reports found." >&2
  echo "Expected in coverage-inputs/* or api/build/reports/jacoco/." >&2
  exit 1
fi
if [[ ${#FRONTEND_JSON_PATHS[@]} -eq 0 ]]; then
  echo "No frontend coverage JSON reports found." >&2
  echo "Expected in coverage-inputs/*, api/build/coverage/frontend-system/, or frontend/coverage/unit/." >&2
  exit 1
fi

validate_existing_files "JaCoCo report" "${JACOCO_REPORT_PATHS[@]}"
validate_existing_files "frontend coverage JSON" "${FRONTEND_JSON_PATHS[@]}"

if ! command -v yarn >/dev/null 2>&1; then
  echo "Yarn is required to run the Node-based coverage merger." >&2
  exit 1
fi

ABS_JACOCO_REPORTS=()
for report in "${JACOCO_REPORT_PATHS[@]-}"; do
  ABS_JACOCO_REPORTS+=("$(to_abs_path "$report")")
done
ABS_JACOCO_REPORTS_JOINED="$(join_with_semicolon "${ABS_JACOCO_REPORTS[@]}")"

ABS_FRONTEND_JSON_REPORTS=()
for report in "${FRONTEND_JSON_PATHS[@]-}"; do
  ABS_FRONTEND_JSON_REPORTS+=("$(to_abs_path "$report")")
done
ABS_FRONTEND_JSON_REPORTS_JOINED="$(join_with_semicolon "${ABS_FRONTEND_JSON_REPORTS[@]}")"

ABS_OUTPUT_DIR="$(to_abs_path "$OUTPUT_DIR")"

yarn --cwd frontend node ./scripts/merge-system-coverage.mjs \
  --jacoco "$ABS_JACOCO_REPORTS_JOINED" \
  --frontend-json "$ABS_FRONTEND_JSON_REPORTS_JOINED" \
  --out "$ABS_OUTPUT_DIR"

mkdir -p "$ABS_OUTPUT_DIR"
INPUT_MANIFEST="${ABS_OUTPUT_DIR}/merge-inputs.txt"
{
  echo "Merged coverage inputs"
  echo
  echo "JaCoCo XML reports:"
  for report in "${ABS_JACOCO_REPORTS[@]-}"; do
    echo "$report"
  done
  echo
  echo "Frontend coverage JSON reports:"
  for report in "${ABS_FRONTEND_JSON_REPORTS[@]-}"; do
    echo "$report"
  done
} > "$INPUT_MANIFEST"

echo "Merged coverage written to: $ABS_OUTPUT_DIR"
echo "Input manifest written to: $INPUT_MANIFEST"
