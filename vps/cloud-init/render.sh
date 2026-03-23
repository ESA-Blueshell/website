#!/usr/bin/env bash
set -euo pipefail

# render.sh — thin wrapper that calls the Python renderer.
#
# Kept for backwards compatibility (CI, ops scripts, README).
# The actual logic lives in tests/helpers/render.py.
#
# Usage:
#   ./render.sh
#
# Reads all values from vps/.env (or exported env vars).
# See tests/helpers/render.py for full documentation.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VPS_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

exec python3 -c "
import sys; sys.path.insert(0, '${VPS_DIR}')
from lib.render import main; main()
"
