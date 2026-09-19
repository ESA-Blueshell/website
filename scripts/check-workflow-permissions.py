#!/usr/bin/env python3
"""
Checks that a job calling a reusable workflow grants every permission that
workflow's own jobs ask for.

GitHub caps a called workflow at its caller's permissions and refuses the whole
run at startup when it asks for more, before any job reports. Nothing in the
pull request that raises the permission sees it, because the caller only runs
on main.

--self-test proves this can still fail, the way check-flux-manifests.sh does.
"""

import sys
from pathlib import Path

import yaml

WORKFLOWS = Path(".github/workflows")

# Ordered weakest to strongest. A caller granting `write` satisfies a callee
# asking for `read`; `none` satisfies only `none`.
RANK = {"none": 0, "read": 1, "write": 2}


def jobs_of(doc):
    return (doc or {}).get("jobs") or {}


def needed(doc):
    """The strongest level each permission reaches across a workflow's jobs."""
    top = (doc or {}).get("permissions") or {}
    if isinstance(top, str):
        top = {}
    worst = dict(top)
    for job in jobs_of(doc).values():
        perms = (job or {}).get("permissions") or {}
        if isinstance(perms, str):
            continue
        for name, level in perms.items():
            if RANK.get(level, 0) > RANK.get(worst.get(name, "none"), 0):
                worst[name] = level
    return worst


def shortfall(granted, required):
    missing = []
    for name, level in required.items():
        if RANK.get(level, 0) > RANK.get(granted.get(name, "none"), 0):
            missing.append((name, level, granted.get(name, "none")))
    return missing


def check(paths):
    findings = []
    for path in sorted(paths):
        doc = yaml.safe_load(path.read_text())
        for job_name, job in jobs_of(doc).items():
            uses = (job or {}).get("uses")
            if not isinstance(uses, str) or not uses.startswith("./"):
                continue
            called = Path(uses.removeprefix("./"))
            if not called.exists():
                findings.append(f"{path.name}: job `{job_name}` calls {uses}, which does not exist")
                continue
            granted = (job or {}).get("permissions") or {}
            if isinstance(granted, str):
                granted = {}
            for name, level, has in shortfall(granted, needed(yaml.safe_load(called.read_text()))):
                findings.append(
                    f"{path.name}: job `{job_name}` calls {called.name}, which needs "
                    f"`{name}: {level}`, but the caller grants `{name}: {has}`. "
                    f"The whole run will fail to start."
                )
    return findings


def self_test():
    """A caller short of a permission its callee needs must be reported."""
    caller = {"jobs": {"publish": {"uses": "./.github/workflows/_probe.yml",
                                   "permissions": {"contents": "read"}}}}
    callee = {"jobs": {"sign": {"permissions": {"id-token": "write"}}}}
    missing = shortfall(caller["jobs"]["publish"]["permissions"], needed(callee))
    if not missing:
        print("self-test FAILED: a missing permission went unreported")
        return 1
    if shortfall({"contents": "read", "id-token": "write"}, needed(callee)):
        print("self-test FAILED: a satisfied permission was reported as missing")
        return 1
    print(f"self-test ok: would report {missing[0][0]}")
    return 0


def main():
    if "--self-test" in sys.argv:
        return self_test()
    findings = check(WORKFLOWS.glob("*.yml"))
    for line in findings:
        print(f"::error::{line}")
    if findings:
        return 1
    print("every reusable-workflow caller grants what it calls")
    return 0


if __name__ == "__main__":
    sys.exit(main())
