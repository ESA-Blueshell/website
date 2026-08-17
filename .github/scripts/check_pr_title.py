#!/usr/bin/env python3
"""Check that a pull request title follows Conventional Commits.

The squash-merge commit takes its subject from the pull request title, so the
title is what ends up in the history a release tool reads.

Environment:
    PR_TITLE   the title to check (required)

Usage:
    check_pr_title.py           check $PR_TITLE, annotate and exit non-zero on failure
    check_pr_title.py --self-test   run the built-in cases
"""

from __future__ import annotations

import os
import re
import sys

# Conventional Commits types. `feat` and `fix` drive minor and patch releases;
# the rest are recognised but release-neutral. Add a type here to allow it.
TYPES = (
    "build",
    "chore",
    "ci",
    "docs",
    "feat",
    "fix",
    "perf",
    "refactor",
    "revert",
    "style",
    "test",
)

# Scope is free-form apart from parentheses, because this repo uses compound
# scopes such as `api,frontend` and `cohort+jobs`.
PATTERN = re.compile(
    r"^(?P<type>" + "|".join(TYPES) + r")"
    r"(?:\((?P<scope>[^()]+)\))?"
    r"(?P<breaking>!)?"
    r": (?P<subject>.+)$"
)

MAX_SUBJECT = 100


def check(title: str) -> str | None:
    """Return None when the title is valid, else a reason."""
    if not title or not title.strip():
        return "the title is empty"
    if title != title.strip():
        return "the title has leading or trailing whitespace"

    match = PATTERN.match(title)
    if not match:
        lowered = title.lower()
        for known in TYPES:
            if lowered.startswith(f"{known} ") or lowered.startswith(f"{known}:"):
                return f"'{known}' must be followed by an optional scope, then ': '"
            if lowered.startswith(known) and not title.startswith(known):
                return f"the type must be lowercase: write '{known}', not '{title[: len(known)]}'"
        head = title.split(":", 1)[0]
        if ":" in title:
            return f"'{head}' is not a known type; use one of {', '.join(TYPES)}"
        return "the title needs a 'type: ' prefix, for example 'fix: ...'"

    subject = match.group("subject")
    if not subject.strip():
        return "the description after the colon is empty"
    if subject.startswith(" "):
        return "there is more than one space after the colon"
    if match.group("scope") is not None and not match.group("scope").strip():
        return "the scope parentheses are empty; drop them or name a scope"
    return None


def advise(title: str) -> str | None:
    """Return non-blocking guidance, or None. Never fails a build.

    Subject length is not part of Conventional Commits, and dependency bots
    generate titles nobody can reasonably shorten, so it only warns.
    """
    match = PATTERN.match(title)
    if not match:
        return None
    subject = match.group("subject")
    if len(subject) > MAX_SUBJECT:
        return f"the description is {len(subject)} characters; under {MAX_SUBJECT} reads better in a changelog"
    return None


HELP = f"""
Pull request titles follow Conventional Commits, because the squash-merge
subject is taken from the title.

    <type>[(scope)][!]: <description>

Types:  {', '.join(TYPES)}
Add `!` before the colon for a breaking change.

Examples:
    fix: stop the session cookie expiring early
    feat(api): add bulk mark-paid endpoint
    feat(api,frontend)!: drop the legacy preview envelope
    build(deps): bump the gradle group across 2 directories
"""


def main() -> int:
    if "--self-test" in sys.argv:
        return self_test()

    title = os.environ.get("PR_TITLE", "")
    reason = check(title)
    if reason is None:
        advice = advise(title)
        if advice:
            print(f"::warning title=Pull request title::{advice}")
        print(f"Title OK: {title}")
        return 0

    # Annotation first so it surfaces in the checks UI, then the guidance.
    print(f"::error title=Pull request title::{reason}")
    print(f"\nTitle: {title}\nProblem: {reason}\n{HELP}", file=sys.stderr)
    return 1


def self_test() -> int:
    valid = [
        "fix: stop the session cookie expiring early",
        "feat(api): add bulk mark-paid endpoint",
        "feat(api,frontend)!: drop the legacy preview envelope",
        "build(deps): bump the gradle group across 2 directories with 38 updates",
        "ci(cohort+jobs): shard the system tests",
        "revert: reinstate the previous fee rule",
        "chore!: drop node 18 support",
    ]
    invalid = [
        "",
        "   ",
        "Show period membership on the contribution manager",
        "Fix: capitalised type",
        "fix stop the session cookie expiring early",
        "fix:no space after the colon",
        "fix:  two spaces after the colon",
        "wip: not a known type",
        "fix(): empty scope",
        "fix: ",
        " fix: leading space",
    ]
    # Long subjects warn but must never fail: dependency bots generate them.
    long_subject = "build(deps): bump node from 26.4.0-bookworm-slim to 26.7.0-bookworm-slim in /services/frontend in the docker group across 1 directory"
    failures = 0
    if check(long_subject) is not None:
        failures += 1
        print(f"  FAIL (long subject must not block): {check(long_subject)}")
    if advise(long_subject) is None:
        failures += 1
        print("  FAIL (long subject should warn)")
    for title in valid:
        reason = check(title)
        if reason is not None:
            failures += 1
            print(f"  FAIL (should pass): {title!r} -> {reason}")
    for title in invalid:
        if check(title) is None:
            failures += 1
            print(f"  FAIL (should fail): {title!r}")
    print(f"self-test: {len(valid) + len(invalid) - failures}/{len(valid) + len(invalid)} cases pass")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
