# Platform runbook

The `platform/` tree manages the production stack:
**NixOS + k3s + FluxCD + Kustomize + Helm**.

Production runs on a single Contabo VPS (`frankfurt-contabo-1`) under
`v2.esa-blueshell.nl`. Flux reconciles every manifest from this
repository against `main`; Keel rolls Deployments when new
`ghcr.io/esa-blueshell/*:latest` images appear.

Detailed setup guides:

- [`bringup-v2.md`](bringup-v2.md) — bringing up a fresh node from
  bare metal.
- [`vault-bootstrap.md`](vault-bootstrap.md) — Vault unseal, secret
  paths, OIDC.
- [`flux-bootstrap.md`](flux-bootstrap.md) — Flux installation +
  cluster reconciliation.
- [`nix-flake.md`](nix-flake.md) — NixOS flake structure + host
  definitions.

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
