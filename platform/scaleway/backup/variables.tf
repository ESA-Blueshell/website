variable "owner_user_ids" {
  description = "IAM user IDs of the organization's two owners. The bucket policies allow them and nobody else besides the backup writer."
  type        = list(string)

  validation {
    condition     = length(var.owner_user_ids) == 2
    error_message = "The backup organization has exactly two owners."
  }
}

variable "lock_days" {
  description = "Days every backup object stays under the COMPLIANCE lock. Kopia's --retention-period must match."
  type        = number
  default     = 30

  # Every object written under a longer lock is billed for that long, and no one can shorten it.
  validation {
    condition     = var.lock_days >= 7 && var.lock_days <= 90
    error_message = "lock_days is between 7 and 90. A COMPLIANCE lock cannot be shortened once written."
  }
}
