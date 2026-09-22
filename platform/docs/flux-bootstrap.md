# Bootstrapping Flux on a fresh cluster

This runs once, on the freshly-installed NixOS + k3s node, against the
tree in `platform/cluster/flux/clusters/production/`. Flux reconciles
from `main` continuously after that.

## Prerequisites

- `kubectl` pointed at the cluster (`~/.kube/config` from the k3s node).
- `flux` CLI locally, matching the controller version in
  `flux-system/gotk-components.yaml`.
- A GitHub personal access token with `repo:read` on `ESA-Blueshell/website`.

## Steps

```bash
# 1. Apply the controllers and the root sync manifests. This creates
#    the flux-system namespace and controllers, and the GitRepository
#    + Kustomization that pull the tree going forward.
kubectl apply -k platform/cluster/flux/clusters/production/flux-system

# 2. Inject the GitHub token secret so the source-controller can
#    authenticate to the repo. Flux expects it in the flux-system
#    namespace under the name `flux-system` (matches gotk-sync.yaml).
kubectl -n flux-system create secret generic flux-system \
  --from-literal=username=flux \
  --from-literal=password=$(gh auth token)

# 3. Watch reconciliation progress.
flux get kustomizations --watch
```

Expected order (each step waits for the previous):

1. `flux-system` — Kustomization + GitRepository seeded.
2. `apps-core` — HelmReleases for cert-manager, external-dns, Traefik,
   Vault Secrets Operator install and report Ready. Takes 2–5 minutes.
   Everything else depends on this one, which is why Flagger is not in
   it: a controller that only `apps-stateless` needs must not be able
   to stall cert-manager and Traefik.
3. `apps-delivery` — Flagger and its loadtester. Waits for Ready so the
   Canary CRDs exist before `apps-stateless` applies Canaries.
4. `apps-edge` — ClusterIssuer, wildcard Certificate + TLSStore, and
   forward-auth Middleware land. The wildcard cert takes the longest
   (DNS-01 propagation + ACME order).
5. `apps-utility-system` — Headlamp deploy. Headlamp login
   will return errors until the website api OIDC issuer lands in its
   own PR; the pod is otherwise healthy.
6. `apps-image-automation` — one ImageRepository per service. Each
   reports Ready with a tag count once VSO has materialised
   `flux-system/ghcr-pull-secret`; before that the scan reports 401 and
   retries. `flux -n flux-system get images repository` shows the state.

## Secrets bootstrap

`apps-edge` depends on a `cloudflare-api-token` Secret in the
`cert-manager` and `external-dns` namespaces (referenced by both
cert-manager's Cloudflare DNS-01 solver and external-dns's provider
config). Those Secrets are materialised by VSO once Vault is installed
and the `VaultStaticSecret` CRs land — see the Vault + VSO PR.

Until Vault is up, the ACME order will retry indefinitely. That is
acceptable: cert-manager keeps the Certificate in `Issuing` state and
issues as soon as the Secret appears.
