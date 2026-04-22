# Bringing up the v2 stack on a fresh VPS

Runs once, to stand up the new NixOS + k3s + Flux stack at
`*.v2.esa-blueshell.nl`. The old Swarm VPS at the apex keeps serving
users throughout — there is no cutover moment here.

The separate apex cutover (`esa-blueshell.nl` → new VPS, strip the
`v2.` prefix) happens in a later PR, only after the v2 stack has been
observed running in production traffic.

## 0. Workstation pre-flight

```bash
nix --version                      # flakes support (≥2.4)
kubectl version --client
flux --version
gh auth status                     # repo:read on ESA-Blueshell/website

# One-time: add your SSH key to the deploy bundle the flake bakes in.
cat ~/.ssh/id_ed25519.pub >> platform/nix/authorized-keys/deploy.pub
# Add other operators' keys the same way. deploy.pub is gitignored.
```

## 1. Provision the Contabo VPS

Order a VPS (minimum VPS M: 4 vCPU / 8 GB / 200 GB NVMe; VPS L is
comfortable). Pick the **Debian 12** cloud-init image — the easiest
base for `nixos-anywhere` to kexec into.

Record:

- `NEW_V4` — public IPv4
- `NEW_V6` — public IPv6
- `NEW_GW4` — IPv4 gateway (Contabo panel → Networking)

Verify SSH:

```bash
ssh -o StrictHostKeyChecking=accept-new root@$NEW_V4   # password auth
```

## 2. Bake the VPS addresses into the host module

```bash
$EDITOR platform/nix/hosts/blueshell-fra-1/default.nix
```

Replace the three `REPLACE_WITH_VPS_*` placeholders with
`NEW_V4`/`NEW_V6`/`NEW_GW4`, commit, push:

```bash
nix flake check ./platform --no-build
git checkout -b platform/blueshell-fra-1-ips
git commit -am "platform(nix): blueshell-fra-1 production addresses"
git push -u origin platform/blueshell-fra-1-ips
gh pr create --assignee ExtraToast --label enhancement --title "..."
# merge before step 3 so the flake on main has the real IPs.
```

## 3. Run nixos-anywhere

From the repo root (the flake is at `./platform/flake.nix`):

```bash
nix run github:nix-community/nixos-anywhere -- \
  --flake ./platform#blueshell-fra-1 \
  --target-host root@$NEW_V4
```

What happens:

1. A kexec image uploads and kexecs the Debian box into a NixOS
   installer.
2. `disko` repartitions with the BIOS/GRUB GPT + `bios_grub` layout
   (Contabo KVM is BIOS-only).
3. The flake's `nixosConfigurations.blueshell-fra-1` is built and
   installed.
4. Reboot. 8–15 min total, most of it rebuild over the uplink.

After reboot:

```bash
ssh -p 2222 deploy@$NEW_V4           # key-only, no password
systemctl status k3s                 # active (running) within ~1 min
kubectl get nodes                    # Ready within 30–60 s
```

If k3s is not up: `journalctl -u k3s --since="5 min ago"`. The
single-node role disables Traefik and ServiceLB so the base cluster is
quiet until Flux lands.

## 4. Pull kubeconfig onto the workstation

```bash
scp -P 2222 deploy@$NEW_V4:/etc/rancher/k3s/k3s.yaml /tmp/k3s.yaml
# Rewrite the server URL from 127.0.0.1 to the public IP.
sed -i '' "s|127.0.0.1|$NEW_V4|" /tmp/k3s.yaml
export KUBECONFIG=/tmp/k3s.yaml
kubectl get nodes                    # confirm from workstation
```

Once you're happy, merge `/tmp/k3s.yaml` into `~/.kube/config` under a
named context (`blueshell-fra-1`).

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
3. `apps-data` — **stalls**, waiting for Vault unseal (step 6). This
   is expected; proceed in parallel.
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

- `v2.esa-blueshell.nl` A/AAAA → `NEW_V4` / `NEW_V6`, proxied (orange).
- `*.v2.esa-blueshell.nl` — external-dns materialises A/CNAME records
  for every IngressRoute automatically. Leave Cloudflare's API token
  in `secret/platform/edge` and external-dns does the rest.
- `stalwart.v2.esa-blueshell.nl` A → `NEW_V4`, **DNS only** (grey
  cloud). Cloudflare cannot proxy SMTP/IMAP, only HTTP(S).
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
  nix run nixpkgs#deploy-rs -- ./platform#blueshell-fra-1
  ```
  `deploy-rs` auto-rolls back if the post-activation SSH health check
  fails.
- **Apps** (anything under `platform/cluster/flux/`): Flux reconciles
  `main` every minute. Keel polls GHCR every 2 min and rolls api +
  frontend when their `:latest` digest changes. No manual step.
