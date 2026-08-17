#!/usr/bin/env python3
"""Render a per-service, per-category diff breakdown into a pull request body.

File counts and line counts come from the GitHub pulls/{n}/files API, which
already reports the three-dot (merge-base) diff and pre-resolves renames, so
no checkout of the head ref is needed.

Environment:
    GH_TOKEN    token for `gh` (required unless --dry-run)
    REPO        owner/name (required)
    PR_NUMBER   pull request number (required)

Usage:
    pr_diff_stats.py             fetch, render, splice into the PR body
    pr_diff_stats.py --dry-run   fetch and render to stdout, write nothing
"""

from __future__ import annotations

import json
import os
import re
import subprocess
import sys
from collections import defaultdict
from pathlib import Path

START = "<!-- diff-stats:start -->"
END = "<!-- diff-stats:end -->"

BAR_WIDTH = 26
LABEL_WIDTH = 18
INDENT = 2
NUM_WIDTH = 7
FILES_WIDTH = 5
HEAD_WIDTH = INDENT + LABEL_WIDTH + 1 + BAR_WIDTH
TOTAL_WIDTH = HEAD_WIDTH + NUM_WIDTH * 2 + FILES_WIDTH

ADD_GLYPH = "█"  # full block
DEL_GLYPH = "░"  # light shade

BODY_LIMIT = 65000

SERVICE_ORDER = ["api", "frontend", "system-tests", "libs", "platform", "ci", "docs"]
CATEGORY_ORDER = [
    "prod",
    "unit",
    "integration",
    "e2e",
    "system",
    "fixtures",
    "infra",
    "build",
    "docs",
    "generated",
    "other",
]
CATEGORY_LABELS = {
    "prod": "production",
    "unit": "unit tests",
    "integration": "integration tests",
    "e2e": "e2e tests",
    "system": "system tests",
    "fixtures": "test fixtures",
    "infra": "infrastructure",
    "build": "build & config",
    "docs": "docs",
    "generated": "generated",
    "other": "unclassified",
}

PROD_CATEGORIES = {"prod"}
TEST_CATEGORIES = {"unit", "integration", "e2e", "system", "fixtures"}
EXCLUDED = {"generated"}


def glob_to_regex(pattern: str) -> re.Pattern[str]:
    """Translate a path glob to a regex. `**` spans separators, `*` does not."""
    out: list[str] = []
    i, n = 0, len(pattern)
    while i < n:
        if pattern.startswith("**/", i):
            out.append("(?:.*/)?")
            i += 3
        elif pattern.startswith("/**", i) and i + 3 == n:
            out.append("(?:/.*)?")
            i += 3
        elif pattern.startswith("**", i):
            out.append(".*")
            i += 2
        elif pattern[i] == "*":
            out.append("[^/]*")
            i += 1
        elif pattern[i] == "?":
            out.append("[^/]")
            i += 1
        else:
            out.append(re.escape(pattern[i]))
            i += 1
    return re.compile("^" + "".join(out) + "$")


def load_rules(path: Path) -> list[tuple[re.Pattern[str], str, str]]:
    lines = [ln for ln in path.read_text(encoding="utf-8").splitlines() if not ln.lstrip().startswith("//")]
    raw = json.loads("\n".join(lines))
    return [(glob_to_regex(pat), service, category) for pat, service, category in raw]


def classify(path: str, rules) -> tuple[str, str]:
    for pattern, service, category in rules:
        if pattern.match(path):
            return service, category
    return "other", "other"


def fetch_files(repo: str, pr: str) -> list[dict]:
    """One JSON object per line, so page boundaries do not need stitching."""
    proc = subprocess.run(
        ["gh", "api", "--paginate", f"repos/{repo}/pulls/{pr}/files", "--jq", ".[]|@json"],
        capture_output=True,
        text=True,
        check=True,
    )
    return [json.loads(line) for line in proc.stdout.splitlines() if line.strip()]


def bar(add: int, dele: int, scale: int) -> str:
    if scale <= 0:
        return ""
    cells = lambda v: max(1, round(v / scale * BAR_WIDTH)) if v > 0 else 0  # noqa: E731
    a, d = cells(add), cells(dele)
    if a + d > BAR_WIDTH:
        # Clamp overflow (generated rows share the hand-written scale).
        keep = BAR_WIDTH - min(d, BAR_WIDTH - 1) if d else BAR_WIDTH
        a, d = max(0, keep), min(d, BAR_WIDTH - max(0, keep))
    return ADD_GLYPH * a + DEL_GLYPH * d


def row(label: str, bar_text: str, add: int, dele: int, files: int, marker: str = "") -> str:
    line = (
        " " * INDENT
        + label.ljust(LABEL_WIDTH)
        + " "
        + bar_text.ljust(BAR_WIDTH)
        + f"+{add}".rjust(NUM_WIDTH)
        + f"-{dele}".rjust(NUM_WIDTH)
        + str(files).rjust(FILES_WIDTH)
    )
    return (line + marker).rstrip()


def render(files: list[dict], rules) -> str:
    buckets: dict[tuple[str, str], dict[str, int]] = defaultdict(lambda: {"add": 0, "del": 0, "files": 0})
    for entry in files:
        service, category = classify(entry["filename"], rules)
        cell = buckets[(service, category)]
        cell["add"] += entry.get("additions", 0)
        cell["del"] += entry.get("deletions", 0)
        cell["files"] += 1

    if not buckets:
        return ""

    def service_key(name: str) -> tuple[int, str]:
        if name in SERVICE_ORDER:
            return (SERVICE_ORDER.index(name), "")
        return (len(SERVICE_ORDER) + (1 if name == "other" else 0), name)

    def category_key(name: str) -> tuple[int, str]:
        return (CATEGORY_ORDER.index(name), "") if name in CATEGORY_ORDER else (len(CATEGORY_ORDER), name)

    scale = max(
        (c["add"] + c["del"] for (_, cat), c in buckets.items() if cat not in EXCLUDED),
        default=0,
    )

    lines: list[str] = []
    for service in sorted({s for s, _ in buckets}, key=service_key):
        categories = sorted((c for s, c in buckets if s == service), key=category_key)
        counted = [c for c in categories if c not in EXCLUDED]
        head = {
            "add": sum(buckets[(service, c)]["add"] for c in counted),
            "del": sum(buckets[(service, c)]["del"] for c in counted),
            "files": sum(buckets[(service, c)]["files"] for c in counted),
        }
        lines.append(
            service.ljust(HEAD_WIDTH)
            + f"+{head['add']}".rjust(NUM_WIDTH)
            + f"-{head['del']}".rjust(NUM_WIDTH)
            + str(head["files"]).rjust(FILES_WIDTH)
        )
        for category in categories:
            cell = buckets[(service, category)]
            label = CATEGORY_LABELS.get(category, category)
            marker = "  ~" if category in EXCLUDED else ""
            lines.append(row(label, bar(cell["add"], cell["del"], scale), cell["add"], cell["del"], cell["files"], marker))
        lines.append("")

    prod = {k: sum(c[k] for (_, cat), c in buckets.items() if cat in PROD_CATEGORIES) for k in ("add", "del")}
    test = {k: sum(c[k] for (_, cat), c in buckets.items() if cat in TEST_CATEGORIES) for k in ("add", "del")}
    kept = {
        k: sum(c[k] for (_, cat), c in buckets.items() if cat not in EXCLUDED)
        for k in ("add", "del", "files")
    }
    gen = {
        k: sum(c[k] for (_, cat), c in buckets.items() if cat in EXCLUDED)
        for k in ("add", "del", "files")
    }

    def summary(label: str, add: int, dele: int, note: str = "") -> str:
        line = label.ljust(HEAD_WIDTH) + f"+{add}".rjust(NUM_WIDTH) + f"-{dele}".rjust(NUM_WIDTH)
        return (line + ("  " + note if note else "")).rstrip()

    lines.append("─" * TOTAL_WIDTH)
    lines.append(summary("production", prod["add"], prod["del"]))
    ratio = f"{test['add'] / prod['add']:.2f} test lines per prod line" if prod["add"] else ""
    lines.append(summary("tests", test["add"], test["del"], ratio))
    lines.append(summary("total (hand-written)", kept["add"], kept["del"], f"{kept['files']} files"))
    if gen["files"]:
        lines.append(summary("~ generated (excluded)", gen["add"], gen["del"], f"{gen['files']} files"))

    table = "\n".join(lines).rstrip()
    return (
        f"{START}\n---\n**Diff breakdown** — `{ADD_GLYPH}` added `{DEL_GLYPH}` removed, "
        f"scaled to the largest row.\n\n```text\n{table}\n```\n{END}"
    )


def splice(body: str, block: str) -> str:
    pattern = re.compile(re.escape(START) + ".*?" + re.escape(END), re.DOTALL)
    if pattern.search(body):
        return pattern.sub(lambda _: block, body, count=1)
    return (body.rstrip() + "\n\n" + block) if body.strip() else block


def main() -> int:
    dry_run = "--dry-run" in sys.argv
    repo, pr = os.environ.get("REPO"), os.environ.get("PR_NUMBER")
    if not repo or not pr:
        print("REPO and PR_NUMBER are required", file=sys.stderr)
        return 1

    rules = load_rules(Path(__file__).resolve().parents[1] / "diff-stats.json")
    files = fetch_files(repo, pr)
    if not files:
        print("No changed files; leaving the body alone.")
        return 0

    block = render(files, rules)
    if dry_run:
        print(block)
        return 0

    current = json.loads(
        subprocess.run(
            ["gh", "api", f"repos/{repo}/pulls/{pr}", "--jq", "{body:.body}"],
            capture_output=True,
            text=True,
            check=True,
        ).stdout
    )["body"] or ""

    updated = splice(current, block)
    if updated == current:
        print("Diff breakdown already current.")
        return 0
    if len(updated) > BODY_LIMIT:
        print("Body would exceed the size limit; skipping.", file=sys.stderr)
        return 0

    subprocess.run(
        ["gh", "api", "--method", "PATCH", f"repos/{repo}/pulls/{pr}", "--input", "-"],
        input=json.dumps({"body": updated}),
        text=True,
        check=True,
        capture_output=True,
    )
    print(f"Updated the diff breakdown on {repo}#{pr}.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
