terraform {
  required_version = ">= 1.10"

  required_providers {
    scaleway = {
      source  = "scaleway/scaleway"
      version = "~> 2.84"
    }
  }

  # The state bucket is declared in this configuration, so the first apply runs on
  # local state; platform/docs/scaleway-backup.md has the bootstrap.
  backend "s3" {
    bucket = "esa-blueshell-tofu-state"
    key    = "scaleway/backup.tfstate"
    region = "nl-ams"
    endpoints = {
      s3 = "https://s3.nl-ams.scw.cloud"
    }

    skip_credentials_validation = true
    skip_region_validation      = true
    skip_requesting_account_id  = true
    skip_metadata_api_check     = true
    skip_s3_checksum            = true
  }
}

provider "scaleway" {
  region = "nl-ams"
}
