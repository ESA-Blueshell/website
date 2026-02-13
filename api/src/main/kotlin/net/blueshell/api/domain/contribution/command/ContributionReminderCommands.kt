package net.blueshell.api.domain.contribution.command

import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import net.blueshell.api.domain.contribution.persistence.ContributionReminder
import net.blueshell.api.shared.command.Command

data class SendContributionReminderCommand(
    @field:NotNull(message = "User ID is required")
    @field:Positive(message = "User ID must be positive")
    val userId: Long?,
    @field:NotNull(message = "Contribution period ID is required")
    @field:Positive(message = "Contribution period ID must be positive")
    val contributionPeriodId: Long?
) : Command<ContributionReminder>

data class SendContributionReminderBatchCommand(
    @field:NotEmpty(message = "Reminder items list cannot be empty")
    @field:Valid
    val items: MutableList<ContributionReminderItem>?
) : Command<MutableList<ContributionReminder>>

data class ContributionReminderItem(
    @field:NotNull(message = "User ID is required")
    @field:Positive(message = "User ID must be positive")
    val userId: Long?,
    @field:NotNull(message = "Contribution period ID is required")
    @field:Positive(message = "Contribution period ID must be positive")
    val contributionPeriodId: Long?
)

data class FindContributionRemindersCommand(
    @field:NotNull(message = "Contribution period ID is required")
    @field:Positive(message = "Contribution period ID must be positive")
    val contributionPeriodId: Long?
) : Command<MutableList<ContributionReminder>>
