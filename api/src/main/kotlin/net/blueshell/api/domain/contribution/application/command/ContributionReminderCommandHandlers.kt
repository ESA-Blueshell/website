package net.blueshell.api.domain.contribution.application.command

import net.blueshell.api.domain.contribution.application.ContributionPeriodService
import net.blueshell.api.domain.contribution.application.ContributionReminderService
import net.blueshell.api.domain.contribution.command.ContributionReminderItem
import net.blueshell.api.domain.contribution.command.FindContributionRemindersCommand
import net.blueshell.api.domain.contribution.command.SendContributionReminderBatchCommand
import net.blueshell.api.domain.contribution.command.SendContributionReminderCommand
import net.blueshell.api.domain.contribution.persistence.ContributionReminder
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.shared.command.CommandHandler
import org.springframework.stereotype.Component

@Component
class SendContributionReminderHandler(
    private val service: ContributionReminderService,
    private val users: UserService,
    private val contributionPeriods: ContributionPeriodService
) : CommandHandler<SendContributionReminderCommand, ContributionReminder> {
    override val commandType = SendContributionReminderCommand::class

    override fun handle(command: SendContributionReminderCommand): ContributionReminder {
        var reminder = ContributionReminder()
        reminder.user = users.findById(command.userId)
        reminder.contributionPeriod = contributionPeriods.findById(command.contributionPeriodId)
        reminder = service.create(reminder)
        service.sendReminder(reminder)
        return reminder
    }
}

@Component
class SendContributionReminderBatchHandler(
    private val service: ContributionReminderService,
    private val users: UserService,
    private val contributionPeriods: ContributionPeriodService
) : CommandHandler<SendContributionReminderBatchCommand, MutableList<ContributionReminder>> {
    override val commandType = SendContributionReminderBatchCommand::class

    override fun handle(command: SendContributionReminderBatchCommand): MutableList<ContributionReminder> {
        var reminders = command.items.map { item ->
            buildReminder(item, users, contributionPeriods)
        }.toMutableList()
        reminders = service.createAll(reminders)
        service.sendReminders(reminders)
        return reminders
    }
}

@Component
class FindContributionRemindersHandler(
    private val service: ContributionReminderService
) : CommandHandler<FindContributionRemindersCommand, MutableList<ContributionReminder>> {
    override val commandType = FindContributionRemindersCommand::class

    override fun handle(command: FindContributionRemindersCommand): MutableList<ContributionReminder> {
        return service.findByContributionPeriodId(command.contributionPeriodId)
    }
}

private fun buildReminder(
    item: ContributionReminderItem,
    users: UserService,
    contributionPeriods: ContributionPeriodService
): ContributionReminder {
    val reminder = ContributionReminder()
    reminder.user = users.findById(item.userId)
    reminder.contributionPeriod = contributionPeriods.findById(item.contributionPeriodId)
    return reminder
}
