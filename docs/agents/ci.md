# CI

What runs, when, and how to read the result.

## Validate runs on pull requests, not on branch pushes

`Validate` is every test in the repo: api unit and integration, acceptance features, six
system-test shards, frontend unit and end-to-end, typecheck, lint, image builds. It triggers on
`pull_request`, filtered to a diff that touches code. The header comment in
`.github/workflows/validate.yml` says why.

So **pushing a branch runs nothing**. Open the PR and the suite starts; push again and the run
in flight is superseded by one on the new head. To validate a branch with no PR open, ask for a
run:

```sh
gh workflow run validate.yml --ref <branch>
```

## A check belongs to a commit, not to a branch

GitHub attaches check-runs to a **sha**, and a PR shows only the ones on its head. A run against
an earlier commit of the same branch is invisible on the PR page even though it tested that PR's
code — so `gh pr checks` listing no tests is not evidence the suite was skipped. Ask the commit
instead:

```sh
gh api "repos/{owner}/{repo}/commits/<sha>/check-runs" --jq '.check_runs[] | "\(.conclusion) \(.name)"'
```

Two readings that trip up a first look:

- **Cancelled is not failed.** Superseded runs are cancelled, and their check-runs stay on the
  sha they belong to. A red mark on an intermediate commit is usually that.
- **`UNSTABLE` is not failure.** It means something on the head is cancelled, neutral or still
  running. Only a `FAILURE` conclusion is a failure.

## Changed lines are covered

`Changed lines are covered` fails a pull request when a changed line that the **unit** suites
measure never ran. The floor is 100%, and it blocks through `Validate complete`, so a new line
needs a unit test in the same pull request. Integration, e2e and system coverage do not count
([ADR-007](../adr/testing/ADR-007-changed-lines-run-under-a-unit-test.md)).

A changed line no coverage report measures is not in the denominator, so docs, workflows and
config pull requests pass with nothing to say. The failure names every uncovered line, and the
report comment on the pull request lists them again under the coverage table.
