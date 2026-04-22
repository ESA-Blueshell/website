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

## SSH keys

Two distinct keys, two distinct roles:

| Role | Pubkey | Privkey on workstation | Accepted by |
|---|---|---|---|
| **Transport** — drives `nixos-anywhere` over the admin account on the pre-install Debian box | `~/.ssh/blueshell-admin.pub` and (after ssh-copy-id) `~/.ssh/bs-deploy.pub` | `~/.ssh/blueshell-admin` initially, `~/.ssh/bs-deploy` afterwards | `admin@157.173.115.164:2222` |
| **Post-install login** — baked into the NixOS image via the flake | `platform/nix/authorized-keys/deploy.pub` (tracked in git) | `~/.ssh/bs-deploy` | `deploy@157.173.115.164:2222` |

## 0. Workstation pre-flight

```bash
nix --version                      # flakes support (≥2.4)
kubectl version --client
flux --version
gh auth status                     # repo:read on ESA-Blueshell/website

# Confirm the pre-install SSH path works with the existing admin key.
ssh -i ~/.ssh/blueshell-admin -p 2222 admin@157.173.115.164 'sudo -n true && echo sudo-ok'
```

The last command must print `sudo-ok`. nixos-anywhere elevates via
passwordless sudo; if it fails, enable passwordless sudo for `admin`
on the Debian base image before running step 3.

### Install bs-deploy as an accepted key on the admin account

Going forward we want to use the `bs-deploy` key for everything —
including the nixos-anywhere transport. `ssh-copy-id` appends it to
`admin`'s `~/.ssh/authorized_keys` using the old admin key to
authenticate the initial connection:

```bash
ssh-copy-id -i ~/.ssh/bs-deploy.pub \
  -o IdentityFile=~/.ssh/blueshell-admin \
  -o IdentitiesOnly=yes \
  -p 2222 \
  admin@157.173.115.164

# Verify bs-deploy now works end-to-end.
ssh -i ~/.ssh/bs-deploy -o IdentitiesOnly=yes -p 2222 admin@157.173.115.164 'whoami'
```

### Verify the deploy.pub bundle is in git

`platform/nix/authorized-keys/deploy.pub` is tracked in the repo (see
that directory's README for why). If you're rotating the operator set,
regenerate and commit:

```bash
cat platform/nix/authorized-keys/bs-deploy.pub \
  > platform/nix/authorized-keys/deploy.pub
git diff --stat platform/nix/authorized-keys/
git commit -am "authorized-keys: rotate operator set"
git push       # must be on main before nixos-anywhere runs
```

## 1. VPS is already provisioned

Not applicable — the Contabo VPS 20 at `157.173.115.164` already exists
and is reachable as `admin` on port 2222. Skip directly to step 3.

## 2. (Already done) Nix host config has the real addresses

The flake at `platform/nix/hosts/frankfurt-contabo-1/default.nix`
carries the full production addressing baked in: IPv4
`157.173.115.164/24` gateway `157.173.115.1`, IPv6
`2a02:c207:2316:2642::1/64` gateway `fe80::1` (link-local). No further
nix edits are needed before install.

```bash
nix flake check ./platform --no-build      # sanity
```

## 3. Run nixos-anywhere

nixos-anywhere kexecs into a NixOS installer over SSH, then runs disko
and copies the fully-built system closure. From the repo root:

```bash
nix run github:nix-community/nixos-anywhere -- \
  --flake ./platform#frankfurt-contabo-1 \
  --target-host admin@157.173.115.164 \
  --ssh-port 2222 \
  --ssh-option IdentityFile=~/.ssh/bs-deploy \
  --ssh-option IdentitiesOnly=yes
```

What happens:

1. The admin account + sudo uploads a kexec image and kexecs the box
   into a NixOS installer over the same SSH session.
2. `disko` repartitions `/dev/sda` with the GPT + `bios_grub` layout
   (Contabo KVM is BIOS-only).
3. The flake's `nixosConfigurations.frankfurt-contabo-1` is built and
   installed, then the box reboots.
4. Total time 8–15 min, most of it closure transfer over the uplink.

After reboot:

```bash
ssh -i ~/.ssh/bs-deploy -o IdentitiesOnly=yes -p 2222 deploy@157.173.115.164 -- \
  'systemctl is-active k3s && kubectl get nodes'
```

Expected: `active` then `frankfurt-contabo-1 Ready control-plane 60s v1.xx.x`.

If k3s isn't up, check `journalctl -u k3s --since="5 min ago"`. The
single-node role disables Traefik and ServiceLB so the base cluster is
quiet until Flux lands.

## 4. Pull kubeconfig onto the workstation

```bash
scp -P 2222 -i ~/.ssh/bs-deploy -o IdentitiesOnly=yes \
  deploy@157.173.115.164:/etc/rancher/k3s/k3s.yaml /tmp/k3s.yaml

# k3s writes the server URL as 127.0.0.1; rewrite to the public IP.
sed -i.bak "s|127.0.0.1|157.173.115.164|" /tmp/k3s.yaml && rm /tmp/k3s.yaml.bak

export KUBECONFIG=/tmp/k3s.yaml
kubectl get nodes                    # confirm from workstation
```

Merge into `~/.kube/config` under a named context once you're happy.

## 5. Bootstrap Flux

Full detail in `flux-bootstrap.md`; short version:

```bash
kubectl apply -k platform/cluster/flux/clusters/production/flux-system

kubectl -n flux-system create secret generic flux-system \
  --from-literal=username=flux \
  --from-literal=password=$(gh auth token)

flux get kustomizations --watch
```

Expected reconciliation order:

1. `flux-system` — Ready (seconds).
2. `apps-core` — Ready (2–5 min: cert-manager, external-dns, Traefik,
   VSO install).
3. `apps-data` — **stalls**, waiting for Vault unseal (step 6).
   Expected; proceed in parallel.
4. `apps-edge` — Ready once Vault is unsealed and VSO has materialised
   the Cloudflare API token Secret for cert-manager + external-dns.
5. `apps-vso-secrets` — Ready once Vault is seeded (step 6).
6. `apps-mail`, `apps-stateless`, `apps-utility-system` — Ready in
   turn.

## 6. Unseal Vault and seed secrets

Full sequence in `vault-bootstrap.md`; short version:

```bash
kubectl -n data-system port-forward svc/vault 8200:8200 &
export VAULT_ADDR=http://127.0.0.1:8200

# First time only — init. Save the 5 unseal keys + root token offline.
vault operator init -key-shares=5 -key-threshold=3

# Unseal (3/5)
vault operator unseal <key1>
vault operator unseal <key2>
vault operator unseal <key3>

vault login <root-token>

# Watch the bootstrap Job enable auth / transit / database engines.
kubectl -n data-system logs -l app.kubernetes.io/name=vault-bootstrap-auth -f

# Seed static secrets (see vault-bootstrap.md for all paths)
vault kv put secret/platform/edge      cloudflare.dns_api_token=<...>
vault kv put secret/platform/mariadb   root-password=<...> user=blueshell password=<...>
vault kv put secret/platform/mail      admin-user=admin admin-password=<...> \
                                        dkim-private-key=<base64-pem> \
                                        bounce-mailbox-user=bounce@v2.esa-blueshell.nl \
                                        bounce-mailbox-password=<...>
vault kv put secret/listmonk           db-admin-password=<...> db-password=<...> \
                                        admin-user=listmonk-admin admin-password=<...> \
                                        smtp-password=<...>
vault kv put secret/api                brevo-api-key=<...> mollie-api-key=<...> \
                                        google-calendar-sa-json=<base64> \
                                        facebook-app-secret=<...> x-api-secret=<...> \
                                        vault-oidc-client-secret=$(openssl rand -hex 32)

# Create the Transit signing key for the OIDC issuer (PR 8).
vault write -f transit/keys/api-jwt type=rsa-2048
vault write transit/keys/api-jwt/config deletion_allowed=false

# Confirm VSO materialised the Kubernetes Secrets.
kubectl get vaultstaticsecret -A
kubectl get secret -A | grep -E 'api-secrets|listmonk-secrets|stalwart-secrets|cloudflare'
```

## 7. Wait for the wildcard cert + IngressRoutes

```bash
kubectl -n edge-system get certificate -w
# wildcard-v2-esa-blueshell-nl → Ready (3–8 min; DNS-01 propagation)

kubectl -n edge-system get ingressroute
# api, auth, frontend, headlamp, listmonk, stalwart-admin, status
```

## 8. Migrate data from the old VPS

```bash
# MariaDB → new cluster's MariaDB
ssh old-vps "mysqldump --single-transaction --routines --triggers blueshell" \
  | kubectl -n data-system exec -i mariadb-0 -- \
      mysql -uroot -p"$(kubectl -n data-system get secret mariadb-secrets -o jsonpath='{.data.root-password}' | base64 -d)" blueshell

# Listmonk Postgres → new listmonk-db
ssh old-vps "pg_dump -Fc listmonk" \
  | kubectl -n data-system exec -i listmonk-db-0 -- \
      pg_restore -d listmonk --clean --if-exists

# Storage (user uploads) → new api pod's PVC
ssh old-vps "tar -C /src/website -cf - storage" \
  | kubectl -n default exec -i "$(kubectl -n default get pod -l app.kubernetes.io/name=api -o name | head -1)" -- \
      tar -C /home -xf -
```

If the direct pipes are flaky over the uplink, relay through a scratch
S3/B2 bucket: dump → upload → workstation download → `kubectl cp` into
the target pod → restore.

## 9. DNS

Cloudflare zone `esa-blueshell.nl`:

- `v2.esa-blueshell.nl` A → `157.173.115.164`, proxied (orange cloud).
  AAAA → VPS IPv6, proxied.
- `*.v2.esa-blueshell.nl` — external-dns auto-materialises these from
  every IngressRoute once Flux is reconciling. Leave Cloudflare's API
  token in `secret/platform/edge` and external-dns does the rest.
- `stalwart.v2.esa-blueshell.nl` A → `157.173.115.164`, **DNS only**
  (grey cloud). Cloudflare cannot proxy SMTP/IMAP, only HTTP(S).
- Leave `esa-blueshell.nl` apex and `www.esa-blueshell.nl` pointed at
  the old VPS.

## 10. Verify

```bash
# External smoke tests
curl -I https://v2.esa-blueshell.nl
curl https://api.v2.esa-blueshell.nl/health
curl https://auth.v2.esa-blueshell.nl/.well-known/openid-configuration | jq .issuer
curl https://status.v2.esa-blueshell.nl/                              # Gatus UI

# System tests from the repo against the new stack
SYSTEM_TEST_BASE_URL=https://v2.esa-blueshell.nl \
  ./gradlew :services:system-tests:test

# OIDC round-trips
vault login -method=oidc                                              # opens a browser
# Then https://kube.v2.esa-blueshell.nl — sign in via the api OIDC
# issuer, land in Headlamp with cluster-admin.
```

All Gatus monitors green on `https://status.v2.esa-blueshell.nl` is
the headline check — it exercises every endpoint the apex cutover
will eventually depend on.

## Exit criteria for PR 9

- Gatus monitors green for ≥72 h straight.
- No pod restarts on api / frontend / listmonk / stalwart / vault in
  the last 72 h.
- `kubectl top pods -A` shows ≥25 % RAM headroom per namespace.
- `vault login -method=oidc` + Headlamp login both succeed and land
  the user at `cluster-admin`.
- `:services:system-tests:test` passes against
  `https://v2.esa-blueshell.nl`.

Once all five hold, open PR 10 (apex cutover). Until then, the old
Swarm VPS keeps serving `esa-blueshell.nl`; users see no change.

## Ongoing updates

- **Platform** (anything under `platform/nix/`):
  ```bash
  nix flake update ./platform
  git commit -am "platform(nix): bump inputs"
  nix run nixpkgs#deploy-rs -- ./platform#frankfurt-contabo-1
  ```
  The flake's `deploy.nodes.frankfurt-contabo-1.hostname` is
  hard-pinned to `157.173.115.164`, so a DNS flip during PR 10 doesn't
  send deploy-rs at the wrong box. `deploy-rs` auto-rolls back if the
  post-activation SSH health check fails.
- **Apps** (anything under `platform/cluster/flux/`): Flux reconciles
  `main` every minute. Keel polls GHCR every 2 min and rolls api +
  frontend when their `:latest` digest changes. No manual step.
