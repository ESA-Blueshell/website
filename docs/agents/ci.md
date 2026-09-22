# CI

What runs, when, and how to read the result.

## Validate runs on pull requests, not on branch pushes

`Validate` holds every test in the repo: api unit and integration, acceptance features, six
system-test shards, frontend unit and end-to-end, typecheck, lint, image builds. It triggers on
`pull_request`. The header comment in `.github/workflows/validate.yml` says why.

So **pushing a branch runs nothing**. Open the PR and the suite starts; push again and the run
in flight is superseded by one on the new head. To validate a branch with no PR open, ask for a
run:

```sh
gh workflow run validate.yml --ref <branch>
```

A dispatched run has no diff to read, so it runs everything.

## Validate runs the suites the diff needs

The `Decide what to validate` job matches the diff against a set of **buckets**, and each job
below gates on the bucket it belongs to. A bucket names what a change can break, so a NixOS
module runs the flake check and nothing else, and a stylesheet runs the frontend suites and no
system tests.

| Changed | What runs |
| --- | --- |
| `platform/nix/**`, `platform/flake.*` | `NixOS flake check` |
| `platform/cluster/**` | `Flux manifests` |
| `.github/**` other than `actions/` and `validate.yml` | `Workflow checks` |
| `services/api/**`, `libs/**`, `config/detekt/**` | api lint, unit, integration and coverage |
| `services/frontend/**` | frontend unit, e2e and e2e coverage |
| the API surface, the schema, `services/frontend/src/**` outside `assets` and `styles`, `tests/**`, the compose files | system tests, acceptance features |
| `db/changelog/**` | schema compatibility, changeset SQL |
| the Dockerfiles and what they resolve | image builds |
| `services/api/src/main/kotlin/**`, `services/frontend/src/**` | `Changed lines are covered` |

Buckets overlap on purpose. A controller is API source and an API surface, so it sets both and
runs the api suites and the system tests.

Every script reaches the job that runs it, so `check-flux-manifests.sh` is `platform-flux` and
`changeset-sql.sh` is `changesets`. `check-diff-buckets.py` is ignored, because the
`Decide what to validate` job runs its self-test on every pull request anyway.

Three things sit outside the buckets.

**`meta`** is what can change how every suite builds or runs: the Gradle wrapper and build
logic, and `.github/actions/**`. A match turns on every bucket.

**`ignore`** is what no job validates: `docs/**`, `gameart/**`, `infra/dns/**`, the editor and
Renovate config, and the release-please manifest. A pull request touching only these runs
nothing, as it did before.

**Anything else** runs the whole suite and says so. `scripts/check-diff-buckets.py` reports a
changed path in neither a bucket nor the ignore list, and the job warns with the path names, so
a new top-level directory gets an entry in the filter rather than silence. The same script
carries a fixture table placing one path per pattern, and its `--self-test` refuses a pattern no
fixture exercises, so the table cannot fall behind the filter.

To see what a given path would run, add it to `FIXTURES` and run the self-test:

```sh
./scripts/check-diff-buckets.py --self-test
```

## Editing validate.yml runs the jobs it edits

`validate.yml` is not `meta`, or every change to CI would be the most expensive kind of pull
request. The `Decide what to validate` job compares this file's jobs against the base and turns
on the buckets the edited jobs gate on. Change the step that runs the api unit tests and the
backend bucket runs; change a platform job and it does not.

Four things there reach every bucket, because none of them belongs to one job: the trigger, the
workflow environment, a job that gates on no bucket at all, and a job this file gained or lost.

A merge queue entry runs everything. It is the only run that sees two pull requests combined,
and it runs once per merge rather than once per push.

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

The check gates on the paths the reports measure, not on the suites that produce them. If
measured lines changed and neither unit suite ran, it fails and says a bucket does not cover
them, rather than passing on an empty denominator.
