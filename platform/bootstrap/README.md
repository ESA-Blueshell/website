# Bootstrap

What the cluster needs that git cannot hold: Flux's deploy key, Vault's shares
and every secret a person puts in it, and the mail setup that lives at Contabo
and Microsoft. Three scripts. Each walks you through each step, checks first and
skips what is already right, asks before every write, reads back what it wrote,
and is safe to re-run.

| script | run | does |
|---|---|---|
| [`cluster.sh`](cluster.sh) | on a new cluster, after a restore, and to check a running one | installs Flux and its read-only deploy key, initialises Vault (each share shown once, never written to disk), unseals it, runs the bootstrap Job with the root token, runs `secrets.sh`, runs the Job again with the seeded values, checks every Secret VSO syncs, revokes the root token and ends with `mail.sh` |
| [`cluster.sh --unseal`](cluster.sh) | after every node restart | asks for shares on a hidden prompt until Vault opens |
| [`secrets.sh`](secrets.sh) | after Vault is unsealed, and whenever a value is added or replaced | every Vault path a person writes, one section each: `edge` (Cloudflare token), `ghcr` (package pull credential), `flux-git` (image automation's write deploy key), `mariadb`, `mail` (Stalwart's admin, every mailbox in `infra/stalwart/accounts.json` and the Brevo relay login), `api` (signing keys, Vault OIDC client secret, Brevo, Google Calendar, Discord) and `alerting` (Discord webhook). `backup` (Kopia password and Scaleway writer key) only reports in a full run, and acts with `--only backup` |
| [`mail.sh`](mail.sh) | after a mail change, and when mail bounces | checks MX, SPF, DKIM against Stalwart's selectors, DMARC, MTA-STS and its policy file, TLS-RPT, reverse DNS, the SMTP greeting and certificate, eight blocklists, the postmaster, abuse and catch-all addresses and recent Outlook.com rejections, and whether the Brevo relay for Microsoft's addresses is on, and walks the Contabo reverse DNS, Microsoft SNDS, JMRP and delisting, and Brevo domain authentication steps |

## On a new cluster

1. Install NixOS and k3s, and pull the kubeconfig:
   [`bringup-v2.md`](../docs/bringup-v2.md) steps 1 to 4.
2. Run `platform/bootstrap/cluster.sh`. With dotenv files at hand, add
   `--from-env services/api/.api.env --from-env services/api/.db.env` and
   `secrets.sh` takes what it finds there instead of asking.
3. Vault signs in through the site, so OIDC needs the api up. If the api was not
   ready when `cluster.sh` reached its last stages, it keeps the root token and
   says so: run `cluster.sh` again once the site loads. Everything done is
   skipped, the bootstrap Job writes the OIDC config, and the root token is
   revoked.
4. An owner runs `platform/bootstrap/secrets.sh --only backup` with a Scaleway
   key ([`backup.md`](../docs/backup.md)).

A restored Vault already holds every secret, so `secrets.sh` only checks it.

## secrets.sh

```bash
platform/bootstrap/secrets.sh                          # check everything, fix what is missing
platform/bootstrap/secrets.sh --list                   # the sections
platform/bootstrap/secrets.sh --only mail              # after adding a mailbox to accounts.json
platform/bootstrap/secrets.sh --only api --rotate      # offer a new value for each key that may change
platform/bootstrap/secrets.sh --from-env extra.env     # take missing values from a dotenv file
platform/bootstrap/secrets.sh --only backup --new-writer-key
```

`--rotate` never replaces a key whose replacement breaks stored state: the
two-factor key and MariaDB's logins have their own rotations in
[`vault-bootstrap.md`](../docs/vault-bootstrap.md). A value it can test is tested
before it is written: the Cloudflare token against the zone, the ghcr credential
by pulling the api image's tag list, the Brevo key, the Discord bot token and
webhook against their APIs, and the Google service account by its shape.

**A dry run.** `BLUESHELL_VAULT_ADDR` points at a Vault directly and skips every
cluster step and live check:

```bash
vault server -dev -dev-root-token-id=root -dev-listen-address=127.0.0.1:18299 &
BLUESHELL_VAULT_ADDR=http://127.0.0.1:18299 VAULT_TOKEN=root platform/bootstrap/secrets.sh
```

`cluster.sh` dry-runs the same way against a `vault server` with file storage,
which starts uninitialised.

## What they need

`kubectl` with a context for the cluster (`blueshell`, or name it with
`KUBE_CONTEXT`; never the current context), `flux`, `gh` signed in as a
repository admin (deploy keys), the `vault` CLI, `jq`, `curl`, `openssl`,
`ssh-keygen`, `dig`, `nc`, `perl` and bash 4 or newer (`brew install bash`, then
run the scripts with that bash). Vault is reached through a port-forward to
`vault-v2-0`; sign-in is OIDC through the site, or a token you paste on a Vault
too new for OIDC. A token a script signs in for is revoked when it ends.

Every secret reaches Vault on stdin, never as an argument, and is shown only as
a 12-character SHA-256 fingerprint. Values that are not secret, such as the
Scaleway organization id and which Microsoft steps are done, are kept in
`~/.config/blueshell-bootstrap.env` (`BLUESHELL_ENV_FILE` overrides).

`lib/wizard.sh` is the shared wizard library, kept verbatim from
`JorisJonkers-dev/estate/bootstrap`. [`lib/blueshell.sh`](lib/blueshell.sh) is
what the scripts share above it: the cluster and Vault they act on, the sign-in,
the KV reads and writes and the bootstrap Job re-run.
