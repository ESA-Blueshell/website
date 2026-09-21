#!/usr/bin/env python3
"""
Refuses a changeset the previously released version cannot read.

The migration runs before any new pod serves, so the version already in
production meets the new schema first and keeps serving against it for the
whole canary analysis. Dropping a column it maps breaks what is serving, and
reverting the release does not undo it.

Reads changeTypes structurally. A `dropTable` inside a `rollback:` block is not
a drop, and no amount of SQL formatting changes what a YAML key says.

--self-test proves this can still fail, the way check-flux-manifests.sh does.
"""

import argparse
import subprocess
import sys
from pathlib import Path

import yaml

CHANGES = Path("services/api/src/main/resources/db/changelog/changes")

# What the previous release cannot survive. Adding a table, a nullable column or
# an index is absent on purpose: none of them change what the old version reads.
BREAKING = {
    "dropColumn": "drops a column the previous release may still select",
    "dropTable": "drops a table the previous release may still read",
    "renameColumn": "renames a column, which reads as a drop to the previous release",
    "renameTable": "renames a table, which reads as a drop to the previous release",
    "dropPrimaryKey": "drops a primary key the previous release's writes rely on",
    "addNotNullConstraint": "rejects the rows the previous release still writes",
    "addForeignKeyConstraint": "rejects the rows the previous release still writes",
}

OVERRIDE_LABEL = "schema:breaks-the-previous-release"


def changed_files(base):
    """Changeset files this branch adds or edits, against the base."""
    out = subprocess.run(
        ["git", "diff", "--name-only", "--diff-filter=AM", f"{base}...HEAD", "--", str(CHANGES)],
        capture_output=True, text=True, check=True,
    )
    return [Path(p) for p in out.stdout.split() if p.endswith((".yaml", ".yml"))]


def changesets(doc):
    for entry in (doc or {}).get("databaseChangeLog") or []:
        if isinstance(entry, dict) and "changeSet" in entry:
            yield entry["changeSet"] or {}


def ops(change_set):
    """The changeTypes a changeset applies. `rollback:` is deliberately not read."""
    for change in change_set.get("changes") or []:
        if isinstance(change, dict):
            for name, body in change.items():
                yield name, (body or {})


def table_of(body):
    return body.get("tableName") or body.get("baseTableName")


def inspect(docs):
    """docs: [(label, parsed)]. Returns findings."""
    # A constraint on a table this same set creates cannot break a version that
    # does not know the table. Gathered first, so file order does not matter.
    fresh = {
        table_of(body)
        for _, doc in docs
        for cs in changesets(doc)
        for name, body in ops(cs)
        if name == "createTable"
    }

    findings = []
    for label, doc in docs:
        for cs in changesets(doc):
            for name, body in ops(cs):
                if name not in BREAKING:
                    continue
                table = table_of(body)
                if table in fresh:
                    continue
                where = f"{label}: changeset `{cs.get('id', '?')}`"
                column = body.get("columnName") or body.get("baseColumnNames")
                target = f"{table}.{column}" if column else str(table)
                findings.append(f"{where} {name} on {target} — {BREAKING[name]}")
    return findings


def self_test():
    breaking = yaml.safe_load("""
      databaseChangeLog:
        - changeSet:
            id: drops-a-live-column
            changes:
              - dropColumn: {tableName: boards, columnName: image}
    """)
    rollback_only = yaml.safe_load("""
      databaseChangeLog:
        - changeSet:
            id: makes-a-table
            changes:
              - createTable: {tableName: role_changes}
              - addForeignKeyConstraint: {baseTableName: role_changes, baseColumnNames: subject_user_id}
            rollback:
              - dropTable: {tableName: role_changes}
    """)
    if not inspect([("probe", breaking)]):
        print("self-test FAILED: a dropped column went unreported")
        return 1
    leaked = inspect([("probe", rollback_only)])
    if leaked:
        print(f"self-test FAILED: reported a safe changeset — {leaked[0]}")
        return 1
    print("self-test ok: reports the drop, and not the rollback or the fresh table's key")
    return 0


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default="origin/main")
    ap.add_argument("--labels", default="", help="the pull request's labels, comma or newline separated")
    ap.add_argument("--self-test", action="store_true")
    args = ap.parse_args()

    if args.self_test:
        return self_test()

    files = changed_files(args.base)
    if not files:
        print("no changeset added or edited")
        return 0

    findings = inspect([(f.name, yaml.safe_load(f.read_text())) for f in files if f.exists()])
    if not findings:
        print(f"{len(files)} changeset file(s), nothing the previous release cannot read")
        return 0

    overridden = OVERRIDE_LABEL in args.labels.replace(",", "\n").split()
    for line in findings:
        print(f"::{'warning' if overridden else 'error'}::{line}")

    if overridden:
        print(f"::warning::allowed by the `{OVERRIDE_LABEL}` label")
        return 0

    print(
        f"::error::A removal takes two releases: the first stops reading, the second drops. "
        f"See ADR-026. If this really is safe, label the pull request `{OVERRIDE_LABEL}`."
    )
    return 1


if __name__ == "__main__":
    sys.exit(main())
