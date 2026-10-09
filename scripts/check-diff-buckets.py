#!/usr/bin/env python3
"""
Decides which buckets a pull request's diff reaches.

`Validate` gates each job on a bucket, and this is the only thing that assigns
one. It was a dorny/paths-filter step, which evaluates each pattern on its own:
there `!a/**` matches every path outside `a`, so one negation made a bucket
claim the whole repository (#1453). Matching here instead means the rules CI
runs are the rules --self-test proves.

A path in no bucket runs everything and says which path it was. That is how
the Gradle build, the CI actions and validate.yml itself reach every suite.

--self-test proves this can still fail, the way check-flux-manifests.sh does.
"""

import json
import os
import re
import subprocess
import sys
from pathlib import Path

import yaml

WORKFLOW = Path(".github/workflows/validate.yml")
BUCKETS = Path(".github/buckets.yml")

# What a job's `if` reads a bucket through: `fromJSON(needs.changes.outputs.run).api`.
GATE = re.compile(r"needs\.changes\.outputs\.run\)\.([a-z-]+)")


def buckets_of(path=BUCKETS):
    """Every bucket, as {name: [pattern, ...]}."""
    declared = yaml.safe_load(path.read_text())
    if "ignore" not in declared:
        raise SystemExit(f"::error::{path} declares no `ignore` bucket")
    return declared


def gates_of(buckets):
    """The buckets a job can gate on: every one but `ignore`, which gates nothing."""
    return [name for name in buckets if name != "ignore"]


# The three globstar shapes, lifted out before the single-segment wildcards so
# that `*` never sees half of a `**`.
ANY_SEGMENTS = "\x00"   # `**/` and `/**/`: any number of directories, none included
ANY_DESCENDANT = "\x01"  # trailing `/**`: the directory itself and everything under it
ANYTHING = "\x02"        # a bare `**`


def to_regex(glob):
    """picomatch's globstar, as far as the filter uses it: `**`, `*` and `?`."""
    glob = re.sub(r"/\*\*$", ANY_DESCENDANT, glob)
    while "/**/" in glob:
        glob = glob.replace("/**/", "/" + ANY_SEGMENTS, 1)
    if glob.startswith("**/"):
        glob = ANY_SEGMENTS + glob[3:]
    glob = glob.replace("**", ANYTHING)

    out = ["(?s:"]
    for char in glob:
        if char == ANY_SEGMENTS:
            out.append("(?:[^/]*/)*")
        elif char == ANY_DESCENDANT:
            out.append("(?:/.*)?")
        elif char == ANYTHING:
            out.append(".*")
        elif char == "*":
            out.append("[^/]*")
        elif char == "?":
            out.append("[^/]")
        else:
            out.append(re.escape(char))
    out.append(r")\Z")
    return re.compile("".join(out))


def matches(path, patterns):
    """A bucket claims a path when a positive pattern takes it and no `!` drops it."""
    claimed = False
    for pattern in patterns or []:
        if pattern.startswith("!"):
            if to_regex(pattern[1:]).match(path):
                return False
        elif to_regex(pattern).match(path):
            claimed = True
    return claimed


def classify(path, buckets):
    return {name for name, patterns in buckets.items() if matches(path, patterns)}


def changed(base, head):
    diff = subprocess.run(
        ["git", "diff", "--name-only", f"{base}...{head}"],
        capture_output=True, text=True, check=True,
    )
    return [line for line in diff.stdout.splitlines() if line]


def unmatched(paths, buckets):
    return [path for path in paths if not classify(path, buckets)]


# Buckets whose suites need neither the jar nor the bundle, so they do not turn `app` on.
STANDALONE = {"platform", "pinger", "pingerapp"}


# A path for every pattern in the filter, including the negations. A bucket
# that stops covering its own tree fails here rather than on the pull request
# that happens to touch it, and the self-test refuses a pattern no fixture
# exercises, so the table cannot fall behind the filter.
FIXTURES = [
    ("platform/nix/modules/k3s/bootstrap.nix", {"platform"}),
    ("platform/flake.lock", {"platform"}),
    ("platform/cluster/flux/apps/data/valkey/deployment.yaml", {"platform"}),
    ("platform/docs/runbook.md", {"ignore"}),
    ("scripts/check-flux-manifests.sh", {"platform"}),
    ("scripts/regenerate-flux-components.sh", {"platform"}),
    ("scripts/seed-vault-from-env.sh", {"platform"}),
    ("scripts/seed-backup-credentials.sh", {"platform"}),
    # validate.yml, the actions it runs and this file change how every suite runs,
    # so they sit in no bucket, which runs everything.
    (".github/workflows/validate.yml", set()),
    (".github/actions/setup-gradle/action.yml", set()),
    (".github/buckets.yml", set()),
    ("gradle/libs.versions.toml", set()),
    ("build-logic/src/main/kotlin/blueshell.kotlin-conventions.gradle.kts", set()),
    ("settings.gradle.kts", set()),
    ("infra/stalwart/accounts.json", set()),
    # Workflow checks runs on every pull request; nothing else reads these.
    (".github/workflows/release.yml", {"ignore"}),
    (".github/scripts/pr_report.py", {"ignore"}),
    ("scripts/check-workflow-permissions.py", {"ignore"}),
    ("scripts/write-release-tag.sh", {"ignore"}),
    # The `changes` job self-tests this on every pull request.
    ("scripts/check-diff-buckets.py", {"ignore"}),
    ("scripts/generate-policy-pdfs.sh", {"ignore"}),
    ("scripts/pandoc-html-br.lua", {"ignore"}),
    ("scripts/__pycache__/check-diff-buckets.cpython-310.pyc", {"ignore"}),
    ("services/api/src/main/kotlin/net/blueshell/api/event/web/EventController.kt", {"api"}),
    ("services/api/src/main/resources/db/changelog/changes/2026-09-26-thing.yaml", {"api"}),
    ("services/api/Dockerfile", {"api"}),
    ("services/api/docker-compose.yml", {"ignore"}),
    ("config/detekt/detekt.yml", {"api"}),
    ("scripts/scrape-public-events.py", {"api"}),
    ("scripts/check-changeset-compatibility.py", {"api"}),
    ("scripts/changeset-sql.sh", {"api"}),
    ("services/frontend/src/pages/Home.vue", {"frontend"}),
    ("services/frontend/Dockerfile", {"frontend"}),
    ("services/frontend/docker-compose.yml", {"ignore"}),
    ("services/pinger/internal/paint/sender.go", {"pinger"}),
    ("services/pinger/docker-compose.yml", {"ignore"}),
    ("services/pinger-app/internal/oauth/pkce.go", {"pingerapp"}),
    ("tests/system/src/test/kotlin/SignUpTest.kt", {"system"}),
    ("docker-compose.yml", {"ignore"}),
    ("docker-compose.oidc-e2e.yml", {"ignore"}),
    ("services/stalwart/entrypoint.sh", {"ignore"}),
    ("services/vault/docker-compose.yml", {"ignore"}),
    (".env", {"ignore"}),
    ("dev-setup.sh", {"ignore"}),
    ("scripts/seed-stalwart-accounts.sh", {"ignore"}),
    ("docs/agents/ci.md", {"ignore"}),
    ("gameart/cs2-1.webp", {"ignore"}),
    ("infra/dns/esa-blueshell.nl.zone", {"ignore"}),
    (".idea/misc.xml", {"ignore"}),
    ("README.md", {"ignore"}),
    (".editorconfig", {"ignore"}),
    (".gitattributes", {"ignore"}),
    (".gitignore", {"ignore"}),
    ("renovate.json", {"ignore"}),
    ("release-please-config.json", {"ignore"}),
    (".release-please-manifest.json", {"ignore"}),
]


def unexercised(buckets):
    """Patterns whose removal no fixture would notice."""
    loose = []
    for name, patterns in buckets.items():
        for pattern in patterns:
            without = {k: list(v) for k, v in buckets.items()}
            without[name] = [p for p in without[name] if p != pattern]
            if all(classify(path, without) == expected for path, expected in FIXTURES):
                loose.append(f"{name}: {pattern}")
    return loose


def unknown_gates(buckets, workflow=WORKFLOW):
    """Bucket names a job's `if` reads that no bucket declares, which would read as false."""
    known = {*gates_of(buckets), "app"}
    return sorted(set(GATE.findall(workflow.read_text())) - known)


def self_test():
    """Every fixture lands where it says, and every gate a job reads exists."""
    buckets = buckets_of()
    wrong = []
    for path, expected in FIXTURES:
        actual = classify(path, buckets)
        if actual != expected:
            wrong.append(f"{path}: expected {sorted(expected) or '[]'}, got {sorted(actual) or '[]'}")
    if wrong:
        for line in wrong:
            print(f"self-test FAILED: {line}")
        return 1
    # A `!` pattern subtracts from its own bucket. It must never add a path to
    # one, which is what dorny/paths-filter did: there `!a/**` matched every
    # path outside `a`, so `backend` claimed the whole repository (#1453).
    for name, patterns in buckets.items():
        negated = [p for p in patterns if p.startswith("!")]
        if negated and matches("nothing/in/any/bucket.txt", patterns):
            print(f"self-test FAILED: `{name}` claims a path only its {negated[0]} matches")
            return 1
    # A misspelt gate is null in an `if`, so its job would skip on every run.
    for name in unknown_gates(buckets):
        print(f"self-test FAILED: {WORKFLOW} gates a job on `{name}`, which no bucket declares")
    if unknown_gates(buckets):
        return 1
    # A pattern nothing exercises is a pattern nothing would miss. Either the
    # filter has an entry it does not need, or the table is behind it.
    for pattern in unexercised(buckets):
        print(f"self-test FAILED: no fixture exercises {pattern}")
    if unexercised(buckets):
        return 1
    print(f"self-test ok: {len(FIXTURES)} fixtures place correctly")
    return 0


def decide(base, head):
    """
    The `run` output the `changes` job publishes: each bucket, and `app`, as JSON.

    `app` is any bucket but `platform` and `pinger`: what needs the jar and the bundle built.
    A path in no bucket turns every bucket on, and a run with no diff to read
    passes no base and runs everything.
    """
    buckets = buckets_of()
    gates = gates_of(buckets)
    if base is None:
        on = dict.fromkeys(gates, True)
    else:
        paths = changed(base, head)
        stray = unmatched(paths, buckets)
        on = {name: any(matches(path, buckets[name]) for path in paths) for name in gates}
        if stray:
            print("::notice::these changed paths are in no bucket, so the whole suite runs: "
                  + " ".join(stray))
            on = dict.fromkeys(gates, True)
    on["app"] = any(value for name, value in on.items() if name not in STANDALONE)
    return "run=" + json.dumps(on)


def main():
    if "--self-test" in sys.argv:
        return self_test()
    args = dict(zip(sys.argv[1::2], sys.argv[2::2]))
    if "--all" not in sys.argv and not args.get("--base"):
        print("::error::--base is required, or --all for a run with no diff")
        return 1
    line = decide(args.get("--base"), args.get("--head", "HEAD"))
    print(line)
    # The step reads this as its output; the same line goes to the log above, so
    # a run says what it decided without opening the job's output.
    destination = os.environ.get("GITHUB_OUTPUT")
    if destination:
        with open(destination, "a") as handle:
            handle.write(line + "\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
