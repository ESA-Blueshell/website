#!/usr/bin/env python3
"""Report a pull request's shape and coverage in one comment.

Replaces the body splice that `pr_diff_stats.py` did: a comment can carry the
coverage tables too, and it stops a bot rewriting the author's description.

Two modes. `render` builds the body and upserts the comment, found again by
MARKER so a second push updates one comment rather than leaving a trail.
`summarize` writes the normalised coverage of a run to one JSON file, which is
what a merge-queue run caches for pull requests to compare against.

Change counts come from the pulls/{n}/files API: already a merge-base diff with
renames resolved, and its `patch` hunks are what patch coverage reads, so no
head checkout is needed anywhere in this script.

Reads GH_TOKEN, REPO, PR_NUMBER, COVERAGE_DIR, BASELINE_PATH, BASE_REF.
"""

from __future__ import annotations

import json
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ElementTree
from collections import defaultdict
from pathlib import Path

MARKER = "<!-- pr-report -->"

# Caps, so a large pull request does not bury the coverage under a list of lines.
MAX_FILES = 40
MAX_LINES_PER_FILE = 15
BAR_WIDTH = 10
COMMENT_LIMIT = 65000

ADD_GLYPH = "█"
DEL_GLYPH = "░"

METRICS = ("statements", "branches", "functions", "lines")
METRIC_HEADS = ("Statements", "Branches", "Functions", "Lines")

# JaCoCo counts instructions and methods where istanbul counts statements and
# functions. Neither pair is the same measurement, but they answer the same
# question per column, which is what lets one table hold both toolchains.
JACOCO_COUNTERS = {
    "INSTRUCTION": "statements",
    "BRANCH": "branches",
    "METHOD": "functions",
    "LINE": "lines",
}

SERVICE_ORDER = ["api", "frontend", "system-tests", "libs", "platform", "ci", "repo", "docs"]
CATEGORY_ORDER = [
    "prod", "unit", "integration", "e2e", "system",
    "fixtures", "infra", "build", "docs", "generated", "other",
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

# Each suite names the artifact it arrives in, because two of the four carry a
# file called coverage-summary.json and only the directory tells them apart.
SUITES = (
    ("api unit", "api-unit-test-reports", "jacoco", "jacocoTestReport.xml"),
    ("api integration", "api-integration-coverage", "jacoco", "jacocoIntegrationTestReport.xml"),
    ("frontend unit", "frontend-unit-coverage", "istanbul", "coverage-summary.json"),
    ("frontend e2e", "frontend-e2e-coverage", "istanbul", "coverage-summary.json"),
)


# --------------------------------------------------------------------------
# taxonomy — the rules file `pr_diff_stats.py` used, read the same way
# --------------------------------------------------------------------------

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


RULE_KEYS = ("glob", "service", "category")


def _scalar(value: str, where: str) -> str:
    value = value.strip()
    if value[:1] in ("'", '"'):
        if len(value) < 2 or value[-1] != value[0]:
            raise ValueError(f"{where}: unterminated quoted value")
        return value[1:-1]
    if "#" in value:
        raise ValueError(f"{where}: quote any value containing '#'")
    return value


def parse_rules(text: str, source: str = "<rules>") -> list[dict[str, str]]:
    """Read a sequence of mappings with three scalar keys, raising on anything else.

    Not PyYAML: it is absent from the runner, and pip installing it would put a
    network dependency inside a workflow that holds a write token.
    """
    entries: list[dict[str, str]] = []
    for lineno, raw in enumerate(text.splitlines(), start=1):
        stripped = raw.strip()
        if not stripped or stripped.startswith("#"):
            continue
        where = f"{source}:{lineno}"
        if stripped.startswith("- "):
            entries.append({})
            stripped = stripped[2:]
        elif not entries:
            raise ValueError(f"{where}: mapping before any '-' entry")
        key, sep, value = stripped.partition(":")
        key = key.strip()
        if not sep or key not in RULE_KEYS:
            raise ValueError(f"{where}: expected one of {RULE_KEYS}, got {key!r}")
        if key in entries[-1]:
            raise ValueError(f"{where}: duplicate key {key!r}")
        entries[-1][key] = _scalar(value, where)

    for index, entry in enumerate(entries, start=1):
        missing = [k for k in RULE_KEYS if k not in entry]
        if missing:
            raise ValueError(f"{source}: entry {index} is missing {missing}")
    if not entries:
        raise ValueError(f"{source}: no rules defined")
    return entries


def load_rules(path: Path) -> list[tuple[re.Pattern[str], str, str]]:
    entries = parse_rules(path.read_text(encoding="utf-8"), path.name)
    return [(glob_to_regex(e["glob"]), e["service"], e["category"]) for e in entries]


def classify(path: str, rules) -> tuple[str, str]:
    for pattern, service, category in rules:
        if pattern.match(path):
            return service, category
    return "other", "other"


# --------------------------------------------------------------------------
# coverage — two report formats, one shape
# --------------------------------------------------------------------------

def _pct(covered: int, total: int) -> float | None:
    """None rather than 0.0 for an unmeasured counter: they read very differently."""
    return None if total == 0 else covered / total * 100


def parse_jacoco(xml_text: str) -> tuple[dict[str, float | None], dict[str, set[int]]]:
    """A JaCoCo report's totals, and the lines it saw covered, keyed by source tail.

    Only the report-level counters are read for the totals: JaCoCo repeats each
    counter at package, class and method level, so summing every `counter`
    element would multiply the same numbers several times over.
    """
    root = ElementTree.fromstring(xml_text)
    totals: dict[str, float | None] = {metric: None for metric in METRICS}
    for counter in root.findall("counter"):
        metric = JACOCO_COUNTERS.get(counter.get("type", ""))
        if metric is None:
            continue
        missed = int(counter.get("missed", "0"))
        covered = int(counter.get("covered", "0"))
        totals[metric] = _pct(covered, missed + covered)

    covered_lines: dict[str, set[int]] = defaultdict(set)
    seen_lines: dict[str, set[int]] = defaultdict(set)
    for package in root.iter("package"):
        package_name = package.get("name", "")
        for source in package.findall("sourcefile"):
            tail = f"{package_name}/{source.get('name', '')}".lstrip("/")
            for line in source.findall("line"):
                number = int(line.get("nr", "0"))
                seen_lines[tail].add(number)
                # `ci` is covered instructions on that line; zero means the line
                # exists in the bytecode and was never executed.
                if int(line.get("ci", "0")) > 0:
                    covered_lines[tail].add(number)
    return totals, _measured(seen_lines, covered_lines)


def parse_istanbul(json_text: str) -> dict[str, float | None]:
    """The `total` entry of istanbul's json-summary reporter."""
    parsed = json.loads(json_text)
    total = parsed.get("total")
    if not isinstance(total, dict):
        raise ValueError('coverage summary carries no usable "total" entry')
    totals: dict[str, float | None] = {}
    for metric in METRICS:
        entry = total.get(metric)
        if isinstance(entry, dict) and isinstance(entry.get("pct"), (int, float)):
            totals[metric] = float(entry["pct"])
        else:
            totals[metric] = None
    return totals


def parse_lcov(text: str) -> dict[str, dict[str, set[int]]]:
    """Every `DA:` record in an lcov report, as measured and covered line sets."""
    covered: dict[str, set[int]] = defaultdict(set)
    seen: dict[str, set[int]] = defaultdict(set)
    current: str | None = None
    for raw in text.splitlines():
        line = raw.strip()
        if line.startswith("SF:"):
            current = line[3:].replace("\\", "/")
        elif line.startswith("DA:") and current is not None:
            number, _, hits = line[3:].partition(",")
            try:
                position = int(number)
                count = int(hits.split(",")[0])
            except ValueError:
                continue
            seen[current].add(position)
            if count > 0:
                covered[current].add(position)
        elif line == "end_of_record":
            current = None
    return _measured(seen, covered)


def _measured(seen, covered) -> dict[str, dict[str, set[int]]]:
    return {path: {"seen": lines, "covered": covered.get(path, set())} for path, lines in seen.items()}


def find_one(root: Path, name: str) -> Path | None:
    """The first file called `name` under `root`, or None.

    Searched rather than named outright: an artifact's internal layout is
    whatever common ancestor the upload step happened to strip.
    """
    if not root.is_dir():
        return None
    matches = sorted(root.rglob(name))
    return matches[0] if matches else None


def read_suites(coverage_dir: Path) -> tuple[dict[str, dict[str, float | None]], dict[str, dict[str, set[int]]], list[str]]:
    """Each suite's totals, the union of every line-level report, and what was missing."""
    totals: dict[str, dict[str, float | None]] = {}
    measured: dict[str, dict[str, set[int]]] = {}
    notes: list[str] = []

    for label, artifact, kind, filename in SUITES:
        root = coverage_dir / artifact
        report = find_one(root, filename)
        if report is None:
            notes.append(f"No coverage reached this report for **{label}** (`{artifact}`).")
            continue
        try:
            if kind == "jacoco":
                suite_totals, suite_lines = parse_jacoco(report.read_text(encoding="utf-8"))
            else:
                suite_totals = parse_istanbul(report.read_text(encoding="utf-8"))
                lcov = find_one(root, "lcov.info")
                suite_lines = parse_lcov(lcov.read_text(encoding="utf-8")) if lcov else {}
        except Exception as error:  # a malformed report is reported, never silently zero
            notes.append(f"Could not read **{label}** coverage: {error}.")
            continue
        totals[label] = suite_totals
        for path, entry in suite_lines.items():
            existing = measured.setdefault(path, {"seen": set(), "covered": set()})
            existing["seen"] |= entry["seen"]
            existing["covered"] |= entry["covered"]

    return totals, measured, notes


# --------------------------------------------------------------------------
# patch coverage — the lines this pull request added, and whether they ran
# --------------------------------------------------------------------------

HUNK_HEADER = re.compile(r"^@@ -\d+(?:,\d+)? \+(\d+)(?:,\d+)? @@")


def added_lines(patch: str) -> list[int]:
    """The new-file line numbers a `patch` hunk body adds."""
    lines: list[int] = []
    position = 0
    for raw in patch.splitlines():
        header = HUNK_HEADER.match(raw)
        if header:
            position = int(header.group(1))
            continue
        if raw.startswith("+"):
            lines.append(position)
            position += 1
        elif raw.startswith("-") or raw.startswith("\\"):
            continue
        else:
            position += 1
    return lines


def match_paths(measured: dict, changed: list[str]) -> dict[str, str]:
    """Map each coverage path onto the changed file it names, by longest common tail.

    Every report spells a path its own way — JaCoCo by package, lcov by whatever
    root the run had — and none of them is repository-relative. Matching on the
    tail is what survives that without a per-toolchain prefix table. An ambiguous
    tail is dropped rather than guessed.
    """
    resolved: dict[str, str] = {}
    for path in measured:
        tail = path.lstrip("./")
        candidates = [c for c in changed if c == tail or c.endswith("/" + tail)]
        if len(candidates) != 1:
            # Try the other direction: a coverage path carrying a deeper root
            # than the repository one (an absolute path from the runner).
            candidates = [c for c in changed if tail.endswith("/" + c) or tail == c]
        if len(candidates) == 1:
            resolved[path] = candidates[0]
    return resolved


def patch_coverage(files: list[dict], measured: dict) -> tuple[int, int, list[tuple[str, list[int]]]]:
    """How many added lines coverage measured, how many it saw run, and which it did not.

    A line counts only when the file it is in appears in a coverage report with a
    record for that exact line: a line no report measures (a comment, a blank, a
    file outside every `include`) is absent from the denominator rather than
    counted as uncovered.
    """
    resolved = match_paths(measured, [f["filename"] for f in files])
    by_repo_path: dict[str, dict[str, set[int]]] = {}
    for coverage_path, repo_path in resolved.items():
        entry = by_repo_path.setdefault(repo_path, {"seen": set(), "covered": set()})
        entry["seen"] |= measured[coverage_path]["seen"]
        entry["covered"] |= measured[coverage_path]["covered"]

    total = 0
    covered = 0
    uncovered: list[tuple[str, list[int]]] = []
    for entry in files:
        name = entry["filename"]
        record = by_repo_path.get(name)
        if record is None or not entry.get("patch"):
            continue
        missing: list[int] = []
        for line in added_lines(entry["patch"]):
            if line not in record["seen"]:
                continue
            total += 1
            if line in record["covered"]:
                covered += 1
            else:
                missing.append(line)
        if missing:
            uncovered.append((name, missing))
    uncovered.sort(key=lambda item: (-len(item[1]), item[0]))
    return covered, total, uncovered


# --------------------------------------------------------------------------
# rendering — one pure function over plain data
# --------------------------------------------------------------------------

def _cell(glyph: str, value: int, scale: int) -> str:
    """Empty rather than a zero, so the eye goes to the rows that moved."""
    if value == 0:
        return ""
    blocks = max(1, round(value / scale * BAR_WIDTH)) if scale > 0 else 1
    return f"{glyph * blocks} {value}"


def _plural(count: int, noun: str) -> str:
    return f"{count} {noun}" if count == 1 else f"{count} {noun}s"


def _pct_text(value: float | None) -> str:
    return "—" if value is None else f"{value:.2f}%"


def _delta_text(current: float | None, base: float | None) -> str:
    if current is None or base is None:
        return "—"
    difference = current - base
    if abs(difference) < 0.005:
        return "0.00"
    return f"{difference:+.2f}"


def changes_section(files: list[dict], rules) -> str:
    buckets: dict[tuple[str, str], dict[str, int]] = defaultdict(lambda: {"add": 0, "del": 0, "files": 0})
    for entry in files:
        service, category = classify(entry["filename"], rules)
        cell = buckets[(service, category)]
        cell["add"] += entry.get("additions", 0)
        cell["del"] += entry.get("deletions", 0)
        cell["files"] += 1
    if not buckets:
        return "## Changes\n\nNo files changed."

    def service_key(name: str) -> tuple[int, str]:
        if name in SERVICE_ORDER:
            return (SERVICE_ORDER.index(name), "")
        return (len(SERVICE_ORDER) + (1 if name == "other" else 0), name)

    def category_key(name: str) -> tuple[int, str]:
        return (CATEGORY_ORDER.index(name), "") if name in CATEGORY_ORDER else (len(CATEGORY_ORDER), name)

    # Hand-written rows set the scale so generated churn cannot dwarf them, but a
    # dependency bump has none, so fall back to generated rather than draw nothing.
    scale = max((c["add"] + c["del"] for (_, cat), c in buckets.items() if cat not in EXCLUDED), default=0)
    if scale == 0:
        scale = max((c["add"] + c["del"] for c in buckets.values()), default=1)

    rows = ["| Bucket | Files | Added | Removed |", "|---|---:|---:|---:|"]
    for service in sorted({s for s, _ in buckets}, key=service_key):
        for category in sorted((c for s, c in buckets if s == service), key=category_key):
            cell = buckets[(service, category)]
            label = f"{service} · {CATEGORY_LABELS.get(category, category)}"
            mark = " ~" if category in EXCLUDED else ""
            rows.append(
                f"| {label}{mark} | {cell['files']} "
                f"| {_cell(ADD_GLYPH, cell['add'], scale)} | {_cell(DEL_GLYPH, cell['del'], scale)} |"
            )

    kept = {k: sum(c[k] for (_, cat), c in buckets.items() if cat not in EXCLUDED) for k in ("add", "del", "files")}
    generated = any(cat in EXCLUDED for _, cat in buckets)
    total_label = f"**total**{' (generated excluded)' if generated else ''}"
    # In the table rather than a sentence under it, so the column a reader is
    # already scanning is where the total is.
    rows.append(f"| {total_label} | {kept['files']} | +{kept['add']} | −{kept['del']} |")

    prod = sum(c["add"] for (_, cat), c in buckets.items() if cat in PROD_CATEGORIES)
    tests = sum(c["add"] for (_, cat), c in buckets.items() if cat in TEST_CATEGORIES)
    if prod == 0:
        ratio = "**No production lines added.**" if tests else "**No production or test lines added.**"
    else:
        ratio = f"**{tests / prod:.2f} test lines per prod line.**"

    return "\n\n".join(["## Changes", "\n".join(rows), ratio])


def coverage_section(totals, baseline, base_ref: str) -> str:
    if not totals:
        return "## Coverage\n\nNo coverage summary reached this report."

    head = "|  | " + " | ".join(METRIC_HEADS) + " |"
    rows = [head, "|---|---:|---:|---:|---:|"]
    base_totals = (baseline or {}).get("suites", {})
    commit = (baseline or {}).get("commit")
    base_label = f"{base_ref}" + (f" `{commit[:7]}`" if commit else "")

    for label, _, _, _ in SUITES:
        suite = totals.get(label)
        if suite is None:
            continue
        rows.append(f"| **{label}** | " + " | ".join(_pct_text(suite[m]) for m in METRICS) + " |")
        base_suite = base_totals.get(label)
        if base_suite is None:
            rows.append(f"| {base_label} | " + " | ".join("—" for _ in METRICS) + " |")
        else:
            rows.append(f"| {base_label} | " + " | ".join(_pct_text(base_suite.get(m)) for m in METRICS) + " |")
            rows.append("| Δ | " + " | ".join(_delta_text(suite[m], base_suite.get(m)) for m in METRICS) + " |")

    parts = ["## Coverage", "\n".join(rows)]
    if baseline is None:
        parts.append(f"No baseline is cached from `{base_ref}` yet, so there is nothing to compare against.")
    return "\n\n".join(parts)


def patch_section(covered: int, total: int, uncovered) -> list[str]:
    if total == 0:
        return ["**Patch coverage:** this pull request changes no line that coverage measures."]
    headline = (
        f"**Patch coverage: {covered / total * 100:.1f}%**, "
        f"{covered} of {total} changed lines covered."
    )
    if not uncovered:
        return [headline]

    missed = sum(len(lines) for _, lines in uncovered)
    shown = uncovered[:MAX_FILES]
    listed = []
    for path, lines in shown:
        head_lines = ", ".join(str(line) for line in lines[:MAX_LINES_PER_FILE])
        rest = len(lines) - MAX_LINES_PER_FILE
        listed.append(f"`{path}` {head_lines}{f' and {rest} more' if rest > 0 else ''}")
    hidden = len(uncovered) - len(shown)
    if hidden > 0:
        listed.append(f"and {_plural(hidden, 'file')} more")

    details = "\n".join([
        f"<details><summary>{_plural(missed, 'uncovered line')} in this pull request</summary>",
        "",
        "\n".join(listed),
        "",
        "</details>",
    ])
    return [headline, details]


def render(files, rules, totals, baseline, base_ref, patch, notes) -> str:
    covered, total, uncovered = patch
    sections = [
        changes_section(files, rules),
        coverage_section(totals, baseline, base_ref),
        *patch_section(covered, total, uncovered),
        # One blockquote rather than one per note, so a run missing three
        # artifacts does not read as three separate complaints.
        "\n".join(f"> {note}" for note in notes),
        MARKER,
    ]
    body = "\n\n".join(part for part in sections if part)
    if len(body) > COMMENT_LIMIT:
        # Drop the uncovered-line list first: it is the longest part and the
        # least load-bearing of the three.
        sections = [
            changes_section(files, rules),
            coverage_section(totals, baseline, base_ref),
            patch_section(covered, total, [])[0],
            "> The list of uncovered lines was too long for one comment.",
            MARKER,
        ]
        body = "\n\n".join(part for part in sections if part)
    return body


# --------------------------------------------------------------------------
# the impure edges
# --------------------------------------------------------------------------

def gh(args: list[str], stdin: str | None = None) -> str:
    result = subprocess.run(
        ["gh", *args], capture_output=True, text=True, input=stdin, check=True,
    )
    return result.stdout


def fetch_files(repo: str, pr: str) -> list[dict]:
    """One JSON object per line, so page boundaries do not need stitching."""
    out = gh(["api", "--paginate", f"repos/{repo}/pulls/{pr}/files", "--jq", ".[]|@json"])
    return [json.loads(line) for line in out.splitlines() if line.strip()]


def upsert_comment(repo: str, pr: str, body: str) -> str:
    out = gh(["api", "--paginate", f"repos/{repo}/issues/{pr}/comments", "--jq", ".[]|@json"])
    for line in out.splitlines():
        if not line.strip():
            continue
        comment = json.loads(line)
        if MARKER in (comment.get("body") or ""):
            if (comment.get("body") or "") == body:
                return "unchanged"
            gh(["api", "--method", "PATCH", f"repos/{repo}/issues/comments/{comment['id']}",
                "--input", "-"], stdin=json.dumps({"body": body}))
            return "updated"
    gh(["api", f"repos/{repo}/issues/{pr}/comments", "--input", "-"],
       stdin=json.dumps({"body": body}))
    return "posted"


def read_baseline(path: str | None) -> dict | None:
    if not path:
        return None
    file = Path(path)
    if not file.is_file():
        return None
    try:
        parsed = json.loads(file.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return None
    return parsed if isinstance(parsed.get("suites"), dict) else None


def command_summarize(coverage_dir: Path, out_path: Path, commit: str) -> int:
    """Write this run's coverage as the baseline a later pull request compares against."""
    totals, _, notes = read_suites(coverage_dir)
    for note in notes:
        print(note, file=sys.stderr)
    if not totals:
        print("No coverage found; writing no baseline.", file=sys.stderr)
        return 1
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(json.dumps({"commit": commit, "suites": totals}, indent=2), encoding="utf-8")
    print(f"Wrote a baseline for {len(totals)} suite(s) to {out_path}.")
    return 0


def command_render(repo: str, pr: str, coverage_dir: Path, baseline_path: str | None,
                   base_ref: str, dry_run: bool) -> int:
    rules = load_rules(Path(__file__).resolve().parents[1] / "pr-report-rules.yml")
    files = fetch_files(repo, pr)
    totals, measured, notes = read_suites(coverage_dir)
    baseline = read_baseline(baseline_path)
    patch = patch_coverage(files, measured)
    body = render(files, rules, totals, baseline, base_ref, patch, notes)

    if dry_run:
        print(body)
        return 0
    print(f"Comment {upsert_comment(repo, pr, body)} on {repo}#{pr}.")
    return 0


def main() -> int:
    args = [a for a in sys.argv[1:] if not a.startswith("-")]
    mode = args[0] if args else "render"
    dry_run = "--dry-run" in sys.argv
    coverage_dir = Path(os.environ.get("COVERAGE_DIR", "coverage-artifacts"))

    if mode == "summarize":
        return command_summarize(
            coverage_dir,
            Path(os.environ.get("BASELINE_PATH", "coverage-baseline/baseline.json")),
            os.environ.get("BASELINE_COMMIT", ""),
        )

    repo, pr = os.environ.get("REPO"), os.environ.get("PR_NUMBER")
    if not repo or not pr:
        print("REPO and PR_NUMBER are required", file=sys.stderr)
        return 1
    return command_render(
        repo, pr, coverage_dir,
        os.environ.get("BASELINE_PATH"),
        os.environ.get("BASE_REF", "main"),
        dry_run,
    )


if __name__ == "__main__":
    sys.exit(main())
