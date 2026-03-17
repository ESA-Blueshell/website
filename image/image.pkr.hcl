packer {
  required_plugins {
    qemu = {
      version = "~> 1"
      source  = "github.com/hashicorp/qemu"
    }
  }
}

variable "debian_image_url" {
  default = "https://cloud.debian.org/images/cloud/bookworm/latest/debian-12-generic-amd64.qcow2"
}

variable "debian_image_checksum" {
  default = "file:https://cloud.debian.org/images/cloud/bookworm/latest/SHA512SUMS"
}

variable "accelerator" {
  description = "QEMU accelerator: 'kvm' on Linux x86_64, 'hvf' on macOS ARM with ARM images, 'tcg' for cross-arch software emulation"
  default     = "tcg"
}

source "qemu" "website" {
  iso_url      = var.debian_image_url
  iso_checksum = var.debian_image_checksum
  disk_image   = true

  output_directory = "output"
  format           = "qcow2"
  disk_size        = "20G"

  # tcg = software emulation (works cross-arch, slower)
  # kvm = Linux hardware accel (use on x86_64 CI runners)
  accelerator    = var.accelerator
  qemu_binary    = "qemu-system-x86_64"
  machine_type   = "q35"
  cpus           = 2
  memory         = 2048

  ssh_username     = "root"
  ssh_password     = "packer"
  ssh_timeout      = "20m"
  shutdown_command = "shutdown -P now"

  # Seed cloud-init just enough to get SSH access for provisioning
  cd_files = ["./seed/meta-data", "./seed/user-data"]
  cd_label = "cidata"
}

build {
  sources = ["source.qemu.website"]

  provisioner "shell" {
    script = "./scripts/provision.sh"
  }

  # Clean cloud-init state so it re-runs on first real boot at Contabo
  provisioner "shell" {
    inline = [
      "cloud-init clean --logs",
      "truncate -s 0 /etc/machine-id",
      "rm -f /var/lib/dbus/machine-id",
      "rm -f /root/.ssh/authorized_keys",
      "sync"
    ]
  }
}