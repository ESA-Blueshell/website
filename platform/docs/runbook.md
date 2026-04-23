# Platform runbook

The `platform/` tree replaces the legacy `infra/` + `vps/` stack with
**NixOS + k3s + FluxCD + Kustomize + Helm**.

Status: **scaffolding in progress**. This PR removes the legacy assets; the
Flux manifests, Nix flake, and cutover runbook land in follow-up PRs:

- **PR2** — `build-logic/` convention plugins, `libs/` skeleton
- **PR3** — split unit/integration/system tests, drop merged-coverage pipeline
- **PR4** — `platform/nix/` flake for the new single-node VPS ✅
- **PR5** — FluxCD scaffold + `apps-core` (cert-manager, external-dns, Traefik, VSO, Headlamp, Keel) ✅
- **PR6** — Vault + VSO + MariaDB + Listmonk Postgres ✅
- **PR7** — application Deployments (api, frontend, listmonk) + Stalwart mail ✅
- **PR8** — OIDC issuer in the API + MyApps page + k3s/Headlamp/Vault OIDC wiring ✅
- **PR9** — bring up v2 stack on a fresh VPS + Gatus status page at `status.v2.esa-blueshell.nl`
- **PR10** — apex cutover: rename `*.v2.esa-blueshell.nl` → apex, flip DNS, 72h soak
- **PR11** — decommission old Swarm VPS, strip vestigial DNS

The v2 stack runs side-by-side with the old Swarm on apex. No big-bang
cutover — when v2 has carried real traffic for long enough, the apex
moves in its own PR. The full PR-9 bring-up runbook lives at
`platform/docs/bringup-v2.md`.
