# ADR-011: A Release Is Applied in Order by Flux

## Status
Accepted

## Context

The platform is one host, and the api runs one replica. A release was a Flagger
blue/green canary for the api and another for the frontend, a Prometheus that only
Flagger read, and a load tester whose `hey` requests at 3 per second were the only
traffic the canary analysis ever measured. Two `confirm-promotion` gates held each
canary until the other was ready, and a `pre-rollout` webhook created the schema
migration from a suspended CronJob. About 17 moving parts, 35 commits in two weeks, 10
of them fixes, and three changes of mechanism in five days.

Three incidents are why any of it exists, and each has to stay covered:

- **#1316**: the api and the frontend rolled minutes apart, and Git did not say what
  ran.
- **#1360**: Liquibase raced itself when every api pod migrated on boot.
- **#1365**: a release dropped a column the previous release still read.

## Decision

**Flux applies a release as three Kustomizations in order: the schema migration, then
the api, then the frontend.** Each one has `wait: true`, so it is Ready only once its
Job has completed or its Deployment has rolled out, and each depends on the one before
it, which Flux checks at the same Git revision.

- `apps-migrate` holds the `migrate` Job, on the api image of the release. `force: true`
  replaces the Job when the tag changes, since its pod template is immutable. A failed
  Job stays failed, so nothing after it moves.
- `apps-api` rolls the api Deployment with a surge, retiring the old pod only once the
  new one is Ready.
- `apps-frontend` rolls the frontend the same way, only after the api is Ready.

image-automation-controller writes every pin (the api in the migration and api paths,
the frontend in its own) in one commit, so the paths never name different releases.
Gatus compares the two served versions and alerts on a mismatch that outlasts a
release.

The incidents stay covered. #1316: one commit pins all three paths and each is Ready
only at that revision. #1360: one Job migrates, before any api pod of the release
starts. #1365: the migration still runs before the new api serves, so a changeset must
still be readable by the release before it (api ADR-026), and the changeset check
still enforces that.

## Considered Options

- **Keep Flagger.** Its analysis measured the load tester, not users, and its gates,
  webhooks and Prometheus were most of the release's failure surface.
- **Migrate on api boot.** One replica would not race, but a failed migration would
  crashloop the only api pod instead of leaving the previous release serving.
- **Roll api and frontend together in one Kustomization.** Flux would apply both before
  either is Ready, so a broken api would still ship its frontend.

## Consequences

- A release is readable in three `flux get kustomization` lines, and rolling back is
  moving the pins back with the automation suspended.
- For the minutes between the api and the frontend being Ready, the new api serves the
  previous frontend. One release of backward compatibility remains the contract.
- A failed migration needs a person: a new tag, or deleting the Job to retry the same
  one.
- Leaving Flagger is a one-time manual cut-over, in the runbook, because removing the
  old Kustomization from Git would otherwise garbage-collect the api's storage claim.
