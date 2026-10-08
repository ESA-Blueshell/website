# The nightly backup

Every night everything the apps keep goes to the Kopia repository in `esa-blueshell-backups` on
Scaleway. MariaDB and Vault go in one run, so they carry the same date and restore together: a
database backup only opens while Vault still holds the key versions its sealed values were sealed
under. Why it is shaped this way is architecture ADR-011; the Scaleway side is
`platform/scaleway/backup`.

## What runs

Three CronJobs, one per namespace whose data they need, share the scripts in
`platform/cluster/flux/apps/backup-common`. Each works on a memory-backed volume, so nothing
reaches the node's disk.

| Time (Amsterdam) | CronJob | Stores | How |
|---|---|---|---|
| 03:00 | `data-system/backup` | MariaDB and Vault, Valkey, the api's uploads | a dump in one transaction and a Raft snapshot; a fresh save streamed from Valkey; the upload directory itself |
| 03:30 | `mail-system/backup` | Stalwart | Stalwart is stopped, its RocksDB store snapshotted straight from its volume, and started again |
| 03:45 | `utility-system/backup` | Gatus's history | SQLite's online backup, while Gatus runs |

Every Job first logs in to Vault with a role of its own: `backup` in `data-system`, which may also
take the Raft snapshot and lease a read-only database login, `backup-mail` and `backup-gatus`.
Each reads the Kopia password, the writer key and its own token for reporting to Gatus, from
`secret/platform/backup-report/<namespace>`, and nothing else: no Job can report another's store
done, and none reads the Discord webhook.

**Stalwart is down for a minute or two** each night: RocksDB must not be copied while it is
open. Senders retry, so no mail is lost. The snapshot step starts Stalwart again however it ends,
the Job's 30-minute deadline included, and Gatus's Stalwart probe has a 20-minute maintenance
window from 03:30. Stalwart's Deployment sets no `replicas`, so Flux does not start it again
mid-copy.

Kopia keeps 7 daily and 4 weekly snapshots of each source. Every blob is written under
COMPLIANCE retention to match the bucket's 30-day lock, and full maintenance extends it, so live
data never falls out of protection.

Each store reports to its own Gatus endpoint under `backups` on the status page:
`mariadb-vault`, `valkey`, `uploads`, `stalwart` and `gatus`. A failed store, or one that has
not reported for 26 hours, alerts Discord; the others still run and report.

## Seeding the credentials

`scripts/seed-backup-credentials.sh` does it, run by an owner on their own machine. It makes the
Kopia password and a `backup-writer` key itself and hands them straight to Vault and Secret
Manager, so neither appears on screen, in a file or on a command line. No agent runs it.

It is safe to run again: it fills in only what is missing, stops without writing when Vault and
Secret Manager disagree about the password, and never replaces a password the repository already
opens with.

**You need** `kubectl` with the cluster's kubeconfig, `vault`, `jq` and `openssl`
(`brew install hashicorp/tap/vault jq`), your own Scaleway API key (the one OpenTofu uses), the
organization ID and an admin login to the site. Then:

```bash
scripts/seed-backup-credentials.sh
```

It asks for your Scaleway secret key without showing it, and for the organization ID. It shows
the `kubectl` context it is about to use, `blueshell` unless `KUBE_CONTEXT=<name>` says
otherwise, never the current one, and asks you to confirm it is Blueshell's. After you sign in it checks the Vault signs in through
`esa-blueshell.nl`, and stops before reading or writing anything if not. It opens
its own port-forward to Vault, since Vault's public host sits behind the site's sign-in, and
opens a browser on that sign-in. Its Vault token stays in the script and is revoked when it ends,
as is the port-forward.

Each line it prints is one thing checked or done. The last check must say Vault has all three
fields, Secret Manager `1 enabled version` and `backup-writer 1 key(s)`. If it names a second
writer key, delete that one in the console.

The report tokens in `secret/platform/backup-report` are seeded on their own, by `bootstrap-auth.sh`.

**Replacing the writer key** by hand, say after a suspected leak: run
`scripts/seed-backup-credentials.sh --new-writer-key`. It makes a new key, puts it in Vault and
deletes the old one. The rotation job (#2099) will do this every 30 days on its own.

## The first night

The `data-system` Job's first run creates the repository; the other two refuse to, so they wait
for it. To run them now rather than at night, with `blueshell` being your kubeconfig's name for
the Blueshell cluster (`kubectl config get-contexts`):

```bash
K="kubectl --context blueshell"
$K -n data-system create job --from=cronjob/backup backup-first
$K -n data-system logs -f job/backup-first --all-containers
# once that has finished:
$K -n mail-system create job --from=cronjob/backup backup-first
$K -n utility-system create job --from=cronjob/backup backup-first
```

The `data-system` run ends with four `Created snapshot` lines (MariaDB, Vault, Valkey, uploads),
the others with one each, and all five endpoints turn green. Then check one stored blob carries
the lock and the storage class, with an owner's key:

```bash
S3="aws --endpoint-url https://s3.nl-ams.scw.cloud --region nl-ams s3api"
KEY=$($S3 list-objects-v2 --bucket esa-blueshell-backups --prefix p --max-items 1 \
  --query 'Contents[0].Key' --output text)
$S3 head-object --bucket esa-blueshell-backups --key "$KEY" \
  --query '{class:StorageClass,mode:ObjectLockMode,until:ObjectLockRetainUntilDate}'
```

It must say `ONEZONE_IA`, `COMPLIANCE` and a date 30 days out, and no `Expiration`.

Delete the manual Jobs afterwards with `$K -n <namespace> delete job backup-first`.

## When it alerts

Read the failed Job's logs: `kubectl --context blueshell -n <namespace> logs job/<job>
--all-containers`. The alert carries the line that failed.

- **"secret/platform/backup has no …"**: the credentials are not seeded, or a field is missing.
- **"Vault refused the backup role's login"** (or `backup-mail`, `backup-gatus`): Vault is sealed, or
  `bootstrap-auth.sh` has not run since the role was added.
- **"Kopia could not connect: … invalid repository password"**: the password in Vault does not
  match the repository's. Never "fix" it by creating a new repository: find the right password
  in Secret Manager.
- **"Kopia could not connect: … Access Denied"**: the writer key expired or was rotated without
  updating Vault.
- **"The repository does not exist yet"**: the Stalwart or Gatus Job ran before the
  `data-system` Job's first night.
- **"Valkey did not hand over a save"**, **"SQLite could not back up Gatus's database"**: that
  store only; the rest of its Job carried on.
- **"Stalwart's pod did not stop within two minutes"**, **"Stalwart could not be scaled
  down"**: no snapshot was taken and Stalwart was started again. Check
  `kubectl --context blueshell -n mail-system get deploy stalwart`: it must show `1/1`.
- **"Stalwart could not be started again"**: mail is down. Run
  `kubectl --context blueshell -n mail-system scale deploy/stalwart --replicas=1` at once.
- **No report at all for 26 hours**: the CronJob did not start. Check
  `kubectl --context blueshell -n <namespace> get cronjob backup` and the node.

## Restoring

Every restore starts by connecting Kopia read-only and picking a snapshot, as steps 2 to 4 of
`platform/recovery/README.md` show. The writer key from Vault (`vault kv get
secret/platform/backup`) can read everything it wrote; without Vault, a Scaleway owner makes a
temporary read key and reads the password from Secret Manager. Rehearse into scratch volumes and
read the data back before restoring over anything live.

### MariaDB and Vault

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

### Stalwart

The snapshot is Stalwart's whole volume, with its RocksDB store in `data/`. To restore it over the
live one:

1. `kubectl --context blueshell -n mail-system scale deploy/stalwart --replicas=0`, and wait for
   the pod to go.
2. Restore the snapshot into the `stalwart-data` volume from a pod that mounts it, replacing
   what is there, owned by uid 2000.
3. Scale back to 1. The apply sidecar reconciles the settings, accounts and DNS from
   `infra/stalwart` as on any start.

To rehearse, restore into a scratch directory and start `stalwartlabs/stalwart` on it with the
same `config.json` (`apps/mail/stalwart/config-json-configmap.yaml`), then sign in to a mailbox.

### The api's uploads

The snapshot is `/srv/blueshell/storage` itself. Restore it into a scratch directory, then copy
it back as "User uploads" in `platform/docs/runbook.md` describes. File names round-trip
unchanged.

### Valkey

The snapshot is a save, `dump.rdb`. Scale Valkey to 0, put the file in the `valkey-data` volume as
`/data/dump.rdb`, and scale it back to 1: Valkey loads it on start. To rehearse, start
`valkey/valkey` with `--dir` pointing at the restored file and read a key back.

### Gatus

The snapshot is a self-contained copy of `data.db`. Scale Gatus to 0, put the file in the
`gatus-data` volume as `/data/data.db`, and scale it back to 1. To rehearse, open the restored
file with `sqlite3` and count the results.
