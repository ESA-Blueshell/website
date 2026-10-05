resource "scaleway_iam_application" "backup_writer" {
  name        = "backup-writer"
  description = "The nightly backup job. Its key lives in Vault and rotates every 30 days."
}

# Project-wide object rights, narrowed to the backup bucket by its bucket policy.
resource "scaleway_iam_policy" "backup_writer" {
  name           = "backup-writer"
  application_id = scaleway_iam_application.backup_writer.id

  rule {
    project_ids = [scaleway_object_bucket.backup.project_id]
    permission_set_names = [
      "ObjectStorageBucketsRead",
      "ObjectStorageObjectsRead",
      "ObjectStorageObjectsWrite",
      "ObjectStorageObjectsDelete",
    ]
  }
}

# It can mint a key for any application here, so no other application may hold more
# than the writer does. The OpenTofu key is therefore an owner's own user key.
resource "scaleway_iam_application" "backup_rotator" {
  name        = "backup-rotator"
  description = "Rotates the backup-writer key. Its key lives in Vault."
}

resource "scaleway_iam_policy" "backup_rotator" {
  name           = "backup-rotator"
  application_id = scaleway_iam_application.backup_rotator.id

  rule {
    organization_id      = scaleway_iam_application.backup_rotator.organization_id
    permission_set_names = ["IAMApplicationManager"]
  }
}
