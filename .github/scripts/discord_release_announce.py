#!/usr/bin/env python3
"""Announce a cut release on Discord.

Reads the release-please outputs from the environment and POSTs a single embed to
a Discord webhook. Discord renders markdown inside an embed description, so the
release notes go across as-is rather than being flattened to plain text.

Environment:
  DISCORD_WEBHOOK_URL  required unless --dry-run; the webhook to post to
  RELEASE_TAG          required; e.g. "v1.2.0"
  RELEASE_NOTES        the generated notes; may be empty
  RELEASE_URL          link to the release page on GitHub
  REPO                 "owner/name", used for the footer

Exit codes: 0 posted (or dry run), 1 bad input, 2 Discord refused the post.
"""

from __future__ import annotations

import json
import os
import sys
import urllib.error
import urllib.request

# Discord's documented ceilings. The description is the only one release notes can
# realistically hit, so it is the only one that gets a truncation path.
DESCRIPTION_LIMIT = 4096
TITLE_LIMIT = 256

# Blueshell blue, so the announcement is recognisable at a glance in the channel.
EMBED_COLOUR = 0x1E88E5


def strip_leading_heading(notes: str) -> str:
    """Drop release-please's own version heading; the embed title already carries it."""
    lines = notes.lstrip().splitlines()
    if lines and lines[0].startswith("## "):
        return "\n".join(lines[1:]).lstrip()
    return notes.strip()


def build_description(notes: str, release_url: str) -> str:
    """Fit the notes plus a trailing link into one embed description."""
    link = f"\n\n[Full release notes]({release_url})" if release_url else ""
    body = strip_leading_heading(notes)
    if not body:
        body = "No notable changes were recorded for this release."

    budget = DESCRIPTION_LIMIT - len(link)
    if len(body) <= budget:
        return body + link

    # Cut on a line boundary so a truncated bullet never lands mid-word.
    marker = "\n\n…"
    kept: list[str] = []
    used = 0
    for line in body.splitlines():
        if used + len(line) + 1 > budget - len(marker):
            break
        kept.append(line)
        used += len(line) + 1
    return "\n".join(kept).rstrip() + marker + link


def build_payload(tag: str, notes: str, release_url: str, repo: str) -> dict:
    embed: dict = {
        "title": tag[:TITLE_LIMIT],
        "description": build_description(notes, release_url),
        "color": EMBED_COLOUR,
    }
    if release_url:
        embed["url"] = release_url
    if repo:
        embed["footer"] = {"text": repo}
    return {"embeds": [embed]}


def post(url: str, payload: dict) -> None:
    request = urllib.request.Request(
        url,
        data=json.dumps(payload).encode(),
        headers={"Content-Type": "application/json", "User-Agent": "blueshell-release-announcer"},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        if response.status >= 300:
            raise RuntimeError(f"Discord returned {response.status}")


def main(argv: list[str]) -> int:
    dry_run = "--dry-run" in argv

    tag = os.environ.get("RELEASE_TAG", "").strip()
    if not tag:
        print("RELEASE_TAG is required", file=sys.stderr)
        return 1

    payload = build_payload(
        tag=tag,
        notes=os.environ.get("RELEASE_NOTES", ""),
        release_url=os.environ.get("RELEASE_URL", "").strip(),
        repo=os.environ.get("REPO", "").strip(),
    )

    if dry_run:
        print(json.dumps(payload, indent=2))
        return 0

    webhook = os.environ.get("DISCORD_WEBHOOK_URL", "").strip()
    if not webhook:
        print("DISCORD_WEBHOOK_URL is required", file=sys.stderr)
        return 1

    try:
        post(webhook, payload)
    except (urllib.error.HTTPError, urllib.error.URLError, RuntimeError) as error:
        detail = ""
        if isinstance(error, urllib.error.HTTPError):
            detail = f": {error.read().decode(errors='replace')[:500]}"
        print(f"Discord refused the announcement ({error}){detail}", file=sys.stderr)
        return 2

    print(f"Announced {tag} on Discord.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
