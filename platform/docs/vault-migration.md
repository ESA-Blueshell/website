# Moving to vault-v2

Production's first Vault, the `vault` release, was initialised with one unseal key, and that key is
lost. While its pod keeps running it works. Once the pod restarts, it stays sealed for good, and
every Raft snapshot of it is unopenable too. A rekey needs the lost key, so the way out is a second
Vault, `vault-v2`, filled from the first while it is still unsealed (#2392).

**Until the switch, do not restart `vault-0`, reboot the node, or change anything in the old
Vault.** A secret written to it after the last sync does not reach `vault-v2`.

## 1. Deploy vault-v2

The `vault-v2` HelmRelease in `apps/data/vault` has the same chart and values as `vault`, without
the CSI provider, which nothing uses. Once Flux applies it, `vault-v2-0` runs but is not ready:
it is uninitialised, and nothing talks to it yet.

```bash
kubectl --context blueshell -n data-system get pod vault-v2-0
```

## 2. Copy the old Vault into it

An admin runs this on their own machine. You need `kubectl`, `vault`, `jq` and `openssl`, and
a password manager open:

```bash
scripts/migrate-vault.sh
```

It signs you in to the old Vault through the site, as `platform/bootstrap/secrets.sh` does, then:

1. **Takes stock of the old Vault.** It stops before writing anything if the old Vault has a
   mount, auth method or role that `bootstrap-auth.sh` does not make, since that would be lost.
2. **Initialises `vault-v2` 5-of-3.** It shows each share once; store each one as "Blueshell
   Vault v2, unseal share N" before pressing Enter. It then asks for shares 2, 4 and 5 back from the
   password manager, so a share copied wrong is caught while the old Vault still works. The root
   token waits in the Secret `vault-v2-bootstrap-token`, so a later run can pick up again, until
   `--finish` revokes it after the switch.
3. **Copies everything:** every KV secret under `secret/` with its metadata, the KV and audit
   settings, every policy, and the transit keys by `transit/backup` and `transit/restore`. That
   last step makes the old keys exportable, which cannot be undone, and the restored keys carry the
   same flag. The switch adds an explicit deny on `transit/export/*` and `transit/backup/*` to the
   `admin` policy, so no token but root can use it.
4. **Runs `bootstrap-auth.sh` in `vault-v2-0`**, which builds the Kubernetes and OIDC auth
   methods, the roles and the database engine, as on any fresh Vault. That includes the `bootstrap`
   role the bootstrap Job signs in with, so the Job needs no token in a Secret.
5. **Checks** that every secret and copied policy is equal, that every transit key version seals on the
   old Vault and opens on `vault-v2`, that `api-jwt` signs the same, that each Kubernetes role logs
   in, and that `vault-v2` can issue a database login, which it revokes at once.

It ends with "vault-v2 matches the old Vault", or names what does not match.

Nothing is written to disk, and no secret reaches a command line. A second run copies only what
changed, so it is also the last sync before the switch. `scripts/migrate-vault.sh --check`
compares the two without writing anything.

**If `vault-v2` is ever in a state the script cannot pick up from**, for example its root token
Secret is gone, wipe it and start again. Nothing depends on it before the switch, and the shares
you stored for it become worthless:

```bash
K="kubectl --context blueshell -n data-system"
$K scale statefulset vault-v2 --replicas=0
$K delete pvc data-vault-v2-0 audit-vault-v2-0
$K delete secret vault-v2-bootstrap-token --ignore-not-found
$K scale statefulset vault-v2 --replicas=1
```

## 3. Switch

Run the script once more from the switch PR's branch. That last sync also writes that branch's
`bootstrap-auth.sh` into `vault-v2`: the `bootstrap` role the Job will sign in with, and the deny on
transit export in `admin`. Without the role, the bootstrap Job fails once the switch lands.

```bash
git fetch origin && git checkout feat/2392-vault-v2-switch
scripts/migrate-vault.sh
```

Then merge the switch PR. It points VSO, the api and its migrate job, the three backup CronJobs,
the bootstrap Job, Gatus, the ingress and the scripts at `vault-v2`. Flux applies it. The api's
canary rolls the new address out; restart VSO so it signs in again at once:

```bash
kubectl --context blueshell -n vso-system rollout restart deployment
```

Then check:

- the `vault` endpoint on the status page is green;
- a member's address opens under Management, and you are still signed in;
- `kubectl --context blueshell get vaultstaticsecrets -A` shows every secret synced;
- the `vault-bootstrap-auth` Job in `data-system` completed.

Then take the root token away. `--finish` checks that the bootstrap Job can sign in and write
policies without it, revokes it and deletes `vault-v2-bootstrap-token`:

```bash
git checkout main && git pull
scripts/migrate-vault.sh --finish
```

From then on no root token exists, and Vault 2.0 makes none from the shares alone. Admin work
signs in through the `admin` OIDC role and a restore through the `restore` role, as
`platform/docs/vault-bootstrap.md` §6 says.

**Rollback**, while the old release still exists: revert the switch PR. The old Vault still holds
everything as it was at the last sync.

## 4. Afterwards

1. Run the nightly backup by hand, as in `backup.md`, so a `vault-v2` snapshot sits in Scaleway.
2. Run `scripts/restore.sh rehearse` with three of the new shares.
3. Hand the shares to named holders (#2097).
4. Remove the old `vault` release, no sooner than 7 days after the switch. Database logins it
   leased live up to `max_ttl`, 168 hours, and the old Vault revokes each one when it runs out; once
   it is gone, a login it leased stays in MariaDB for good. Merge the PR that drops `release.yaml`.
   Flux uninstalls the release, which takes the pod, its Services and the unused CSI provider with
   it. The volumes stay behind, so delete them:

   ```bash
   kubectl --context blueshell -n data-system delete pvc data-vault-0 audit-vault-0
   ```
