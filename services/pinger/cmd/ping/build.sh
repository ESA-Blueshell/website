#!/usr/bin/env bash
# Cross-compile the helper into dist/ for the common platforms, to hand to association members.
set -euo pipefail
cd "$(dirname "$0")/../.."

out=dist
mkdir -p "$out"
build() {
  GOOS="$1" GOARCH="$2" CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o "$out/$3" ./cmd/ping
  echo "built $out/$3"
}

build windows amd64 blueshell-pinger-windows-amd64.exe
build darwin  amd64 blueshell-pinger-macos-intel
build darwin  arm64 blueshell-pinger-macos-apple
build linux   amd64 blueshell-pinger-linux-amd64
build linux   arm64 blueshell-pinger-linux-arm64
