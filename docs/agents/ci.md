# CI

What runs, when, and how to read the result.

## Validate runs on pull requests, not on branch pushes

`Validate` holds every test in the repo: api unit and integration, acceptance features, six
system-test shards, frontend unit and end-to-end, typecheck, lint, image builds. It triggers on
`pull_request`. The header comment in `.github/workflows/validate.yml` says why.

So **pushing a branch runs nothing**. Open the PR and the suite starts; push again and a second
run starts on the new head. The first is never cancelled: it finishes, and its report is dropped
because its commit is no longer the head. To validate a branch with no PR open, ask for a run:

```sh
gh workflow run validate.yml --ref <branch>
```

A dispatched run has no diff to read, so it runs everything.

## Validate runs the suites the diff needs

`.github/buckets.yml` maps a changed path to **buckets**, and each job gates on the buckets it
belongs to. There are five, and each one skips work the others run:

| Bucket | Paths | What runs |
| --- | --- | --- |
| `platform` | `platform/**` but its docs, the Flux scripts | `NixOS flake check`, `Flux manifests` |
| `api` | `services/api/**`, detekt config, the changeset scripts | api lint, unit, integration and coverage, `Build has no warnings`, schema compatibility, changeset SQL, and everything `app` runs |
| `frontend` | `services/frontend/**` | frontend unit and e2e, and everything `app` runs |
| `system` | `tests/**` | api lint, `Build has no warnings`, and everything `app` runs |
| `pinger` | `services/pinger/**` but its compose file | `Pinger tests and image` |

`app` is any bucket but `platform` and `pinger`. It runs both compile jobs, the image builds, the
system tests and the acceptance features, because the system tests drive the api through the
pages and a change on either side can break them. `Validate OpenAPI client generation` and `Changed lines are
covered` run on `api` or `frontend`. `Workflow checks` runs on every pull request.

`Frontend typecheck, lint and build` also runs the job-catalogue test, which holds
`src/utils/jobCatalog.ts` to the job types the api's sources register. The frontend unit job runs
on `frontend` alone, so an api pull request adding a job needs the check on the `app` side.

Two things sit outside the five.

**`ignore`** is what no suite reads: `docs/**`, `gameart/**`, `infra/dns/**`, the editor and
Renovate config, the release-please manifest, every workflow but `validate.yml`, and the dev
stack's compose files, env and mail server, which CI has not started since the system tests run
natively. A pull request touching only these runs `Workflow checks` and nothing else.

**A path in no bucket runs everything**, and the job names it in a notice. That is where the
Gradle wrapper, build-logic, `gradle.properties`, `.github/actions/**`, `.github/buckets.yml` and
`validate.yml` itself sit: each can change how every suite builds or runs. A new top-level
directory lands here too until it gets an entry.

`Decide what to validate` does the matching with `scripts/check-diff-buckets.py`, not with a
paths-filter step. That action evaluates each pattern on its own, so `!a/**` matches every path
outside `a`, and one negation made a bucket claim the whole repository ([#1453]). Matching in
the script means the rules CI runs are the rules `--self-test` proves.

[#1453]: https://github.com/ESA-Blueshell/website/issues/1453

The job publishes one output, `run`, a JSON object with a key per bucket and `app`, and a job
reads it as `fromJSON(needs.changes.outputs.run).api`. The bucket names are written once, in
`buckets.yml`; the self-test refuses a job gated on a name no bucket declares, which would read as
false and skip that job on every run. It also carries a fixture table placing one path per
pattern and refuses a pattern no fixture exercises, so the table cannot fall behind the file. The
self-test runs first thing in the job, so a broken rule fails before anything is decided.

To see what a given path would run, add it to `FIXTURES` and run the self-test:

```sh
./scripts/check-diff-buckets.py --self-test
```

A merge queue entry and a dispatched run have no diff to read, so they run everything. The queue
entry is the only run that sees two pull requests combined, and it runs once per merge rather than
once per push.

`Build` publishes every image on every push to `main`, with no path filter, so every commit there
has a full set to deploy.

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

## The build has no warnings

`Build has no warnings` compiles build-logic, the build scripts and every source set with
each warning an error. It runs on the api and system buckets. Three switches refuse a
warning, and the log is read for the rest:

- `--warning-mode=fail` refuses a Gradle deprecation.
- `-Porg.gradle.kotlin.dsl.allWarningsAsErrors=true` refuses a warning in a build script.
- `-PwarningsAsErrors=true` refuses a Kotlin compiler warning in build-logic and in every
  source set. Without it a warning stays a warning, so a work in progress still compiles.
- The Kotlin Gradle plugin logs its own warnings, such as the plugin being loaded in more
  than one project, and no switch fails on those. The job fails on any `w:` line or that
  message in the log.

The job runs without the build cache, because a task restored from it prints nothing it
warned about. To run it locally:

```sh
./gradlew --continue --warning-mode=fail \
  -Porg.gradle.kotlin.dsl.allWarningsAsErrors=true -PwarningsAsErrors=true \
  assemble testClasses testFixturesClasses integrationTestClasses
```

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
