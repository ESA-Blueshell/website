package net.blueshell.api.domain.contribution.application.command

import net.blueshell.api.domain.contribution.application.ContributionReminderService
import net.blueshell.api.domain.contribution.command.ContributionReminderItem
import net.blueshell.api.domain.contribution.command.FindContributionRemindersCommand
import net.blueshell.api.domain.contribution.command.SendContributionReminderBatchCommand
import net.blueshell.api.domain.contribution.command.SendContributionReminderCommand
import net.blueshell.api.domain.contribution.persistence.ContributionReminder
import net.blueshell.api.shared.command.CommandHandler
import net.blueshell.api.shared.model.asRef
import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.domain.user.persistence.User
import org.springframework.stereotype.Component

@Component
class SendContributionReminderHandler(
    private val service: ContributionReminderService
) : CommandHandler<SendContributionReminderCommand, ContributionReminder> {
    override val commandType = SendContributionReminderCommand::class

    override fun handle(command: SendContributionReminderCommand): ContributionReminder {
        var reminder = ContributionReminder()
        reminder.user = User::class.asRef(command.userId)
        reminder.contributionPeriod = ContributionPeriod::class.asRef(command.contributionPeriodId)
        reminder = service.create(reminder)
        service.sendReminder(reminder)
        return reminder
    }
}

@Component
class SendContributionReminderBatchHandler(
    private val service: ContributionReminderService
) : CommandHandler<SendContributionReminderBatchCommand, MutableList<ContributionReminder>> {
    override val commandType = SendContributionReminderBatchCommand::class

    override fun handle(command: SendContributionReminderBatchCommand): MutableList<ContributionReminder> {
        var reminders = command.items.map { item -> buildReminder(item) }.toMutableList()
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

private fun buildReminder(item: ContributionReminderItem): ContributionReminder {
    val reminder = ContributionReminder()
    reminder.user = User::class.asRef(item.userId)
    reminder.contributionPeriod = ContributionPeriod::class.asRef(item.contributionPeriodId)
    return reminder
}
