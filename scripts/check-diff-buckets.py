#!/usr/bin/env python3
"""
Decides which buckets a pull request's diff reaches.

`Validate` gates each job on a bucket, and this is the only thing that assigns
one. It was a dorny/paths-filter step, which evaluates each pattern on its own:
there `!a/**` matches every path outside `a`, so one negation made a bucket
claim the whole repository (#1453). Matching here instead means the rules CI
runs are the rules --self-test proves.

A path in no bucket is validated by nothing, so it runs everything and says
which path it was, rather than deciding in silence.

--self-test proves this can still fail, the way check-flux-manifests.sh does.
"""

import os
import re
import subprocess
import sys
from pathlib import Path

import yaml

WORKFLOW = Path(".github/workflows/validate.yml")
BUCKETS = Path(".github/buckets.yml")

# Everything `Validate` gates on. `meta` and `ignore` are not gates: meta turns
# them all on, and ignore is what no job validates.
GATES = ["platform-nix", "platform-flux", "workflows", "backend", "frontend",
         "contract", "changesets", "images", "measured"]

# What api-static and fe-static compile for. A platform or workflow change
# needs neither.
APP = {"backend", "frontend", "contract", "changesets", "images"}

# The suites whose coverage the `measured` gate reads. A path that is measured
# is always in one of these already; an edit to the gate job is not, and the
# gate refuses to run with neither suite behind it.
MEASURED_BY = {"backend", "frontend"}


def buckets_of(path=BUCKETS):
    """Every bucket, as {name: [pattern, ...]}."""
    declared = yaml.safe_load(path.read_text())
    for name in [*GATES, "meta", "ignore"]:
        if name not in declared:
            raise SystemExit(f"::error::{path} declares no `{name}` bucket")
    return declared


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


def gate_of(job):
    """The buckets a job's `if` reads, or None when it reads no bucket."""
    found = set(re.findall(r"needs\.changes\.outputs\.([a-z-]+) == 'true'", str(job.get("if", ""))))
    return found or None


def reached_by(was, head):
    """The buckets an edit from `was` to `head` reaches, or None for all of them."""
    if {k: v for k, v in was.items() if k != "jobs"} != {k: v for k, v in head.items() if k != "jobs"}:
        return None
    old_jobs, new_jobs = was.get("jobs") or {}, head.get("jobs") or {}
    reached = set()
    for name in set(old_jobs) | set(new_jobs):
        if old_jobs.get(name) == new_jobs.get(name):
            continue
        gate = gate_of(new_jobs.get(name) or old_jobs[name])
        if gate is None:
            return None
        reached |= gate
    if "measured" in reached:
        reached |= MEASURED_BY
    return reached


def workflow_buckets(base, workflow=WORKFLOW):
    """
    The buckets a change to this workflow reaches, by the jobs it edits.

    An edited job is validated by whatever it gates on, so editing the api
    unit-test step runs the backend bucket and editing a platform job does not.
    A job that gates on nothing, a job this file gained or lost, and the
    workflow's own trigger and environment all reach everything: none of them
    belongs to one bucket, and the decision is not worth guessing.
    """
    shown = subprocess.run(
        ["git", "show", f"{base}:{workflow}"],
        capture_output=True, text=True,
    )
    if shown.returncode:
        return None
    return reached_by(yaml.safe_load(shown.stdout), yaml.safe_load(workflow.read_text()))


# A path for every pattern in the filter, including the negations. A bucket
# that stops covering its own tree fails here rather than on the pull request
# that happens to touch it, and the self-test refuses a pattern no fixture
# exercises, so the table cannot fall behind the filter.
FIXTURES = [
    ("platform/nix/modules/k3s/bootstrap.nix", {"platform-nix"}),
    ("platform/flake.lock", {"platform-nix"}),
    ("platform/flake.nix", {"platform-nix"}),
    ("platform/cluster/flux/apps/data/valkey/deployment.yaml", {"platform-flux"}),
    (".github/workflows/release.yml", {"workflows"}),
    (".github/workflows/validate.yml", {"workflows"}),
    # A workflow file like any other, and meta besides, since it decides
    # what every bucket claims.
    (".github/buckets.yml", {"meta", "workflows"}),
    (".github/actions/setup-gradle/action.yml", {"meta"}),
    ("gradle/libs.versions.toml", {"meta"}),
    ("gradlew", {"meta"}),
    ("gradlew.bat", {"meta"}),
    ("build.gradle.kts", {"meta"}),
    ("settings.gradle.kts", {"meta"}),
    ("build-logic/src/main/kotlin/blueshell.kotlin-conventions.gradle.kts", {"meta"}),
    # Every script reaches the job that runs it. check-diff-buckets.py is
    # ignored because the `changes` job self-tests it on every pull request.
    ("scripts/check-diff-buckets.py", {"ignore"}),
    ("scripts/generate-policy-pdfs.sh", {"ignore"}),
    ("scripts/pandoc-html-br.lua", {"ignore"}),
    ("scripts/__pycache__/check-diff-buckets.cpython-310.pyc", {"ignore"}),
    ("scripts/check-workflow-permissions.py", {"workflows"}),
    ("scripts/write-release-tag.sh", {"workflows"}),
    ("scripts/check-flux-manifests.sh", {"platform-flux"}),
    ("scripts/regenerate-flux-components.sh", {"platform-flux"}),
    ("scripts/seed-vault-from-env.sh", {"platform-flux"}),
    ("scripts/generate_openapi.sh", {"backend"}),
    ("scripts/generate-openapi-local.sh", {"backend"}),
    ("scripts/openapi-common.sh", {"backend"}),
    ("scripts/scrape-public-events.py", {"backend"}),
    ("scripts/seed-stalwart-accounts.sh", {"contract"}),
    ("scripts/check-changeset-compatibility.py", {"changesets"}),
    ("scripts/changeset-sql.sh", {"changesets"}),
    ("config/detekt/detekt.yml", {"backend"}),
    ("services/api/src/main/kotlin/net/blueshell/api/event/domain/EventService.kt",
     {"backend", "measured"}),
    ("services/api/src/main/kotlin/net/blueshell/api/event/web/EventController.kt",
     {"backend", "measured", "contract"}),
    # No controller lives outside a `web` package today. The pattern is what
    # keeps that from mattering, so a fixture holds it open.
    ("services/api/src/main/kotlin/net/blueshell/api/job/JobController.kt",
     {"backend", "measured", "contract"}),
    ("services/api/src/main/resources/application.yaml", {"backend", "contract"}),
    ("services/api/src/main/resources/db/changelog/changes/V70__thing.sql",
     {"backend", "contract", "changesets"}),
    ("services/api/openapi.yaml", {"backend", "contract"}),
    ("services/api/src/main/kotlin/net/blueshell/api/event/web/EventDto.kt",
     {"backend", "measured", "contract"}),
    ("services/api/docker-compose.yml", {"contract"}),
    ("services/api/build.gradle.kts", {"backend", "images"}),
    ("services/api/Dockerfile", {"images"}),
    ("services/.dockerignore", {"images"}),
    ("services/frontend/src/pages/Home.vue", {"frontend", "contract", "measured"}),
    ("services/frontend/src/styles/main.css", {"frontend", "measured"}),
    ("services/frontend/src/assets/logo.svg", {"frontend", "measured"}),
    ("services/frontend/yarn.lock", {"frontend", "images"}),
    ("services/frontend/Dockerfile", {"images"}),
    ("services/frontend/nginx.conf", {"images"}),
    ("services/frontend/index.html", {"frontend", "images"}),
    ("services/frontend/tests/nginx/link-preview.sh", {"frontend", "images"}),
    ("services/frontend/docker-compose.yml", {"contract"}),
    ("services/frontend/package.json", {"frontend", "images"}),
    ("services/frontend/.yarnrc.yml", {"frontend", "images"}),
    ("libs/kotlin-common/src/main/kotlin/Thing.kt", {"backend", "contract", "images"}),
    ("tests/system/src/test/kotlin/SignUpTest.kt", {"contract"}),
    ("docker-compose.yml", {"contract"}),
    ("docker-compose.oidc-e2e.yml", {"contract"}),
    ("services/stalwart/config.dev.toml", {"contract"}),
    (".env", {"contract"}),
    ("services/vault/docker-compose.yml", {"contract"}),
    ("infra/stalwart/accounts.json", {"contract", "images"}),
    ("dev-setup.sh", {"contract"}),
    ("docs/agents/ci.md", {"ignore"}),
    ("platform/docs/runbook.md", {"ignore"}),
    ("gameart/cs2-1.webp", {"ignore"}),
    ("renovate.json", {"ignore"}),
    ("infra/dns/esa-blueshell.nl.zone", {"ignore"}),
    (".idea/misc.xml", {"ignore"}),
    ("README.md", {"ignore"}),
    (".editorconfig", {"ignore"}),
    (".gitattributes", {"ignore"}),
    (".gitignore", {"ignore"}),
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


GATED = {"if": "needs.changes.outputs.backend == 'true'"}
UNGATED = {"if": "always() && needs.api-integration-tests.result != 'skipped'"}

# What an edit to this workflow reaches. `None` is every bucket: the trigger,
# the environment and a job that gates on nothing each belong to no one bucket.
WORKFLOW_EDITS = [
    ("an edited gated job",
     {"jobs": {"a": dict(GATED, run="x")}}, {"jobs": {"a": dict(GATED, run="y")}}, {"backend"}),
    ("an untouched job",
     {"jobs": {"a": dict(GATED, run="x")}}, {"jobs": {"a": dict(GATED, run="x")}}, set()),
    ("an added job",
     {"jobs": {}}, {"jobs": {"a": dict(GATED, run="x")}}, {"backend"}),
    ("a removed job",
     {"jobs": {"a": dict(GATED, run="x")}}, {"jobs": {}}, {"backend"}),
    ("an edited coverage gate",
     {"jobs": {"a": {"if": "needs.changes.outputs.measured == 'true'", "run": "x"}}},
     {"jobs": {"a": {"if": "needs.changes.outputs.measured == 'true'", "run": "y"}}},
     {"measured", "backend", "frontend"}),
    ("an edited job that gates on nothing",
     {"jobs": {"a": dict(UNGATED, run="x")}}, {"jobs": {"a": dict(UNGATED, run="y")}}, None),
    ("an edited trigger",
     {"on": ["pull_request"], "jobs": {}}, {"on": ["push"], "jobs": {}}, None),
]


def self_test():
    """Every fixture lands where it says, and a stray path is reported."""
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
    for label, was, head, expected in WORKFLOW_EDITS:
        if reached_by(was, head) != expected:
            print(f"self-test FAILED: {label} reaches {reached_by(was, head)}, expected {expected}")
            return 1
    # A `!` pattern subtracts from its own bucket. It must never add a path to
    # one, which is what dorny/paths-filter did: there `!a/**` matched every
    # path outside `a`, so `backend` claimed the whole repository (#1453).
    for name, patterns in buckets.items():
        negated = [p for p in patterns if p.startswith("!")]
        if negated and matches("nothing/in/any/bucket.txt", patterns):
            print(f"self-test FAILED: `{name}` claims a path only its {negated[0]} matches")
            return 1
    stray = "services/worker/src/main/kotlin/Worker.kt"
    if not unmatched([stray], buckets):
        print(f"self-test FAILED: {stray} belongs to no bucket and went unreported")
        return 1
    if unmatched(["services/api/openapi.yaml"], buckets):
        print("self-test FAILED: a bucketed path was reported as unmatched")
        return 1
    # A pattern nothing exercises is a pattern nothing would miss. Either the
    # filter has an entry it does not need, or the table is behind it.
    for pattern in unexercised(buckets):
        print(f"self-test FAILED: no fixture exercises {pattern}")
    if unexercised(buckets):
        return 1
    print(f"self-test ok: {len(FIXTURES)} fixtures place correctly, would report {stray}")
    return 0


def decide(base, head):
    """
    Every output the `changes` job publishes, as `name=value` lines.

    Three things mean every bucket: a `meta` path, a changed path in no bucket
    at all, and a change to validate.yml that no single job owns.
    """
    buckets = buckets_of()
    paths = changed(base, head)
    stray = unmatched(paths, buckets)
    reached = workflow_buckets(base)

    on = {name: any(matches(path, buckets[name]) for path in paths) for name in GATES}
    if stray:
        print("::warning::these changed paths are in no bucket, so the whole suite runs: "
              + " ".join(stray))
    if any(matches(path, buckets["meta"]) for path in paths) or stray or reached is None:
        on = dict.fromkeys(GATES, True)
    elif reached:
        on.update(dict.fromkeys(reached & set(GATES), True))

    on["app"] = any(on[name] for name in APP)
    return [f"{name}={str(value).lower()}" for name, value in on.items()]


def main():
    if "--self-test" in sys.argv:
        return self_test()
    args = dict(zip(sys.argv[1::2], sys.argv[2::2]))
    base, head = args.get("--base"), args.get("--head", "HEAD")
    if not base:
        print("::error::--base is required")
        return 1
    lines = decide(base, head)
    for line in lines:
        print(line)
    # The step reads these as outputs; the same lines go to the log above, so
    # a run says what it decided without opening the job's output.
    destination = os.environ.get("GITHUB_OUTPUT")
    if destination:
        with open(destination, "a") as handle:
            handle.write("\n".join(lines) + "\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
