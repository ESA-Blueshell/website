# Bringing up the v2 stack on the Frankfurt Contabo VPS

Runs once, to stand up the new NixOS + k3s + Flux stack at
`*.v2.esa-blueshell.nl`. The old Swarm VPS at the apex keeps serving
users throughout — there is no cutover moment here.

The separate apex cutover (`esa-blueshell.nl` → new VPS, strip the
`v2.` prefix) happens in a later PR, only after the v2 stack has been
observed running in production traffic.

## Target VPS

- **Contabo VPS 20** — 6 vCPU / 12 GB RAM / 400 GB NVMe SSD
- **Public IPv4:** `157.173.115.164` (permanent — baked into the flake)
- **Hostname (nix + k8s):** `frankfurt-contabo-1`
- **DNS:** `v2.esa-blueshell.nl` + wildcard `*.v2.esa-blueshell.nl`
- **SSH:** `~/.ssh/bs-deploy` throughout
  - Pre-install: `admin@157.173.115.164:2222` (Debian base image; the
    `admin` account disappears once NixOS is installed).
  - Post-install: `deploy@157.173.115.164:2222`.

Historical: the VPS was originally provisioned with a different SSH
key (`~/.ssh/blueshell-admin`). The `bs-deploy` key was installed as
the canonical operator identity with one `ssh-copy-id` bootstrap; the
old admin key is no longer used and can be retired after a successful
install.

## 0. Workstation pre-flight

Confirm local tooling:

```bash
nix --version
```
```bash
kubectl version --client
```
```bash
flux --version
```
```bash
gh auth status
```

Nix flakes + `nix-command` must be enabled. One-time setup if not
already active (every `nix run` / `nix flake` step below assumes it):

```bash
mkdir -p ~/.config/nix && echo 'experimental-features = nix-command flakes' >> ~/.config/nix/nix.conf
```

Confirm the pre-install SSH path works and has passwordless sudo
(required for `nixos-anywhere` to elevate during kexec):

```bash
ssh -i ~/.ssh/bs-deploy -o IdentitiesOnly=yes -p 2222 admin@157.173.115.164 'sudo -n true && echo sudo-ok'
```

## 1. Deploy.pub is already populated

`platform/nix/authorized-keys/deploy.pub` is tracked in the repo and
already contains the `bs-deploy` public key. If you later rotate the
operator set, regenerate and commit:

```bash
cat platform/nix/authorized-keys/bs-deploy.pub > platform/nix/authorized-keys/deploy.pub
```
```bash
git commit -am "authorized-keys: rotate operator set" && git push
```

## 2. Host config has the real addresses

The flake at `platform/nix/hosts/frankfurt-contabo-1/default.nix`
carries the full production addressing baked in: IPv4
`157.173.115.164/24` gateway `157.173.115.1`, IPv6
`2a02:c207:2316:2642::1/64` gateway `fe80::1` (link-local). No further
nix edits are needed before install.

```bash
nix --extra-experimental-features 'nix-command flakes' flake check ./platform --no-build
```

## 3. Run nixos-anywhere

Kexecs the Debian box into a NixOS installer, runs disko, copies the
closure, reboots into NixOS (8–15 min total over the uplink):

Flake URL is single-quoted because zsh's `extendedglob` treats `#` as
a wildcard; bash tolerates both forms, the quotes just make the
command shell-portable:

```bash
nix run github:nix-community/nixos-anywhere -- --flake './platform#frankfurt-contabo-1' --target-host admin@157.173.115.164 --ssh-port 2222 --ssh-option IdentityFile=~/.ssh/bs-deploy --ssh-option IdentitiesOnly=yes
```

After reboot, the `admin` account is gone; log in as `deploy`:

```bash
ssh -i ~/.ssh/bs-deploy -o IdentitiesOnly=yes -p 2222 deploy@157.173.115.164 'systemctl is-active k3s && kubectl get nodes'
```

Expected: `active` + one Ready node named `frankfurt-contabo-1`.

## 4. Pull kubeconfig onto the workstation

```bash
scp -i ~/.ssh/bs-deploy -o IdentitiesOnly=yes -P 2222 deploy@157.173.115.164:/etc/rancher/k3s/k3s.yaml /tmp/k3s.yaml
```
```bash
sed -i '' 's|127.0.0.1|157.173.115.164|' /tmp/k3s.yaml
```
```bash
export KUBECONFIG=/tmp/k3s.yaml
```
```bash
kubectl get nodes
```

Merge `/tmp/k3s.yaml` into `~/.kube/config` under a named context
(`blueshell-fra`) once you're confident.

## 5. Bootstrap Flux

```bash
kubectl apply -k platform/cluster/flux/clusters/production/flux-system
```
```bash
kubectl -n flux-system create secret generic flux-system --from-literal=username=flux --from-literal=password="$(gh auth token)"
```
```bash
flux get kustomizations --watch
```

Reconciliation order: `flux-system` → `apps-core` (2–5 min) →
`apps-data` (stalls pending Vault unseal — expected, proceed to
step 6 in parallel) → `apps-edge` / `apps-vso-secrets` /
`apps-mail` / `apps-stateless` / `apps-utility-system`.

## 6. Unseal Vault and seed secrets

Before unsealing, gather every external token and dotenv file the seed
flow needs — see the **Day-0 checklist** at the top of
[`vault-bootstrap.md` §4](vault-bootstrap.md#4-seed-static-secrets).
Doing this on the workstation first is much faster than discovering a
missing `cloudflare.dns_api_token` mid-bootstrap.

Port-forward and point the CLI at Vault:

```bash
kubectl -n data-system port-forward svc/vault 8200:8200 &
```
```bash
export VAULT_ADDR=http://127.0.0.1:8200
```

Follow [`vault-bootstrap.md`](vault-bootstrap.md) end-to-end from §1
(init + unseal, 5-of-3 Shamir) through §6 (revoke root token). That
runbook is the canonical source for every `vault kv put` command and
for the Transit signing key the OIDC issuer needs — keeping the detail
in one place prevents the two docs from drifting.

Once the bootstrap sequence has completed, confirm VSO materialised
every VaultStaticSecret as a Kubernetes Secret:

```bash
kubectl get vaultstaticsecret -A
```
```bash
kubectl get secret -A | grep -E 'api-secrets|listmonk-secrets|stalwart-secrets|cloudflare'
```

Later, once `listmonk-setup` has run successfully, also expect
`default/listmonk-api-token`.

## 7. Wait for the wildcard cert + IngressRoutes

```bash
kubectl -n edge-system get certificate -w
```

Expect `wildcard-v2-esa-blueshell-nl` → Ready in 3–8 min (DNS-01
propagation).

```bash
kubectl -n edge-system get ingressroute
```

Expect: `api`, `frontend`, `headlamp`, `listmonk`, `stalwart-admin`,
`status`, `traefik-dashboard`, `vault`.

## 8. Migrate data from the old VPS

Capture the new MariaDB root password once so the pipe is a clean
one-liner:

```bash
export MARIADB_ROOT=$(kubectl -n data-system get secret mariadb-credentials -o jsonpath='{.data.mariadb-root-password}' | base64 -d)
```

MariaDB. Pick one source: direct from the old VPS or an already-copied
dump file on your workstation. The old Swarm usernames/passwords are
not needed by the new stack once the dump lands in the new `blueshell`
database.

```bash
ssh old-vps 'mysqldump --single-transaction --routines --triggers blueshell' | kubectl -n data-system exec -i mariadb-0 -- mysql -uroot -p"$MARIADB_ROOT" blueshell
```

```bash
cat /path/to/blueshell.sql | kubectl -n data-system exec -i mariadb-0 -- mysql -uroot -p"$MARIADB_ROOT" blueshell
```

Listmonk Postgres:

```bash
ssh old-vps 'pg_dump -Fc listmonk' | kubectl -n data-system exec -i listmonk-db-0 -- pg_restore -d listmonk --clean --if-exists
```

User-uploaded storage:

```bash
ssh old-vps 'tar -C /src/website -cf - storage' | kubectl -n default exec -i "$(kubectl -n default get pod -l app.kubernetes.io/name=api -o name | head -1)" -- tar -C /home -xf -
```

```bash
tar -C /path/to/storage-parent -cf - storage | kubectl -n default exec -i "$(kubectl -n default get pod -l app.kubernetes.io/name=api -o name | head -1)" -- tar -C /home -xf -
```

If the direct pipes are flaky over the uplink, relay via a scratch
S3/B2 bucket: dump → upload → `kubectl cp` from workstation → restore.

## 9. DNS

Cloudflare zone `esa-blueshell.nl`:

- `v2.esa-blueshell.nl` A → `157.173.115.164`, AAAA →
  `2a02:c207:2316:2642::1`, both proxied (orange cloud).
- `*.v2.esa-blueshell.nl` — external-dns auto-materialises records
  from every IngressRoute; no manual action needed.
- `stalwart.v2.esa-blueshell.nl` A → `157.173.115.164`, **grey cloud**
  (Cloudflare cannot proxy SMTP/IMAP).
- Leave `esa-blueshell.nl` apex and `www.esa-blueshell.nl` pointed at
  the old VPS. The apex cutover is PR 10.

## 10. Verify

```bash
curl -I https://v2.esa-blueshell.nl
```
```bash
curl https://v2.esa-blueshell.nl/api/health
```
```bash
curl https://v2.esa-blueshell.nl/api/.well-known/openid-configuration | jq .issuer
```
```bash
curl https://status.v2.esa-blueshell.nl/
```
```bash
SYSTEM_TEST_BASE_URL=https://v2.esa-blueshell.nl ./gradlew :services:system-tests:test
```
```bash
vault login -method=oidc
```

All Gatus monitors green on `https://status.v2.esa-blueshell.nl` is
the headline check — it exercises every endpoint the apex cutover
will eventually depend on.

## 11. Retire the old admin key

Only once the install has succeeded and `bs-deploy` is confirmed
working end-to-end:

```bash
mv ~/.ssh/blueshell-admin ~/.ssh/blueshell-admin.retired
```
```bash
mv ~/.ssh/blueshell-admin.pub ~/.ssh/blueshell-admin.pub.retired
```

## Exit criteria for PR 9

- Gatus monitors green for ≥72 h straight.
- No pod restarts on api / frontend / listmonk / stalwart / vault in
  the last 72 h.
- `kubectl top pods -A` shows ≥25 % RAM headroom per namespace.
- `vault login -method=oidc` + Headlamp login both succeed and land
  the user at `cluster-admin`.
- `:services:system-tests:test` passes against
  `https://v2.esa-blueshell.nl`.

Once all five hold, open PR 10 (apex cutover).

## Ongoing updates

- **Platform** (anything under `platform/nix/`):

  ```bash
  nix flake update ./platform
  ```
  ```bash
  git commit -am 'platform(nix): bump inputs' && git push
  ```
  ```bash
  nix run 'nixpkgs#deploy-rs' -- './platform#frankfurt-contabo-1'
  ```

  The flake's `deploy.nodes.frankfurt-contabo-1.hostname` is pinned to
  the permanent IPv4 (`157.173.115.164`), so a DNS flip during the
  apex cutover (PR 10) doesn't send deploy-rs at a stale box.
  `deploy-rs` auto-rolls back if the post-activation SSH health check
  fails.

- **Apps** (anything under `platform/cluster/flux/`): Flux reconciles
  `main` every minute. Keel polls GHCR every 2 min and rolls api +
  frontend when their `:latest` digest changes. No manual step.
