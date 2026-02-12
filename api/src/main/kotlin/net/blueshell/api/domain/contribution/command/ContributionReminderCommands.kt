package net.blueshell.api.domain.contribution.command

import net.blueshell.api.domain.contribution.persistence.ContributionReminder
import net.blueshell.api.domain.contribution.web.dto.ContributionReminderDTO
import net.blueshell.api.shared.command.Command

data class SendContributionReminderCommand(
    val dto: ContributionReminderDTO
) : Command<ContributionReminder>

data class SendContributionReminderBatchCommand(
    val dtos: MutableList<ContributionReminderDTO>
) : Command<MutableList<ContributionReminder>>

data class FindContributionRemindersCommand(
    val contributionPeriodId: Long
) : Command<MutableList<ContributionReminder>>
