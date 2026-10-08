# The nightly backup

Every night at 03:00 Amsterdam time the CronJob `data-system/backup` stores a MariaDB dump and a
Vault Raft snapshot in the Kopia repository in `esa-blueshell-backups` on Scaleway. Both come
from one run, so they carry the same date and restore together: a database backup only opens
while Vault still holds the key versions its sealed values were sealed under. Why it is shaped
this way is architecture ADR-011; the Scaleway side is `platform/scaleway/backup`.

## What a run does

The Job (`platform/cluster/flux/apps/data/backup`) runs three steps on a memory-backed volume, so
nothing reaches the node's disk:

1. **collect** (`hashicorp/vault`) logs in to Vault as role `backup`. It takes the Raft snapshot,
   reads the Kopia password, the writer key and the Gatus token, and leases a database login
   that may only read, for an hour.
2. **dump** (`mariadb`) dumps `blueshell` in one transaction into `blueshell.sql.gz`.
3. **snapshot** (`kopia/kopia`) stores `/work/mariadb` and `/work/vault` as two snapshots and
   tells Gatus how it went.

Kopia keeps 7 daily and 4 weekly snapshots. Every blob is written under COMPLIANCE retention to
match the bucket's 30-day lock, and full maintenance extends it, so live data never falls out of
protection.

A failed step, or a night that never reports, alerts Discord through Gatus: the endpoint
`backups / nightly-backup` on the status page expects a report every 26 hours.

## Seeding the credentials

An owner does this once, and again whenever the Kopia password changes. No agent reads these
values. The rotation job (#2099) later replaces the writer key on its own.

1. **Kopia password.** Generate it on your own machine and keep it in your terminal only:

   ```bash
   KOPIA_PASSWORD=$(openssl rand -base64 32)
   ```

   Add it as the only version of `kopia-repository-password` in Scaleway Secret Manager
   (`nl-ams`). That copy is the break-glass one.
2. **Writer key.** In the Scaleway console, IAM & API keys, Generate API key: bearer the
   application `backup-writer`, expiry a year, Object Storage **Yes** with project `Website`.
3. **Into Vault**, logged in through the Vault UI's OIDC (an admin) or `vault login -method=oidc`:

   ```bash
   read -rsp "Writer access key: " AK; echo
   read -rsp "Writer secret key: " SK; echo
   vault kv put secret/platform/backup \
     kopia.password="$KOPIA_PASSWORD" writer.access_key="$AK" writer.secret_key="$SK"
   unset KOPIA_PASSWORD AK SK
   ```

   `kv put` replaces the whole path. Once #2099 adds the rotator's key to it, use
   `vault kv patch` to change one field.

`secret/platform/alerting` gains `gatus.backup_token` on its own: `bootstrap-auth.sh` seeds it.

## The first night

The first run creates the repository. To run it now rather than at 03:00:

```bash
kubectl -n data-system create job --from=cronjob/backup backup-first
kubectl -n data-system logs -f job/backup-first --all-containers
```

It ends with two `Created snapshot` lines and the status page shows `nightly-backup` green. Then
check one stored blob carries the lock and the storage class, with an owner's key:

```bash
S3="aws --endpoint-url https://s3.nl-ams.scw.cloud --region nl-ams s3api"
KEY=$($S3 list-objects-v2 --bucket esa-blueshell-backups --prefix p --max-items 1 \
  --query 'Contents[0].Key' --output text)
$S3 head-object --bucket esa-blueshell-backups --key "$KEY" \
  --query '{class:StorageClass,mode:ObjectLockMode,until:ObjectLockRetainUntilDate}'
```

It must say `ONEZONE_IA`, `COMPLIANCE` and a date 30 days out, and no `Expiration`.

Delete the manual Job afterwards with `kubectl -n data-system delete job backup-first`.

## When it alerts

Read the Job's logs: `kubectl -n data-system logs job/<job> --all-containers`. The alert carries
the first line that failed.

- **"secret/platform/backup has no …"**: the credentials are not seeded, or a field is missing.
- **"Vault refused the backup role's login"**: Vault is sealed, or `bootstrap-auth.sh` has not
  run since the role was added.
- **"Kopia could not connect: … invalid repository password"**: the password in Vault does not
  match the repository's. Never "fix" it by creating a new repository: find the right password
  in Secret Manager.
- **"Kopia could not connect: … Access Denied"**: the writer key expired or was rotated without
  updating Vault.
- **No report at all for 26 hours**: the CronJob did not start. Check
  `kubectl -n data-system get cronjob backup` and the node.

## Restoring

Restore into a throwaway Vault and a scratch database, **never over the live ones**. A snapshot
loaded into the live Vault replaces every secret written since.

**Routine**, while the cluster and its Vault are up: read the Kopia password and the writer key
from Vault (`vault kv get secret/platform/backup`), then follow `platform/recovery/README.md`
from step 2, using them in place of the Secret Manager password and the temporary key. The
writer key can read everything it wrote.

**Break-glass**, when the server and its Vault are gone: follow `platform/recovery/README.md`
from the start. A Scaleway owner reads the Kopia password from Secret Manager and makes a
temporary read key, and three holders of Vault's unseal shares open the restored Vault (#2097).

A database restored on its own, without its Vault snapshot, opens against the live Vault only
while the live Vault still holds the key versions it was sealed under.
