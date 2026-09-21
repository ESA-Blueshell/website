# Cutting over to Liquibase and Flagger

The 1.8.0 release is the first to run through a schema Liquibase owns and a
promotion Flagger gates. Both mechanisms are new, neither has run against
production, and one step in the middle cannot be undone from git.

This is the procedure, what each step costs, and what it looks like when it
goes wrong. [`runbook.md`](runbook.md) covers the steady state afterwards.

The wizard that drives it is `private/rollout.sh`. It is gitignored and lives
only on the machine that runs the cutover, so this document is the record of
what it does.

```bash
private/rollout.sh          # from stage 1
private/rollout.sh 5        # resume at a stage
private/rollout.sh 3 5      # a range, then stop
```

## What is actually changing

Three things at once, which is why the order matters.

**Flyway became Liquibase.** The schema's origin is now a baseline captured
from production, not 96 replayed migrations — [ADR-026](../../docs/adr/api/ADR-026-the-schema-starts-from-a-baseline.md).
Production has never been told it already has that baseline. Until it is, any
Liquibase run against it tries to *apply* the baseline, hits tables that exist
and fails.

**The migration left the pod.** It used to run at every api boot, so a rolling
replacement raced itself. It now runs once per release, in a Job the Canary's
`pre-rollout` webhook fires before a single new pod exists. A migration that
fails stops the release with nothing changed.

The consequence is the thing to hold on to: **the previously released version
meets the new schema first**, and keeps serving against it for the whole
analysis. A changeset it cannot read breaks what is live.
`scripts/check-changeset-compatibility.py` refuses those on the pull request,
but it only knows about changeTypes, not about what your entities map.

**The deploy became a promotion.** api and frontend are pinned to one tag in
`apps/stateless/kustomization.yaml` and promoted as a pair. Each Canary holds
at `WaitingPromotion` until the other is out of `Progressing`, so a broken
frontend parks the api rather than shipping half a release.

## Before you start

- `kubectl` context `blueshell` reaches the cluster, and `docker`, `jq`, `git`
  and `flux` are on the path.
- An hour when the site being briefly odd is acceptable. Not during an event.
- The Canaries exist in the cluster. **If they do not, stop and read
  "The first Flagger apply" below** — that is its own event, not part of this.

Stage 1 answers the last one. It costs nothing and changes nothing:

```bash
private/rollout.sh 1 1
```

## The first Flagger apply

Only if stage 1 warns there is no api Canary.

Flux prunes the hand-written `api` and `frontend` Services in the same apply
that creates the Canaries, and Flagger only recreates them once its `-primary`
Deployments are Ready. **Expect an outage of roughly one api cold start.**

Do it on its own, in a quiet window, with `apps-stateless` suspended and
resumed deliberately — the procedure is in
[`runbook.md`](runbook.md#merging-a-change-you-do-not-want-applied-immediately).
Let it settle before starting the cutover. Two new mechanisms failing at once
are much harder to read than one.

## The stages

### 1 — preflight

Reads. Changes nothing.

Checks the tools and the cluster, takes the changelog from `origin/main`,
reports the pinned tag, what production is actually running, whether Flagger
owns the api, and whether the `db-migrate` CronJob is applied.

*If the CronJob is missing*, the `pre-rollout` gate has nothing to instantiate
and every release will park. Apply `apps-stateless` before going further.

### 2 — cut the release

Merging the release pull request is yours. The stage only asks which version
and then refuses to continue until GHCR has both images.

**Cutting a release does not deploy.** It publishes and signs
`ghcr.io/esa-blueshell/{api,frontend}:v<version>` and writes a tag, a GitHub
release and a changelog commit. The cluster keeps running whatever
`kustomization.yaml` pins, which is still the previous version. Nothing moves
until stage 8.

*If it says GHCR has no images*, the release build has not finished or has
failed. Check the `Release` workflow run before retrying — a run that dies at
startup reports no job at all, which is how it stayed broken for eight
consecutive merges (#1363).

### 3 — capture the production schema

Reads production. Writes a local file.

`mariadb-dump --no-data`: structure, routines, triggers and events, **no rows**.
The drift check compares shape, so rows would be dead weight and pure risk.

### 4 — does production match the baseline?

Local only. Production is not touched.

Starts a throwaway MariaDB, restores the production schema into one database
and applies the baseline into another, then diffs them. This is the check that
found the baseline describing `main` rather than production (#1345).

*If it reports differences*, stop. Either the baseline is wrong, or production
has drifted. Syncing over an unexplained difference is how drift becomes
permanent and invisible. Known and accepted: the collation differences in
[#1346](https://github.com/ESA-Blueshell/website/issues/1346).

### 5 — rehearse the sync

Local only. Production is not touched.

Runs `changelog-sync-to-tag baseline` against the restored copy, then asserts
the table count moved by exactly two — `DATABASECHANGELOG` and
`DATABASECHANGELOGLOCK` — and nothing else. Then runs `update` to prove the
changesets after the tag apply on top.

**Do not skip this.** `changelog-sync` records the baseline as applied without
comparing anything. It cannot fail loudly on a schema that does not match; it
simply blesses whatever it finds, and you discover the mismatch at some later
migration instead.

### 6 — back up production, with its rows

Reads production. Writes a local file.

The one artefact that carries data: `mariadb-dump --single-transaction --quick`,
gzipped, into `private/.rollout/`. `--single-transaction` takes a consistent
snapshot without locking the site out.

Verified without being read — `gzip -t`, a byte floor, line and table counts,
and a `sha256` written beside it.

**This file holds member data, unencrypted, on a laptop.** Keep it until the
release has served for a day, then delete it. It is the only thing standing
between stage 7 and a bad afternoon.

The stage prints the restore command. It is also named again in stage 7's
failure path.

### 7 — sync production

**The first step that cannot be undone from git.** Confirms first, and refuses
to run at all if stage 6 left no backup.

Runs `changelog-sync-to-tag baseline` against production. It writes rows into
`DATABASECHANGELOG` and **no DDL** — the schema is not modified. Afterwards it
counts tables before and after and makes the same `before + 2` assertion stage
5 makes, because a command exiting zero is not evidence it was harmless.

*If the table count moved*, something applied DDL that should not have.
Restore from the stage 6 backup before anything else.

After this, the api applies whatever follows the `baseline` tag when it starts.

### 8 — cut over

Suspends `apps-stateless`, waits for you to merge the tag bump, resumes, and
watches.

Both `newTag` values in `apps/stateless/kustomization.yaml` go to the new
version. **Never move one without the other** — the pair is the point.

Then Flagger: the `pre-rollout` webhook creates `migrate-<tag>` from the
CronJob and waits up to eleven minutes; the canary pods come up; four
thirty-second analysis rounds run against error rate and latency; the
rendezvous gate holds until the sibling is ready; promotion copies the canary
spec onto `-primary`.

Phases, and what each is waiting for:

| Phase | Waiting for |
| --- | --- |
| `Progressing` | the migrate Job, then the canary pods. A long pause here is the Job: `kubectl -n default logs job/migrate-<tag>` |
| `WaitingPromotion` | the other service. Normal, briefly |
| `Promoting` | the spec being copied onto `-primary` |
| `Succeeded` | done. Both reach it, or neither should |
| `Failed` | analysis or a webhook failed |

A canary stuck in `Progressing` while the other sits in `WaitingPromotion` is
the rendezvous working. **Fix the stuck side, not the waiting one.**

`Failed` means the primary still serves the previous release — the site is up
and on the old version. Recover by reverting the tag in git, **not** by
deleting pods. Reverting the tag does not revert the schema, and it does not
need to: a changeset that reached production was one the previous release
could already read.

### 9 — did it land?

Reads.

Both Canaries, what `-primary` is running, what `/version` reports on each
service, and a fresh error-rate sample. `0.73%` (34 of 4677) was the rate
before this release — a materially higher number is worth understanding before
you walk away.

`/version` matters more than `Succeeded` does: a promotion that kept the old
pods reports healthy and serves yesterday's build.

## What is irreversible, and what is not

| | Reversible? |
| --- | --- |
| Cutting the release (stage 2) | The tag and GitHub release are awkward to remove, but nothing is deployed |
| Stages 3, 4, 5, 6 | Nothing written to production |
| **Sync (stage 7)** | **No.** Restore from the stage 6 backup |
| Tag bump (stage 8) | Yes — revert the commit, both tags together |
| A schema change a release carried | Not automatically, and deliberately so |

A rollback of the schema is a rehearsed manual procedure with a backup, never
an automatic response to a failed release: a down migration run after the new
code has written rows the old schema cannot hold loses them.

## If it goes wrong

**The migrate Job fails.** The release parks with no pod churn and the previous
version keeps serving. `kubectl -n default logs job/migrate-<tag> --tail=100`.
Fix the changeset, cut a new tag, let the gate run again. The Job is named
after the image tag, so re-running the same release reuses it rather than
starting a second.

**A canary fails analysis.** The primary still serves the old release. Revert
the tag bump. Read `kubectl -n default describe canary api` for which gate or
metric refused.

**Both canaries park at `WaitingPromotion`.** They are each waiting for the
other, which should not happen — read both phases and the loadtester logs in
`flagger-system`.

**The site is broken and you need it back now.** Revert the tag commit and push.
Flux reconciles every minute. If the schema is the problem rather than the code,
restore the stage 6 backup — but read
[ADR-026](../../docs/adr/api/ADR-026-the-schema-starts-from-a-baseline.md) on
what a down migration costs first.

## Afterwards

- Delete the backup once the release has served for a day.
- `docker rm -f rollout-rehearsal` to clean up stage 4's throwaway database.
- Later releases are stages 2, 8 and 9 only. Stages 3 to 7 exist because
  production had not met Liquibase yet, and it only has to meet it once.
