# CI

What runs, when, and where to read the result.

## Validate runs on pull requests, not on branch pushes

`Validate` — every test in the repo: api unit and integration, acceptance features, six
system-test shards, frontend unit and end-to-end, typecheck, lint, image builds — triggers on
`pull_request`, filtered to a diff that touches code.

So **pushing a branch runs nothing**. Open the PR and the suite starts; push again and the run
in flight is superseded by one on the new head. To validate a branch with no PR open, dispatch
a run deliberately:

```sh
gh workflow run validate.yml --ref <branch>
```

## Reading a result on the right commit

GitHub attaches check-runs to a **sha**, and a PR displays only the ones on its head. A run
against an earlier commit of the same branch is invisible on the PR page even though it tested
that PR's code — so `gh pr checks` showing no tests does not mean the suite was skipped. To see
what actually ran against a commit:

```sh
gh api "repos/ESA-Blueshell/website/commits/<sha>/check-runs" --jq '.check_runs[] | "\(.conclusion) \(.name)"'
```

This is why `Validate` listens to `pull_request` rather than `push`: a push filter matches the
files in that push's commits, so a docs-only final commit left the head with no run and the PR
reading all-green with no test in it (#1032, #1033). A `pull_request` filter matches the whole
PR diff, so the head is always validated when the PR touches code.

## A cancelled job is not a failed one

Pushing repeatedly cancels superseded runs, and cancelled check-runs stay on the shas they
belong to. A red mark on an intermediate commit is usually that, not a failure. `UNSTABLE` on
a PR means something is cancelled, neutral or still running — not that anything failed.
