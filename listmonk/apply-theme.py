#!/usr/bin/env python3
"""
Apply the ESA Blueshell custom theme to Listmonk.

Reads theme.css, fetches the current Listmonk settings via the API,
injects the CSS into app.custom_css, and writes the merged settings back.
Idempotent: re-running simply overwrites the CSS with the latest version.

Environment variables
---------------------
LISTMONK_URL              Base URL of the Listmonk instance (default: http://listmonk:9000)
LISTMONK_ADMIN_USERNAME   Admin username (default: listmonk)
LISTMONK_ADMIN_PASSWORD   Admin password (default: listmonk)
THEME_CSS_PATH            Path to the CSS file to inject (default: /theme.css)
"""
import json
import os
import sys
import urllib.error
import urllib.request
from base64 import b64encode

LISTMONK_URL = os.environ.get("LISTMONK_URL", "http://listmonk:9000")
ADMIN_USER = os.environ.get("LISTMONK_ADMIN_USERNAME", "listmonk")
ADMIN_PASS = os.environ.get("LISTMONK_ADMIN_PASSWORD", "listmonk")
THEME_CSS_PATH = os.environ.get("THEME_CSS_PATH", "/theme.css")


def _auth_headers() -> dict:
    token = b64encode(f"{ADMIN_USER}:{ADMIN_PASS}".encode()).decode()
    return {
        "Authorization": f"Basic {token}",
        "Content-Type": "application/json",
    }


def api_get(path: str) -> dict:
    req = urllib.request.Request(
        f"{LISTMONK_URL}{path}",
        headers=_auth_headers(),
    )
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read())


def api_put(path: str, body: dict) -> dict:
    data = json.dumps(body).encode()
    req = urllib.request.Request(
        f"{LISTMONK_URL}{path}",
        data=data,
        headers=_auth_headers(),
        method="PUT",
    )
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read())


def main() -> None:
    with open(THEME_CSS_PATH) as f:
        css = f.read()

    print(f"Fetching Listmonk settings from {LISTMONK_URL} …")
    try:
        response = api_get("/api/settings")
    except urllib.error.HTTPError as exc:
        print(f"Failed to fetch settings: HTTP {exc.code} – {exc.reason}", file=sys.stderr)
        sys.exit(1)

    # Listmonk wraps all responses in {"data": {...}}
    settings = response.get("data", response)

    if "app" not in settings:
        print(
            "Unexpected settings structure – 'app' key not found.\n"
            f"Keys present: {list(settings.keys())}",
            file=sys.stderr,
        )
        sys.exit(1)

    settings["app"]["custom_css"] = css

    print("Applying theme …")
    try:
        result = api_put("/api/settings", settings)
    except urllib.error.HTTPError as exc:
        body = exc.read().decode(errors="replace")
        print(f"Failed to update settings: HTTP {exc.code} – {body}", file=sys.stderr)
        sys.exit(1)

    if result.get("data") is not False:
        print("Theme applied successfully.")
    else:
        print(f"Unexpected response from settings API: {result}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()