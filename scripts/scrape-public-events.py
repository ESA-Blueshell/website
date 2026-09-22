#!/usr/bin/env python3
"""
Writes the dev seeder's events and committees from what the site already serves.

The association's own events read better than anything a generator invents: real titles, real
places, real descriptions and the mix of members-only, sign-up and walk-in that the pages have
to draw. The public API answers all of it without a token, so the seed is scraped rather than
written by hand.

The API says nothing about which committee ran an event, so the committee is read out of the
title and the description; what names no committee is left to the member's initiative, which is
what the association itself does.

Each event's banner is fetched into `art/` beside the CSV and named in the `art` column; an
event the site serves without one is left blank, and the site draws it on the fallback
template. `--no-banners` leaves the art directory alone.
"""

import argparse
import csv
import json
import ssl
import sys
import urllib.error
import urllib.request
from pathlib import Path

SITE = "https://esa-blueshell.nl/api"
HERE = Path(__file__).resolve().parent
SEED = HERE.parent / "services/api/src/main/resources/db/seed/events"
FALLBACK_COMMITTEE = "Member's initiative"


def context() -> ssl.SSLContext:
    """The chain as the system has it, and as the runtime has it where certifi is installed."""
    try:
        import certifi

        return ssl.create_default_context(cafile=certifi.where())
    except ImportError:
        return ssl.create_default_context()


TRUST = context()


# The site answers a bare urllib with 403, so every call says who is asking.
AGENT = "blueshell-seed-scraper/1.0"


def fetch(path: str, timeout: int = 30):
    asking = urllib.request.Request(f"{SITE}/{path}", headers={"accept": "*/*", "user-agent": AGENT})
    return urllib.request.urlopen(asking, timeout=timeout, context=TRUST)


def asked(path: str) -> object:
    with fetch(path) as answer:
        return json.load(answer)


def rows_of(answer: object) -> list:
    if isinstance(answer, dict):
        return answer.get("content", [])
    return answer if isinstance(answer, list) else []


def committee_for(event: dict, names: list[str]) -> str:
    """The committee an event names, read out of what it says about itself."""
    said = f"{event.get('title', '')} {event.get('description', '')}".lower()
    for name in sorted(names, key=len, reverse=True):
        if name.lower().strip() in said:
            return name
    return FALLBACK_COMMITTEE


def fetch_banners(events: list, into: Path) -> dict:
    """Each event's banner, by event id, kept under the name the seed row will carry."""
    into.mkdir(parents=True, exist_ok=True)
    kept = {}
    for one in events:
        try:
            with fetch(f"events/{one['id']}/banners", timeout=60) as answer:
                kind = answer.headers.get_content_type()
                if not kind.startswith("image/"):
                    continue
                name = f"{one['id']}.{kind.split('/')[-1]}"
                (into / name).write_bytes(answer.read())
                kept[one["id"]] = name
        except urllib.error.HTTPError:
            continue
    print(f"{len(kept)} banners fetched into {into}")
    return kept


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--events", type=int, default=60, help="How many events to take, newest first.")
    parser.add_argument("--out", type=Path, default=SEED, help="Where the CSV files go.")
    parser.add_argument("--no-banners", dest="banners", action="store_false", help="Leave art/ as it stands.")
    said = parser.parse_args()

    committees = rows_of(asked("committees"))
    events = rows_of(asked(f"events?sort=startTime,desc&size={said.events}"))
    if not committees or not events:
        print("The site answered no committees or no events; nothing was written.", file=sys.stderr)
        return 1

    names = [one["name"].strip() for one in committees if one.get("name")]
    said.out.mkdir(parents=True, exist_ok=True)

    with (said.out / "committees.csv").open("w", newline="", encoding="utf-8") as file:
        writer = csv.writer(file, lineterminator="\n")
        writer.writerow(["name", "description"])
        for one in committees:
            writer.writerow([one["name"].strip(), (one.get("description") or "").strip()])

    detailed = []
    for one in events:
        try:
            detailed.append(asked(f"events/{one['id']}"))
        except urllib.error.HTTPError:
            detailed.append(one)

    art = fetch_banners(detailed, said.out / "art") if said.banners else {}

    with (said.out / "events.csv").open("w", newline="", encoding="utf-8") as file:
        writer = csv.writer(file, lineterminator="\n")
        writer.writerow([
            "source_id", "title", "committee", "location", "start_time", "end_time",
            "members_only", "sign_up", "description", "art",
        ])
        for one in detailed:
            writer.writerow([
                one["id"],
                one.get("title", "").strip(),
                committee_for(one, names),
                (one.get("location") or "").strip(),
                one.get("startTime", ""),
                one.get("endTime", ""),
                str(bool(one.get("membersOnly"))).lower(),
                str(bool(one.get("signUp"))).lower(),
                (one.get("description") or "").strip(),
                art.get(one["id"], ""),
            ])

    print(f"{len(committees)} committees and {len(detailed)} events written to {said.out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
