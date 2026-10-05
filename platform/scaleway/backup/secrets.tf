# An owner adds the password as a version in the console. Code never holds it.
resource "scaleway_secret" "kopia_repository_password" {
  name        = "kopia-repository-password"
  description = "Opens the Kopia repository in esa-blueshell-backups. Break-glass copy; the job reads it from Vault."
  protected   = true
}
