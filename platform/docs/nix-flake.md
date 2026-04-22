# NixOS flake

The `platform/` tree defines a single-node NixOS + k3s deployment for the
production VPS.

```
platform/
├── flake.nix                                 flake inputs + nixosConfigurations
├── nix/
│   ├── authorized-keys/
│   │   ├── README.md                         how to compose deploy.pub
│   │   └── .gitignore                        deploy.pub is local-only
│   ├── hosts/
│   │   └── frankfurt-contabo-1/
│   │       ├── default.nix                   hostname, networking, boot loader
│   │       └── disko.nix                     GPT + bios_grub disk layout
│   └── modules/
│       ├── base/default.nix                  SSH :2222, DNS, fail2ban, deploy user
│       ├── k3s/bootstrap.nix                 firewall for k3s + web + mail
│       └── roles/single-node.nix             k3s server with OIDC trust for the api
```

## First-time provisioning

The target Contabo VPS 20 is already provisioned at `157.173.115.164`,
reachable as `admin@157.173.115.164:2222` with key `~/.ssh/blueshell-admin`
(passwordless sudo required for nixos-anywhere to elevate). The flake
carries the IPv4 + gateway; the IPv6 address must be filled in before
install. End-to-end runbook: `bringup-v2.md`.

1. Fill in the IPv6 in
   `platform/nix/hosts/frankfurt-contabo-1/default.nix`
   (replace `REPLACE_WITH_VPS_IPV6`).
2. Compose `platform/nix/authorized-keys/deploy.pub` from every
   operator's public key (one per line; the flake bakes it into the
   post-install `deploy` account).
3. From a workstation with Nix installed:
   ```
   nix run github:nix-community/nixos-anywhere -- \
     --flake ./platform#frankfurt-contabo-1 \
     --target-host admin@157.173.115.164 \
     --ssh-port 2222 \
     --ssh-option IdentityFile=~/.ssh/blueshell-admin
   ```
4. Reboot. SSH reaches the VPS at `deploy@157.173.115.164:2222` with
   whichever key is in `deploy.pub`.

## Ongoing updates

```
nix run nixpkgs#deploy-rs -- ./platform#frankfurt-contabo-1
```

`deploy-rs` uses the SSH keys in `platform/nix/authorized-keys/deploy.pub`
and activates a new profile with an automatic rollback window.

## Local validation

CI runs `nix flake check ./platform` on every PR that touches the nix tree.
Run it locally the same way:

```
nix flake check ./platform --no-build
```
