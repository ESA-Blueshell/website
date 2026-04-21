# Platform runbook

The `platform/` tree replaces the legacy `infra/` + `vps/` stack with
**NixOS + k3s + FluxCD + Kustomize + Helm**, modeled on `personal-stack-2`.

Status: **scaffolding in progress**. This PR removes the legacy assets; the
Flux manifests, Nix flake, and cutover runbook land in follow-up PRs:

- **PR2** — `build-logic/` convention plugins, `libs/` skeleton
- **PR3** — split unit/integration/system tests, drop merged-coverage pipeline
- **PR4** — `platform/nix/` flake for the new single-node VPS ✅
- **PR5** — FluxCD scaffold + `apps-core` (cert-manager, external-dns, Traefik, VSO, Headlamp, Keel)
- **PR6** — Vault + VSO + MariaDB + Listmonk Postgres
- **PR7** — application Deployments (api, frontend, listmonk) + Stalwart mail
- **PR8** — OIDC issuer in the API + MyApps page + k3s/Headlamp/Vault OIDC wiring
- **PR9** — parallel VPS provisioning + data migration + DNS cutover
- **PR10** — decommission the old Swarm VPS + Gatus status page

Until PR9 lands, production still runs on the existing Docker Swarm VPS via
`.github/workflows/deploy.yml` and the GHCR-published `api`/`frontend` images.
