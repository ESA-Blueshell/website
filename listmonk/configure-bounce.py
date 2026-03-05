#!/usr/bin/env python3
"""
Configure Listmonk bounce processing settings.

Reads the current Listmonk settings via the API, enables bounce processing,
and optionally configures an IMAP mailbox for bounce collection.
Idempotent: never overwrites existing mailbox entries.

Environment variables
---------------------
LISTMONK_URL                            Base URL (default: http://listmonk:9000)
LISTMONK_ADMIN_USERNAME                 Admin username (default: listmonk)
LISTMONK_ADMIN_PASSWORD                 Admin password (default: listmonk)

LISTMONK_BOUNCE_MAILBOX_ENABLED         Set to "true" to configure an IMAP mailbox
LISTMONK_BOUNCE_MAILBOX_HOST            IMAP host (required when enabled)
LISTMONK_BOUNCE_MAILBOX_PORT            IMAP port (default: 993)
LISTMONK_BOUNCE_MAILBOX_USERNAME        IMAP username (required when enabled)
LISTMONK_BOUNCE_MAILBOX_PASSWORD        IMAP password
LISTMONK_BOUNCE_MAILBOX_TLS_ENABLED     Use TLS (default: true)
LISTMONK_BOUNCE_MAILBOX_TLS_SKIP_VERIFY Skip TLS certificate verification (default: false)
LISTMONK_BOUNCE_MAILBOX_FOLDER          IMAP folder to scan (default: INBOX)
LISTMONK_BOUNCE_MAILBOX_RETURN_PATH     Return-Path address (default: empty)
LISTMONK_BOUNCE_MAILBOX_SCAN_INTERVAL   Scan interval, e.g. "10m" (default: 10m)
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


def api_put(path: str, body: dict) -> None:
    data = json.dumps(body).encode()
    req = urllib.request.Request(
        f"{LISTMONK_URL}{path}",
        data=data,
        headers=_auth_headers(),
        method="PUT",
    )
    with urllib.request.urlopen(req) as resp:
        resp.read()


def _bool_env(name: str, default: bool) -> bool:
    val = os.environ.get(name, "").lower()
    if val in ("true", "1", "yes"):
        return True
    if val in ("false", "0", "no"):
        return False
    return default


def main() -> None:
    print(f"Configuring Listmonk bounce settings at {LISTMONK_URL} …")
    try:
        response = api_get("/api/settings")
    except urllib.error.HTTPError as exc:
        print(f"Failed to fetch settings: HTTP {exc.code} – {exc.reason}", file=sys.stderr)
        sys.exit(1)

    settings = response.get("data", response)
    if not isinstance(settings, dict):
        print("Unexpected settings structure — skipping bounce configuration.", file=sys.stderr)
        sys.exit(1)

    changed = False

    # Always ensure bounce processing is enabled
    if settings.get("bounce.enabled") is not True:
        settings["bounce.enabled"] = True
        changed = True
        print("Enabling bounce processing.")
    else:
        print("Bounce processing already enabled.")

    # Optionally configure an IMAP mailbox
    if _bool_env("LISTMONK_BOUNCE_MAILBOX_ENABLED", False):
        host = os.environ.get("LISTMONK_BOUNCE_MAILBOX_HOST", "")
        username = os.environ.get("LISTMONK_BOUNCE_MAILBOX_USERNAME", "")
        if not host or not username:
            print(
                "LISTMONK_BOUNCE_MAILBOX_ENABLED=true but HOST/USERNAME not set — skipping mailbox config.",
                file=sys.stderr,
            )
        else:
            mailboxes = settings.get("bounce.mailboxes") or []
            if mailboxes:
                print(f"Listmonk already has {len(mailboxes)} bounce mailbox(es) — not overwriting.")
            else:
                port = int(os.environ.get("LISTMONK_BOUNCE_MAILBOX_PORT", "993"))
                settings["bounce.mailboxes"] = [
                    {
                        "enabled": True,
                        "type": "imap",
                        "host": host,
                        "port": port,
                        "auth_protocol": "userpass",
                        "username": username,
                        "password": os.environ.get("LISTMONK_BOUNCE_MAILBOX_PASSWORD", ""),
                        "tls_enabled": _bool_env("LISTMONK_BOUNCE_MAILBOX_TLS_ENABLED", True),
                        "tls_skip_verify": _bool_env("LISTMONK_BOUNCE_MAILBOX_TLS_SKIP_VERIFY", False),
                        "folder": os.environ.get("LISTMONK_BOUNCE_MAILBOX_FOLDER", "INBOX"),
                        "return_path": os.environ.get("LISTMONK_BOUNCE_MAILBOX_RETURN_PATH", ""),
                        "scan_interval": os.environ.get("LISTMONK_BOUNCE_MAILBOX_SCAN_INTERVAL", "10m"),
                    }
                ]
                changed = True
                print(f"Configuring IMAP bounce mailbox: host={host} port={port}")

    if not changed:
        print("Bounce settings already up to date — no changes needed.")
        return

    try:
        api_put("/api/settings", settings)
    except urllib.error.HTTPError as exc:
        body = exc.read().decode(errors="replace")
        print(f"Failed to update settings: HTTP {exc.code} – {body}", file=sys.stderr)
        sys.exit(1)

    print("Bounce settings configured successfully.")


if __name__ == "__main__":
    main()
