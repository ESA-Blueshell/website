output "backup_bucket" {
  value = scaleway_object_bucket.backup.name
}

output "backup_endpoint" {
  value = "https://s3.nl-ams.scw.cloud"
}

output "backup_writer_application_id" {
  value = scaleway_iam_application.backup_writer.id
}

output "backup_rotator_application_id" {
  value = scaleway_iam_application.backup_rotator.id
}
