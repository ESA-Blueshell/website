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

The target Contabo VPS 20 is already provisioned at `157.173.115.164`
(full dual-stack networking baked into the flake) and reachable as
`admin@157.173.115.164:2222` with passwordless sudo. End-to-end
runbook: `bringup-v2.md`.

1. Use `ssh-copy-id` to add `~/.ssh/bs-deploy.pub` to the admin
   account's authorized_keys, using `~/.ssh/blueshell-admin` for the
   initial authentication.
2. Ensure `platform/nix/authorized-keys/deploy.pub` contains the
   public key(s) you want the post-install `deploy` user to accept
   (see that directory's README). Both files are tracked in git.
3. From a workstation with Nix installed:
   ```
   nix run github:nix-community/nixos-anywhere -- \
     --flake './platform#frankfurt-contabo-1' \
     --target-host admin@157.173.115.164 \
     --ssh-port 2222 \
     --ssh-option IdentityFile=~/.ssh/bs-deploy \
     --ssh-option IdentitiesOnly=yes
   ```
4. Reboot. SSH reaches the VPS at `deploy@157.173.115.164:2222` with
   whichever keys are in `deploy.pub`.

## Ongoing updates

```
nix run 'nixpkgs#deploy-rs' -- './platform#frankfurt-contabo-1'
```

`deploy-rs` uses the SSH keys in `platform/nix/authorized-keys/deploy.pub`
and activates a new profile with an automatic rollback window.

## Local validation

CI runs `nix flake check ./platform` on every PR that touches the nix tree.
Run it locally the same way:

```
nix flake check ./platform --no-build
```
