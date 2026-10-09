#!/usr/bin/env bash
# Cross-compile the helper into dist/ for the common platforms, to hand to association members.
# VERSION (such as 1.19.1) goes into the Windows version info; it defaults to 0.0.0.
set -euo pipefail
cd "$(dirname "$0")/../.."

out=dist
version="${VERSION:-0.0.0}"
mkdir -p "$out"
build() {
  GOOS="$1" GOARCH="$2" CGO_ENABLED=0 go build -trimpath -ldflags="$4" -o "$out/$3" ./cmd/ping
  echo "built $out/$3"
}

# The Windows build carries version info and a manifest, and keeps its symbols: antivirus machine
# learning scores an anonymous stripped binary as packed, and a raw-ICMP sender is suspect enough.
trap 'rm -f cmd/ping/rsrc_windows_*.syso' EXIT
go run github.com/tc-hib/go-winres@v0.3.3 make --in cmd/ping/winres/winres.json \
  --out cmd/ping/rsrc --arch amd64 --product-version "$version.0" --file-version "$version.0"
build windows amd64 blueshell-pinger-windows-amd64.exe ""

build darwin  amd64 blueshell-pinger-macos-intel  "-s -w"
build darwin  arm64 blueshell-pinger-macos-apple  "-s -w"
build linux   amd64 blueshell-pinger-linux-amd64  "-s -w"
build linux   arm64 blueshell-pinger-linux-arm64  "-s -w"
