# Platform runbook

The `platform/` tree manages the production stack:
**NixOS + k3s + FluxCD + Kustomize + Helm**.

Production runs on a single Contabo VPS (`frankfurt-contabo-1`) under
`esa-blueshell.nl`. Flux reconciles every manifest from this
repository against `main`. The api and the frontend are pinned to one
release tag and promoted as a pair by Flagger (see below); Keel still
rolls the remaining Deployments when a new `:latest` image appears.

Detailed setup guides:

- [`bringup-v2.md`](bringup-v2.md) — bringing up a fresh node from
  bare metal.
- [`vault-bootstrap.md`](vault-bootstrap.md) — Vault unseal, secret
  paths, OIDC.
- [`flux-bootstrap.md`](flux-bootstrap.md) — Flux installation +
  cluster reconciliation.
- [`nix-flake.md`](nix-flake.md) — NixOS flake structure + host
  definitions.

## Releasing api + frontend

Both images are pinned in
[`apps/stateless/kustomization.yaml`](../cluster/flux/apps/stateless/kustomization.yaml),
by digest with the release tag beside it, so the cluster runs the image that
was built rather than whatever the tag points at later. CI writes the pin onto
the release branch once the images publish; merging the release pull request is
what rolls both services.

A tag is not a stable identifier here. `v1.8.0` was rebuilt and re-tagged while
production was already running the earlier digest, so anything resolving the
tag afterwards gets a different image than production has.

Flux applies the pair; Flagger turns each one
into a blue/green rollout and the two `confirm-promotion` gates hold
each canary until the other is out of `Progressing` and not `Failed`.
One release of backward compatibility is still the contract: promotion
copies the canary spec onto the `-primary` Deployment, which then rolls
normally, so the two apex Services finish flipping seconds apart.

```bash
kubectl -n default get canaries
kubectl -n default describe canary api | tail -30
flux -n flux-system get kustomization apps-stateless
```

Phases: `Initializing` → `Initialized` on first install, then
`Progressing` → `WaitingPromotion` → `Promoting` → `Succeeded` per
release. A canary parked in `WaitingPromotion` is waiting for its
sibling — check the other one's phase before touching anything.

`Failed` means the analysis or the acceptance webhook failed; Flagger
scales the canary down and leaves the primary serving the previous
release. Recover by reverting the tag in git, not by deleting pods.

Manual rollback: set both `newTag` values back to the previous release
tag and push. Never move one without the other.

### Merging a change you do not want applied immediately

Flux reconciles `main` every minute and there is no other gate, so a
merge is a deploy. Suspend the Kustomization that owns the change
first, merge, read what it would apply, then resume while watching:

```bash
flux -n flux-system suspend kustomization apps-stateless
# merge, then:
flux -n flux-system build kustomization apps-stateless --path ./platform/cluster/flux/apps/stateless
flux -n flux-system resume kustomization apps-stateless
flux -n flux-system get kustomization apps-stateless --watch
```

Suspending stops drift correction for that subtree as well, so resume
the same day. `flux diff kustomization` shows the change against the
live cluster if you want it before resuming.

### First rollout onto Flagger

Expect an outage of roughly one api cold start. Flux prunes the
hand-written `api` and `frontend` Services in the same apply that
creates the Canaries, and Flagger only recreates them once its
`-primary` Deployments are Ready. Do it in a quiet window, with
`apps-stateless` suspended per the procedure above, and watch
`kubectl -n default get canaries,svc`.

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

The api does not migrate at boot. A `pre-rollout` webhook on the api Canary
creates `migrate-<tag>` from the suspended `db-migrate` CronJob, on the image
the release pins, and waits up to ten minutes for it.

Flagger scales the canary up and waits for it to be Ready before running a
`pre-rollout` webhook, so by the time the migration runs a canary pod exists.
It takes no traffic — this is blue/green, and the weight stays at 0 — and
nothing is promoted until the Job succeeds. A migration that cannot apply
therefore leaves the previous release serving, with a canary pod idle beside
it that is scaled away when the analysis gives up.

```bash
kubectl -n default get jobs -l job-name --field-selector status.successful=0
kubectl -n default logs job/migrate-<tag> --tail=100
kubectl -n default describe canary api | grep -A5 pre-rollout
```

The Job name comes from the image tag, so re-running the same release reuses
the existing Job rather than starting a second one. Fix the changeset, cut a
new tag and let the gate run again; delete the failed Job only once you want
that tag retried from scratch.

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
