package net.blueshell.api.domain.contribution.application.command

import net.blueshell.api.domain.contribution.application.ContributionPeriodService
import net.blueshell.api.domain.contribution.application.ContributionReminderService
import net.blueshell.api.domain.contribution.command.ContributionReminderItem
import net.blueshell.api.domain.contribution.command.FindContributionRemindersCommand
import net.blueshell.api.domain.contribution.command.SendContributionReminderBatchCommand
import net.blueshell.api.domain.contribution.command.SendContributionReminderCommand
import net.blueshell.api.domain.contribution.command.result.ContributionReminderResult
import net.blueshell.api.domain.contribution.command.result.toContributionReminderResults
import net.blueshell.api.domain.contribution.command.result.toResult
import net.blueshell.api.domain.contribution.persistence.ContributionReminder
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.shared.command.CommandHandler
import org.springframework.stereotype.Component

@Component
class SendContributionReminderHandler(
    private val service: ContributionReminderService,
    private val users: UserService,
    private val contributionPeriods: ContributionPeriodService
) : CommandHandler<SendContributionReminderCommand, ContributionReminderResult> {
    override val commandType = SendContributionReminderCommand::class

    override fun handle(command: SendContributionReminderCommand): ContributionReminderResult {
        var reminder = ContributionReminder(
            user = users.findById(command.userId!!),
            contributionPeriod = contributionPeriods.findById(command.contributionPeriodId!!),
        )
        reminder = service.create(reminder)
        service.sendReminder(reminder)
        return reminder.toResult()
    }
}

@Component
class SendContributionReminderBatchHandler(
    private val service: ContributionReminderService,
    private val users: UserService,
    private val contributionPeriods: ContributionPeriodService
) : CommandHandler<SendContributionReminderBatchCommand, List<ContributionReminderResult>> {
    override val commandType = SendContributionReminderBatchCommand::class

    override fun handle(command: SendContributionReminderBatchCommand): List<ContributionReminderResult> {
        var reminders = command.items!!.map { item ->
            buildReminder(item, users, contributionPeriods)
        }.toMutableList()
        reminders = service.createAll(reminders)
        service.sendReminders(reminders)
        return reminders.toContributionReminderResults()
    }
}

@Component
class FindContributionRemindersHandler(
    private val service: ContributionReminderService
) : CommandHandler<FindContributionRemindersCommand, List<ContributionReminderResult>> {
    override val commandType = FindContributionRemindersCommand::class

    override fun handle(command: FindContributionRemindersCommand): List<ContributionReminderResult> {
        return service.findByContributionPeriodId(command.contributionPeriodId!!).toContributionReminderResults()
    }
}

private fun buildReminder(
    item: ContributionReminderItem,
    users: UserService,
    contributionPeriods: ContributionPeriodService
): ContributionReminder {
    return ContributionReminder(
        user = users.findById(item.userId!!),
        contributionPeriod = contributionPeriods.findById(item.contributionPeriodId!!),
    )
}
