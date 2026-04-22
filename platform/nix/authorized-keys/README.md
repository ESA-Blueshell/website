# authorized-keys

Public SSH keys that end up in `authorized_keys` for the `deploy` user on
every NixOS host built from this flake.

## Layout

- `deploy.pub` — **tracked**. The flake's base module reads this file at
  evaluation time and bakes its contents into
  `users.users.deploy.openssh.authorizedKeys.keys`. Must be committed:
  Nix flakes filter the source tree with `git ls-files`, so a gitignored
  pubkey would be silently missing from the store copy at install time,
  producing a deploy user with no authorized keys and no way in.
- `<operator>.pub` (e.g. `bs-deploy.pub`) — **tracked**. Per-operator
  staging files. Concatenate into `deploy.pub` when rotating the key set:

  ```
  cat bs-deploy.pub alice.pub bob.pub > deploy.pub
  git add deploy.pub && git commit -m "authorized-keys: rotate operator set"
  ```

Private keys (`id_ed25519`, `bs-deploy` without the `.pub` suffix, etc.)
never belong in this directory or anywhere else in the repo. They live
on each operator's own machine under `~/.ssh/`.

## First-time add

```
ssh-keygen -t ed25519 -f ~/.ssh/bs-deploy -C 'bs-deploy@ops'
cp ~/.ssh/bs-deploy.pub platform/nix/authorized-keys/bs-deploy.pub
cat platform/nix/authorized-keys/bs-deploy.pub > platform/nix/authorized-keys/deploy.pub
```

The base module emits a build-time warning if `deploy.pub` is missing or
empty, so first-time installs catch the omission before the host boots
without any authorized keys.
