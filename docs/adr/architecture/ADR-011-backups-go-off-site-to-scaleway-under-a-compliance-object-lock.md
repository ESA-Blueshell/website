# ADR-011: Backups Go Off-Site to Scaleway Under a Compliance Object Lock

## Status
Accepted

## Context

Every data volume sits on the one Contabo node, and nothing is backed up: a deleted claim takes
its data with it. The backups have to survive losing the server and its Vault together, and a
compromised server must not be able to delete them. The data is small, about 0.9 GB, and
includes personal data that the privacy policy promises to erase.

Alternatives considered:

- **restic.** It encrypts and deduplicates like Kopia, but has no native Object Lock support, so
  a stolen key could prune the repository.
- **A copy on the node.** It dies with the node, which is the case that matters.
- **Standard Multi-AZ.** It costs more and guards against losing a Scaleway zone, which a second
  copy of data that also lives on the server does not need.
- **Scaleway Audit Trail alerts** on IAM and key changes. They cost more than this setup is worth,
  and Audit Trail does not cover Object Storage, so it would miss changes to the lock.

## Decision

**Kopia backs up nightly to Scaleway Object Storage in `nl-ams`, in an organization that holds
nothing but the backups, under a COMPLIANCE Object Lock.**

- **Kopia** encrypts, deduplicates and compresses on the client, so Scaleway never sees
  plaintext. It writes with COMPLIANCE retention and extends it during full maintenance.
- **Standard One Zone in `nl-ams`**: an EU provider, data kept in the Netherlands, a different
  provider and country from the host. The class is set by the bucket's `.storageconfig`, which
  Kopia reads, because S3 has no bucket-wide default class. SSE-ONE encrypts at rest as well.
- **A dedicated organization.** Scaleway IAM rights reach a whole organization, so a key here
  reaches nothing but backups. Two owners, each a role account with its own login and 2FA: the
  SiteCie's, which is the Owner, and the board's. Each is handed over with its role.
- **A 30-day lock**, with 7 daily and 4 weekly snapshots. Nothing can erase a locked version
  before its lock runs out, owners included. The writer key has no `s3:DeleteObjectVersion`, so it
  can only add data or delete markers. A lifecycle rule erases a version 30 days after it stops
  being current. Erased personal data can therefore stay in backups for up to about two months,
  and the privacy policy says so.
- **No application but the rotator holds more than the writer.** The rotator can mint a key for any application
  in the organization, so OpenTofu runs with an owner's own user key, never an application's.
- **The Scaleway side is OpenTofu** in `platform/scaleway/backup`. It never creates an API key,
  so its state holds no secret.
- **Routine credentials live in Vault**: the writer key, the rotator key and the Kopia password.
  The writer key rotates every 30 days.
- **Break-glass:** the Scaleway console login opens Secret Manager, which holds the Kopia
  password. No owner keeps a copy of their own, so the second owner holds nothing but a login.
  Vault's unseal shares are held by people, never in Scaleway, so the console alone yields only
  ciphertext: it cannot open values sealed by Vault Transit.
- **Monitoring is on the server.** A failed run, or no success in 26 hours, alerts the board. A
  weekly verify reads back 5% of the files and checks the bucket's lock and versioning, the only
  check on a lock weakened for future uploads.

## Consequences

- A restore needs a Scaleway owner login or the Kopia password plus a key, and, for sealed
  values, three unseal shares. Losing the server costs neither.
- A full restore downloads in about a minute, well inside the free egress.
- Raising the lock raises how long erased data survives. The privacy policy changes with it.
- A rekey of Vault's unseal shares must keep the old set for as long as backups from before it
  are kept.
- Nobody notices a change to IAM in the organization until it breaks a backup or a verify.

## Implementation status

The organization and its owners exist (#2096), and `platform/scaleway/backup` declares the
buckets, applications and secret (#2098). Nothing backs up yet: the nightly job is #2085 and
#2093, rotation and the weekly verify are #2099, and the unseal shares are #2097.

## Related Documentation
- [Applying the Scaleway side](../../../platform/docs/scaleway-backup.md)
- [Epic #2095](https://github.com/ESA-Blueshell/website/issues/2095)
