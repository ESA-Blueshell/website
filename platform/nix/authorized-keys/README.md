# authorized-keys

Drop each operator's SSH public key into a separate file in this directory
and combine into `deploy.pub` before provisioning. `deploy.pub` is the file
that ends up in `authorized_keys` for the `deploy` user on every NixOS host.

```
cat you.pub teammate.pub > deploy.pub
```

`deploy.pub` is gitignored on purpose — adding keys should be a conscious
local step, not something merged through a PR. The base module emits a
build-time warning when the file is missing so first-time installs catch
the omission before the host boots without any authorized keys.
