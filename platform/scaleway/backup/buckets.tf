resource "scaleway_object_bucket" "state" {
  name = "esa-blueshell-tofu-state"

  versioning {
    enabled = true
  }

  lifecycle_rule {
    id                                     = "old-state-versions"
    enabled                                = true
    abort_incomplete_multipart_upload_days = 1

    noncurrent_version_expiration {
      noncurrent_days = 90
    }
  }
}

resource "scaleway_object_bucket_server_side_encryption_configuration" "state" {
  bucket = scaleway_object_bucket.state.name

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# A bucket policy allows only the principals it names, on top of their IAM rights,
# so the backup writer cannot reach this bucket.
resource "scaleway_object_bucket_policy" "state" {
  bucket = scaleway_object_bucket.state.name
  policy = jsonencode({
    Version = "2023-04-17"
    Statement = [
      {
        Sid       = "Owners"
        Effect    = "Allow"
        Principal = { SCW = [for id in var.owner_user_ids : "user_id:${id}"] }
        Action    = ["s3:*"]
        Resource  = [scaleway_object_bucket.state.name, "${scaleway_object_bucket.state.name}/*"]
      },
    ]
  })
}

resource "scaleway_object_bucket" "backup" {
  name                = "esa-blueshell-backups"
  object_lock_enabled = true

  versioning {
    enabled = true
  }

  # A deleted object only gains a delete marker, and its locked version cannot be
  # removed early. Once the lock has run out, this rule erases it. No rule may carry
  # an `expiration` block: Scaleway reads one without days as expiring every current
  # object at the next midnight, even with only expired_object_delete_marker set.
  lifecycle_rule {
    id                                     = "noncurrent-after-lock"
    enabled                                = true
    abort_incomplete_multipart_upload_days = 1

    noncurrent_version_expiration {
      noncurrent_days = var.lock_days
    }
  }
}

resource "scaleway_object_bucket_lock_configuration" "backup" {
  bucket = scaleway_object_bucket.backup.name

  rule {
    default_retention {
      mode = "COMPLIANCE"
      days = var.lock_days
    }
  }
}

resource "scaleway_object_bucket_server_side_encryption_configuration" "backup" {
  bucket = scaleway_object_bucket.backup.name

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "scaleway_object_bucket_policy" "backup" {
  bucket = scaleway_object_bucket.backup.name
  policy = jsonencode({
    Version = "2023-04-17"
    Statement = [
      {
        Sid       = "Owners"
        Effect    = "Allow"
        Principal = { SCW = [for id in var.owner_user_ids : "user_id:${id}"] }
        Action    = ["s3:*"]
        Resource  = [scaleway_object_bucket.backup.name, "${scaleway_object_bucket.backup.name}/*"]
      },
      {
        # No s3:DeleteObjectVersion: the writer can hide an object behind a delete
        # marker, never erase a version, even one whose lock has run out.
        Sid       = "Writer"
        Effect    = "Allow"
        Principal = { SCW = "application_id:${scaleway_iam_application.backup_writer.id}" }
        Action = [
          "s3:ListBucket",
          "s3:ListBucketVersions",
          "s3:ListBucketMultipartUploads",
          "s3:GetBucketLocation",
          "s3:GetBucketVersioning",
          "s3:GetBucketObjectLockConfiguration",
          "s3:GetObject",
          "s3:GetObjectVersion",
          "s3:GetObjectRetention",
          "s3:PutObject",
          "s3:PutObjectRetention",
          "s3:DeleteObject",
          "s3:AbortMultipartUpload",
          "s3:ListMultipartUploadParts",
        ]
        Resource = [scaleway_object_bucket.backup.name, "${scaleway_object_bucket.backup.name}/*"]
      },
    ]
  })
}

# Kopia reads this before every upload and stores its blobs in Standard One Zone.
# It must exist before the repository is created.
resource "scaleway_object" "kopia_storage_config" {
  bucket        = scaleway_object_bucket.backup.name
  key           = ".storageconfig"
  content       = jsonencode({ blobOptions = [{ storageClass = "ONEZONE_IA" }] })
  content_type  = "application/json"
  storage_class = "ONEZONE_IA"

  depends_on = [
    scaleway_object_bucket_lock_configuration.backup,
    scaleway_object_bucket_server_side_encryption_configuration.backup,
    scaleway_object_bucket_policy.backup,
  ]
}
