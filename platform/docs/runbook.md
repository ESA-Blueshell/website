# Platform runbook

The `platform/` tree manages the production stack:
**NixOS + k3s + FluxCD + Kustomize + Helm**.

Production runs on a single Contabo VPS (`frankfurt-contabo-1`) under
`esa-blueshell.nl`. Flux reconciles every manifest from this
repository against `main`. The api and the frontend are pinned to one
release tag and applied in order: schema, api, frontend (see below). Every image this
repository builds is pinned by digest in git; nothing polls a moving tag.

Detailed setup guides:

- [`bringup-v2.md`](bringup-v2.md) — bringing up a fresh node from
  bare metal.
- [`vault-bootstrap.md`](vault-bootstrap.md) — Vault unseal, secret
  paths, OIDC.
- [`flux-bootstrap.md`](flux-bootstrap.md) — Flux installation +
  cluster reconciliation.
- [`nix-flake.md`](nix-flake.md) — NixOS flake structure + host
  definitions.
- [`discord-bot.md`](discord-bot.md) — creating the Discord bot, adding
  it to the server, and registering its token.
- [`break-glass.md`](break-glass.md) — unlocking an account or resetting
  two-factor when no admin can.

## Releasing api + frontend

Both images are pinned by digest with the release tag beside it, so the
cluster runs the image that was built rather than whatever the tag points at
later. The api's pin is written twice, in
[`stateless/migrate`](../cluster/flux/apps/stateless/migrate/kustomization.yaml)
and [`stateless/api`](../cluster/flux/apps/stateless/api/kustomization.yaml),
and the frontend's once, in
[`stateless/frontend`](../cluster/flux/apps/stateless/frontend/kustomization.yaml).

A version tag is written once, by the build that pushed the manifest and so
holds the digest it names, and that build refuses to move a tag that already
names something else. A push publishes `:sha-<short>` only. Up to 1.8.0 this
was not so: every merge to main rebuilt the pending version and moved its tag,
which is why `v1.8.1` and `v1.9.0` images exist for releases nobody cut.

Cutting a release is what deploys it. The build publishes the images and
writes their version tag; image-reflector-controller sees the new tag within
five minutes, image-automation-controller commits the reference to every
setter on main, and kustomize-controller applies it. Nothing in CI writes the
pin, and no CI commit has to survive release-please regenerating its branch,
which is what made the old arrangement unmergeable.

The commit is authored by `flux <flux@esa-blueshell.nl>` and carries
`[ci skip]`, so it triggers no workflow. All three setters are written in one
commit by one automation run, so the paths never name different releases.

Flux applies that commit in order, and each step waits for the one before it
to be Ready at the same revision:

1. `apps-migrate` replaces the `migrate` Job with one on the new api image and
   waits for it to complete.
2. `apps-api` rolls the api Deployment, surging a new pod and retiring the old
   one only once the new one is Ready.
3. `apps-frontend` rolls the frontend the same way.

So a migration that fails leaves the previous api and frontend serving, and an
api that does not come up leaves the previous frontend serving. One release of
backward compatibility is still the contract: for the minutes between steps 2
and 3 the new api serves the previous frontend. Gatus alerts if the two report
different versions for longer than twenty minutes.

```bash
flux -n flux-system get kustomizations | grep -E 'NAME|apps-(migrate|api|frontend)'
kubectl -n default get job migrate
kubectl -n default rollout status deployment/api
curl -s https://esa-blueshell.nl/api/version; curl -s https://esa-blueshell.nl/version.json
```

### Rolling back

This is the one way back from a release, whether it failed or shipped
something wrong. Editing the pins is not enough on its own: the policy selects
the highest released version, so the next scan writes the new version straight
back over the edit. Suspend the automation first:

```bash
flux -n flux-system suspend image update apps
```

Then set every setter back to the previous release, with the digest that
release published beside each: the api in `stateless/migrate` and
`stateless/api`, the frontend in `stateless/frontend`. Never move one without
the others; one release of backward compatibility is the contract, and a
mismatched pair is outside it. Push, and Flux applies the three steps above
with the previous release.

The migration does not roll back with it: the previous release runs against the
newer schema, which is what backward compatibility guarantees. Rolling a
changeset back is a separate, rehearsed step; see "The schema" below.

Resuming re-advances to the newest version, which is the thing you just rolled
away from. So leave it suspended until the fix is released, and say so in the
incident notes, because a suspended automation is silent: releases publish and
tag as usual and simply do not deploy.

```bash
flux -n flux-system resume image update apps
```

For a version that must never be selected again, deleting its tag from the
registry is the only durable answer. Narrowing the ImagePolicy range works but
is a commit that the next release has to remember to undo.

### Merging a change you do not want applied immediately

Flux reconciles `main` every minute and there is no other gate, so a
merge is a deploy. Suspend the Kustomization that owns the change
first, merge, read what it would apply, then resume while watching:

```bash
flux -n flux-system suspend kustomization apps-api
# merge, then:
flux -n flux-system build kustomization apps-api --path ./platform/cluster/flux/apps/stateless/api
flux -n flux-system resume kustomization apps-api
flux -n flux-system get kustomization apps-api --watch
```

Suspending stops drift correction for that subtree as well, so resume
the same day. `flux diff kustomization` shows the change against the
live cluster if you want it before resuming.

### Leaving Flagger (once)

The change that introduced `apps-migrate`, `apps-api` and `apps-frontend`
also removed `apps-stateless` and `apps-delivery`. Removing `apps-stateless`
from Git would make Flux garbage-collect everything it applied, the api's
storage claim included, and Flagger owns the `api` and `frontend` Services
until its Canaries are gone. So it is done by hand, once, in a quiet window,
before the change reaches `main`:

```bash
# 1. Stop Flux applying main while the ownership moves. apps-delivery stays
#    running: a suspended Kustomization is not pruned when it is deleted, and
#    step 4 relies on that prune to uninstall Flagger.
flux -n flux-system suspend kustomization flux-system
flux -n flux-system suspend kustomization apps-stateless

# 2. Delete the Canaries, but not what they own. Flagger created the api and
#    frontend Services and the -primary Deployments, so they name the Canary as
#    owner, and a plain delete would take them with it. Orphaned, they keep
#    serving. revertOnDeletion scales api and frontend back up meanwhile.
kubectl -n default get svc api frontend \
  -o jsonpath='{range .items[*]}{.metadata.name}: {.metadata.ownerReferences[*].kind}{"\n"}{end}'
kubectl -n default delete canary api frontend --cascade=orphan
kubectl -n default rollout status deployment/api
kubectl -n default rollout status deployment/frontend

# 3. Orphan what apps-stateless applied, so deleting it deletes nothing.
kubectl -n flux-system patch kustomization apps-stateless --type=merge -p '{"spec":{"prune":false}}'
kubectl -n flux-system delete kustomization apps-stateless

# 4. Merge the change, then resume. flux-system prunes apps-delivery, which
#    uninstalls Flagger, Prometheus and the load tester, and creates the three
#    new Kustomizations, which adopt the Deployments, Services and storage.
flux -n flux-system resume kustomization flux-system
flux -n flux-system get kustomizations --watch

# 5. Clear what nothing owns any more, once apps-api and apps-frontend are
#    Ready and their Services select the api and frontend pods.
kubectl -n default get svc api frontend -o wide
kubectl -n default delete deployment api-primary frontend-primary
kubectl -n default delete svc api-primary api-canary frontend-primary frontend-canary
kubectl -n default delete cronjob db-migrate
kubectl -n default get jobs   # delete the old migrate-<tag> Jobs by name; keep `migrate`
kubectl delete crd canaries.flagger.app metrictemplates.flagger.app alertproviders.flagger.app
```

Check afterwards that nothing used what went: `kubectl get ns flagger-system`
reports NotFound, `kubectl -n default get deploy` lists `api` and `frontend`
with no `-primary`, and the site serves throughout.

Once, on the next release, check the order holds: while the api rolls,
`flux -n flux-system get kustomizations` shows `apps-frontend` waiting with
"dependency 'flux-system/apps-api' is not ready", and `/version.json` still
reports the previous release.

## The schema

Liquibase owns it, starting from a baseline that captures what the 96 Flyway
migrations produced — see
[ADR-026](../../docs/adr/api/ADR-026-the-schema-starts-from-a-baseline.md).
An empty database applies the baseline. A database that already ran the
migrations must be told it has it, rather than running it:

```bash
liquibase --changeLogFile=db/changelog/db.changelog-master.yaml changelog-sync
```

**Rehearse against a restored dump first.** `changelog-sync` records the
baseline as applied without comparing anything. If production has drifted from
what the migrations produced, that drift becomes invisible rather than
resolved. Dump production, build a second database from the baseline alone, and
diff the two before running this anywhere that matters.

**Check what the sync wrote, not just that it exited zero.** A filename the api
does not look for leaves it re-applying the baseline, mid-release.

```bash
SELECT DISTINCT FILENAME FROM DATABASECHANGELOG;
-- every row must start db/changelog/
```

### When a release parks on the migration

The api does not migrate at boot. `apps-migrate` replaces the `migrate` Job
with one on the new api image and waits up to fifteen minutes for it; nothing
of the release is applied until it completes. A migration that cannot apply
leaves the Job Failed, `apps-migrate` not Ready, and the previous api and
frontend serving.

```bash
flux -n flux-system get kustomization apps-migrate
kubectl -n default get job migrate
kubectl -n default logs job/migrate --tail=100
```

A failed Job stays failed: Flux reapplies the same spec and finds nothing to
change. A Job that runs past its ten-minute deadline fails the same way, and
may leave Liquibase's lock held; release it with the same image before retrying
(`DATABASECHANGELOGLOCK` shows who holds it). Fix the changeset and cut a new tag, which replaces the Job. Delete
the failed Job only when you want the same tag retried from scratch, then run
`flux -n flux-system reconcile kustomization apps-migrate`.

Every changeset carries a rollback, the baseline included. Rolling one back is
a rehearsed manual step with a backup, never an automatic response to a failed
release: a down migration run after the new code has written rows the old
schema cannot hold loses them.

## Is this image ours?

Every image the repository publishes is signed by the workflow that built it,
keylessly — there is no key, only a short-lived certificate tied to that run's
OIDC identity. To check one:

```bash
cosign verify ghcr.io/esa-blueshell/api:v1.8.0 \
  --certificate-identity-regexp '^https://github\.com/ESA-Blueshell/website/\.github/workflows/build\.yml@' \
  --certificate-oidc-issuer https://token.actions.githubusercontent.com
```

It prints the subject, the workflow, the commit and the run. A signature that
does not verify means the image was not built by this repository's CI.

What went into an image is recorded beside it, as attestations:

```bash
cosign download attestation ghcr.io/esa-blueshell/api:v1.8.0 | jq -r .payload \
  | base64 -d | jq '.predicateType'
```

**Known gap: nothing enforces this.** The cluster will run an unsigned image
quite happily, because no admission controller checks. Verification is a thing
a person does while investigating, not a thing that stops a bad image reaching
production. Closing it needs an admission policy (Kyverno or the sigstore
policy-controller), which is not deployed.

## User uploads

The api persists uploads to `/srv/blueshell/storage`, backed by a
static hostPath PV (`platform/cluster/flux/apps/stateless/api/pvc.yaml`).

To migrate uploads from another host:

```bash
rsync -av --progress \
  root@<source>:/path/to/storage/ \
  root@frankfurt-contabo-1:/srv/blueshell/storage/
```

Run after the api pod is Ready on the PV (so the subdirectory layout
already exists) and before any DNS change. The subdirectory layout
(`profile-pictures/`, `event-banners/`, `board-documents/`, …) is
owned by `FileType.directory` in code, so filenames round-trip
unchanged.
