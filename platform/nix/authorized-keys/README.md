# authorized-keys

Public SSH keys that end up in `authorized_keys` for the `deploy` user
on every NixOS host built from this flake.

## Layout

- `bs-deploy.pub` — **tracked**. The flake's base module reads this
  file at evaluation time and bakes its contents into
  `users.users.deploy.openssh.authorizedKeys.keys`. Must be committed:
  Nix flakes filter the source tree with `git ls-files`, so a
  gitignored pubkey would be silently missing from the store copy at
  install time, producing a deploy user with no authorized keys and
  no way in.

The base module emits a build-time warning if `bs-deploy.pub` is
missing or empty, so first-time installs catch the omission before
the host boots without any authorized keys.

Private keys (`id_ed25519`, `bs-deploy` without the `.pub` suffix,
etc.) never belong in this directory or anywhere else in the repo.
They live on each operator's own machine under `~/.ssh/`.

## First-time add

```
ssh-keygen -t ed25519 -f ~/.ssh/bs-deploy -C 'bs-deploy@ops'
cp ~/.ssh/bs-deploy.pub platform/nix/authorized-keys/bs-deploy.pub
git add platform/nix/authorized-keys/bs-deploy.pub
git commit -m "authorized-keys: add bs-deploy"
```

## Rotation

Overwrite `bs-deploy.pub` with the new key and commit. Apply via
`deploy-rs` (or re-run `nixos-anywhere` for the install path).

## Adding a second operator

The base module reads one file. If more than one operator needs SSH
access, either:

1. Append additional public keys to `bs-deploy.pub` directly — one
   key per line. The flake parser tolerates multiple lines and blank
   lines.
2. Restore the aggregator pattern by adding a `cat *.pub > merged.pub`
   step to the flake and pointing the module at `merged.pub`.
