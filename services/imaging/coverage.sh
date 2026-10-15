#!/bin/sh
# Holds the unit suite to every statement, branch, condition and function; branches and conditions
# come from gobco.
set -eu

out=${1:-coverage}
mkdir -p "$out"

go test -count=1 -coverpkg=./internal/... -coverprofile="$out/coverage.out" ./internal/...
go tool cover -func="$out/coverage.out" >"$out/functions.txt"
statements=$(awk '/^total:/ { sub("%", "", $3); print $3 }' "$out/functions.txt")
functions=$(grep -vc '^total:' "$out/functions.txt")
functions_run=$(awk '!/^total:/ && $NF != "0.0%"' "$out/functions.txt" | wc -l | tr -d ' ')

# gobco cannot read cgo, so cgo packages may hold no branch.
for package in $(go list -f '{{if .CgoFiles}}{{.Dir}}{{end}}' ./internal/...); do
  for file in "$package"/*.go; do
    if sed -n '/^import "C"/,$p' "$file" | grep -En '^[[:space:]]*(if|for|switch|select)[[:space:]]|&&|\|\|'; then
      echo "$file decides something after its cgo import; move the decision to a package gobco reads" >&2
      exit 1
    fi
  done
done

gobco_total() {
  mode=$1
  shift
  taken=0
  total=0
  for package in $(go list -f '{{if not .CgoFiles}}{{.Dir}}{{end}}' ./internal/...); do
    # gobco wants a module-relative directory.
    report=$(go tool gobco "$@" "./${package#"$PWD"/}")
    echo "$report" >&2
    counted=$(echo "$report" | sed -n "s/^$mode coverage: \([0-9]*\)\/\([0-9]*\)\$/\1 \2/p")
    taken=$((taken + ${counted% *}))
    total=$((total + ${counted#* }))
  done
  echo "$taken $total"
}

# Assigned first, so a failed gobco run stops the script.
counted=$(gobco_total Branch -branch)
branches_taken=${counted% *}
branches=${counted#* }
counted=$(gobco_total Condition)
conditions_taken=${counted% *}
conditions=${counted#* }

printf '{"statements": %s, "branches": {"covered": %s, "total": %s}, "conditions": {"covered": %s, "total": %s}, "functions": {"covered": %s, "total": %s}}\n' \
  "$statements" "$branches_taken" "$branches" "$conditions_taken" "$conditions" "$functions_run" "$functions" >"$out/coverage-totals.json"
cat "$out/coverage-totals.json"

if [ "$statements" != "100.0" ] || [ "$branches_taken" != "$branches" ] ||
  [ "$conditions_taken" != "$conditions" ] || [ "$functions_run" != "$functions" ]; then
  echo "unit coverage must reach every statement, branch, condition and function" >&2
  exit 1
fi
