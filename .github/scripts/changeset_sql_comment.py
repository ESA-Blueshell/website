#!/usr/bin/env python3
"""
Posts the SQL a changeset will run, as one comment found again by its marker.
Runs in pr-report.yml, which holds the write token a fork's run does not.
"""

import json
import os
import subprocess
import sys
from pathlib import Path

MARKER = "<!-- changeset-sql -->"
LIMIT = 50_000


def gh(*args, **kw):
    return subprocess.run(["gh", *args], capture_output=True, text=True, **kw)


def existing_comment(repo, number):
    out = gh("api", f"repos/{repo}/issues/{number}/comments", "--paginate",
             "--jq", ".[] | {id, body}")
    for line in out.stdout.splitlines():
        if not line.strip():
            continue
        c = json.loads(line)
        if MARKER in (c.get("body") or ""):
            return c["id"]
    return None


def main():
    repo = os.environ["REPO"]
    number = os.environ["PR_NUMBER"]
    sql_path = Path(os.environ.get("SQL_PATH", "coverage-artifacts/changeset-sql/changeset.sql"))
    files_path = sql_path.with_suffix(".files")

    if not sql_path.exists() or not sql_path.read_text().strip():
        print("no changeset SQL in this run; nothing to comment")
        return 0

    sql = sql_path.read_text().strip()
    truncated = len(sql) > LIMIT
    if truncated:
        sql = sql[:LIMIT]

    names = files_path.read_text().split() if files_path.exists() else []
    heading = ", ".join(f"`{n}`" for n in names) or "this branch"

    body = (
        f"{MARKER}\n"
        f"### The SQL {heading} will run\n\n"
        "Rendered against a database built from the baseline plus everything on "
        "the base branch, so this is what the changeset adds and nothing else. "
        "Generated, not applied.\n\n"
        f"```sql\n{sql}\n```\n"
    )
    if truncated:
        body += f"\nTruncated at {LIMIT} characters.\n"

    body_file = Path("changeset-sql-comment.md")
    body_file.write_text(body)

    cid = existing_comment(repo, number)
    if cid:
        r = gh("api", "-X", "PATCH", f"repos/{repo}/issues/comments/{cid}",
               "-F", f"body=@{body_file}")
    else:
        r = gh("api", "-X", "POST", f"repos/{repo}/issues/{number}/comments",
               "-F", f"body=@{body_file}")
    if r.returncode != 0:
        print(r.stderr, file=sys.stderr)
        return 1
    print(f"{'updated' if cid else 'posted'} the changeset SQL comment")
    return 0


if __name__ == "__main__":
    sys.exit(main())
