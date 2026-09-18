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

Both images are pinned to one tag in
[`apps/stateless/kustomization.yaml`](../cluster/flux/apps/stateless/kustomization.yaml),
bumped by hand until the release pipeline writes a digest there (#1293).
Cutting a release therefore publishes images without deploying them;
edit that one file and push to roll both services.

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
