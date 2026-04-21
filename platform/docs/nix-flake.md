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
│   │   └── blueshell-fra-1/
│   │       ├── default.nix                   hostname, networking, boot loader
│   │       └── disko.nix                     GPT + bios_grub disk layout
│   └── modules/
│       ├── base/default.nix                  SSH :2222, DNS, fail2ban, deploy user
│       ├── k3s/bootstrap.nix                 firewall for k3s + web + mail
│       └── roles/single-node.nix             k3s server with OIDC trust for the api
```

## First-time provisioning

1. Provision a Contabo VPS with Debian (cloud-init image), note the public IPv4,
   IPv6 and v4 gateway from the cidata.
2. Edit `platform/nix/hosts/blueshell-fra-1/default.nix` and replace the three
   `REPLACE_WITH_VPS_*` placeholders.
3. Compose `platform/nix/authorized-keys/deploy.pub` from every operator's
   public key.
4. From a workstation with Nix installed:
   ```
   nix run github:nix-community/nixos-anywhere -- \
     --flake ./platform#blueshell-fra-1 root@<public-ip>
   ```
5. Reboot. SSH reaches the VPS at `deploy@<public-ip>:2222`.

## Ongoing updates

```
nix run nixpkgs#deploy-rs -- ./platform#blueshell-fra-1
```

`deploy-rs` uses the SSH keys in `platform/nix/authorized-keys/deploy.pub`
and activates a new profile with an automatic rollback window.

## Local validation

CI runs `nix flake check ./platform` on every PR that touches the nix tree.
Run it locally the same way:

```
nix flake check ./platform --no-build
```
