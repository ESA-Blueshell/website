#!/bin/sh
# The unit suite, held to every statement, every branch and every function, with the totals written
# beside the profile for the pull request report. The image's test stage runs it; so can a host
# with libwebp installed. Go measures only statements, so branches come from gobco and functions
# from the profile itself.
set -eu

out=${1:-coverage}
mkdir -p "$out"

go test -count=1 -coverpkg=./internal/... -coverprofile="$out/coverage.out" ./internal/...
go tool cover -func="$out/coverage.out" >"$out/functions.txt"
statements=$(awk '/^total:/ { sub("%", "", $3); print $3 }' "$out/functions.txt")
functions=$(grep -vc '^total:' "$out/functions.txt")
functions_run=$(awk '!/^total:/ && $NF != "0.0%"' "$out/functions.txt" | wc -l | tr -d ' ')

# gobco cannot read a cgo file, so the packages that call libwebp hold no decision of their own:
# every branch is in Go it can instrument, and this refuses one written there.
for package in $(go list -f '{{if .CgoFiles}}{{.Dir}}{{end}}' ./internal/...); do
  for file in "$package"/*.go; do
    if sed -n '/^import "C"/,$p' "$file" | grep -En '^[[:space:]]*(if|for|switch|select)[[:space:]]|&&|\|\|'; then
      echo "$file decides something after its cgo import; move the decision to a package gobco reads" >&2
      exit 1
    fi
  done
done

branches=0
branches_taken=0
for package in $(go list -f '{{if not .CgoFiles}}{{.Dir}}{{end}}' ./internal/...); do
  # gobco takes a directory relative to the module, not an import path.
  report=$(go tool gobco -branch "./${package#"$PWD"/}")
  echo "$report"
  counted=$(echo "$report" | sed -n 's/^Branch coverage: \([0-9]*\)\/\([0-9]*\)$/\1 \2/p')
  branches_taken=$((branches_taken + ${counted% *}))
  branches=$((branches + ${counted#* }))
done

printf '{"statements": %s, "branches": {"covered": %s, "total": %s}, "functions": {"covered": %s, "total": %s}}\n' \
  "$statements" "$branches_taken" "$branches" "$functions_run" "$functions" >"$out/coverage-totals.json"
cat "$out/coverage-totals.json"

if [ "$statements" != "100.0" ] || [ "$branches_taken" != "$branches" ] || [ "$functions_run" != "$functions" ]; then
  echo "unit coverage must reach every statement, branch and function" >&2
  exit 1
fi
