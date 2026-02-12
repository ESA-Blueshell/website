package net.blueshell.api.domain.contribution.command

import net.blueshell.api.domain.contribution.persistence.ContributionReminder
import net.blueshell.api.shared.command.Command

data class SendContributionReminderCommand(
    val userId: Long,
    val contributionPeriodId: Long
) : Command<ContributionReminder>

data class SendContributionReminderBatchCommand(
    val items: MutableList<ContributionReminderItem>
) : Command<MutableList<ContributionReminder>>

data class ContributionReminderItem(
    val userId: Long,
    val contributionPeriodId: Long
)

data class FindContributionRemindersCommand(
    val contributionPeriodId: Long
) : Command<MutableList<ContributionReminder>>
