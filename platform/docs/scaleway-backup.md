# Scaleway backup organization

The association's backups go to Scaleway Object Storage in `nl-ams`, in an organization that
holds nothing else. `platform/scaleway/backup` declares everything inside it. Why it is shaped
this way is in [architecture ADR-011](../../docs/adr/architecture/ADR-011-backups-go-off-site-to-scaleway-under-a-compliance-object-lock.md).

## Who holds what

The two owners are role accounts, not people. Each is handed over with its role, so the
organization never depends on one person staying on.

| Account | Scaleway rights | Its login, 2FA and recovery codes |
|---------|-----------------|-----------------------------------|
| **SiteCie** | The organization's Owner | Held by the chair of the SiteCie |
| **Board** | IAM Member in the Administrators group, with `OrganizationManager` and `AllProductsFullAccess` | The board's password manager, passed on from board to board |

Billing mail and the billing alerts (€2 a month, at 50% and 100%) go to board@ and
sitecie@blueshell.utwente.nl and to a Discord webhook. Neither mailbox runs on the Blueshell
server, so they still arrive when it is down.

Neither account's materials are ever stored on the server. The board account is the way in if
the SiteCie account is unavailable.

A suspended account takes the backups with it, so the organization is paid from the
association's bank account, its billing contact is a role mailbox, and a billing alert emails the
billing contact. Scaleway has no hard spending cap; the alert is the only guard.

The Kopia password lives in Vault for the nightly job and in Secret Manager for break-glass,
nowhere else. Secret Manager bills each stored version, so it holds one at a time.

## What the code declares

- **`esa-blueshell-tofu-state`**: OpenTofu's own state, versioned, encrypted at rest (SSE-ONE).
  Only the owners may touch it.
- **`esa-blueshell-backups`**: versioned, SSE-ONE, and Object Lock in COMPLIANCE mode with a
  default retention of 30 days. Its `.storageconfig` makes Kopia store every blob in Standard One
  Zone. A lifecycle rule erases a version 30 days after it stops being current, once its lock
  has run out.
- **`backup-writer`**: an application that may read, write and delete objects in the backup
  bucket only. Deleting adds a delete marker, and the bucket policy withholds
  `s3:DeleteObjectVersion`, so no key of its can erase a version.
- **`backup-rotator`**: an application that may manage applications and their API keys.
- **`kopia-repository-password`**: an empty, protected Secret Manager entry.

The code never creates an API key, so its state holds no secret.

## Your API key

OpenTofu runs with an owner's **own user key**, never an application's. The rotator can mint a
key for any application in the organization, so an application holding OpenTofu's rights would
hand them to anyone holding the rotator key. If an `opentofu` application exists, delete it.

An owner who applies makes their own key: IAM & API keys, API keys, Generate API key.

- Bearer: **Myself (IAM user)**.
- Expiry: about a year.
- Object Storage: **Yes**, preferred project `Website`.

Keep it in your password manager, never on the server.

## Applying

From `platform/scaleway/backup`, with OpenTofu 1.10 or newer:

```bash
export SCW_ACCESS_KEY=... SCW_SECRET_KEY=...
export SCW_DEFAULT_ORGANIZATION_ID=... SCW_DEFAULT_PROJECT_ID=...
# The state backend speaks S3 and reads the same key under AWS names.
export AWS_ACCESS_KEY_ID=$SCW_ACCESS_KEY AWS_SECRET_ACCESS_KEY=$SCW_SECRET_KEY

tofu init
tofu plan
tofu apply
```

`terraform.tfvars` names the two owners by IAM user ID: IAM & API keys, Users, the user, and
the ID on its overview. The bucket policies allow nobody else besides the writer.

The state is not locked. Only one owner applies at a time.

### The first apply

The state bucket is declared here too, so the very first apply runs on local state and then moves
it into the bucket:

```bash
printf 'terraform {\n  backend "local" {}\n}\n' > backend_override.tf
tofu init
tofu apply -target=scaleway_object_bucket_policy.state \
  -target=scaleway_object_bucket_server_side_encryption_configuration.state
rm backend_override.tf
tofu init -migrate-state
tofu apply
rm terraform.tfstate terraform.tfstate.backup
```

### After the first apply

1. One owner generates the Kopia password and adds it as a version of `kopia-repository-password`
   in Secret Manager, and #2085 puts it in Vault.
2. An owner generates the first `backup-writer` and `backup-rotator` keys in the console (bearer:
   the application) and puts them in Vault. From then on, the rotation job replaces the writer
   key (#2099).
3. Prove the lock with a writer key:

   ```bash
   export AWS_ACCESS_KEY_ID=<writer access key> AWS_SECRET_ACCESS_KEY=<writer secret key>
   S3="aws --endpoint-url https://s3.nl-ams.scw.cloud --region nl-ams s3api"
   echo probe > probe.txt
   $S3 put-object --bucket esa-blueshell-backups --key probe.txt --body probe.txt
   $S3 delete-object --bucket esa-blueshell-backups --key probe.txt
   $S3 list-object-versions --bucket esa-blueshell-backups --prefix probe.txt
   $S3 get-object --bucket esa-blueshell-backups --key probe.txt --version-id <version> out.txt
   $S3 delete-object --bucket esa-blueshell-backups --key probe.txt --version-id <version>  # refused
   $S3 list-objects-v2 --bucket esa-blueshell-tofu-state                                  # refused
   ```

   Repeat the `--version-id` delete with your own key. The lock refuses it too.

   The probe stays for 30 days and costs nothing worth counting.

## Changing the Kopia password

Change it with `kopia repository change-password`, then update Vault, add the new password as a
version of `kopia-repository-password` and delete the old version. OpenTofu manages the entry,
never its versions, so a plan shows no change.

## Restoring

[`platform/recovery`](../recovery/README.md) takes the member data out of the backup onto a laptop,
without the server.

## Things that cannot be undone

- Object Lock on the backup bucket can never be turned off, and versioning can never be suspended.
- A locked version cannot be deleted or shortened by anyone, owners included, until its lock runs
  out. `tofu destroy` cannot remove the backup bucket while it holds one.
- Raising `lock_days` keeps erased personal data in backups for longer, and the privacy policy
  states how long. Change both together.
