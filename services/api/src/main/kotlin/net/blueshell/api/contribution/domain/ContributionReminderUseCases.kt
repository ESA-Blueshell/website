package net.blueshell.api.contribution.domain

import net.blueshell.api.contribution.persistence.ContributionReminder
import net.blueshell.api.domain.user.application.UserService
import org.springframework.stereotype.Service
import net.blueshell.api.contribution.api.ContributionPeriodService

/** A reminder is persisted before it is sent, so a send failure leaves a record. */
@Service
class ContributionReminderUseCases(
    private val service: ContributionReminderService,
    private val users: UserService,
    private val contributionPeriods: ContributionPeriodService,
) {
    fun send(userId: Long, contributionPeriodId: Long): ContributionReminderResult {
        val reminder = service.create(build(userId, contributionPeriodId))
        service.sendReminder(reminder)
        return reminder.toResult()
    }

    fun sendBatch(items: List<Pair<Long, Long>>): List<ContributionReminderResult> {
        val reminders = service.createAll(
            items.map { (userId, periodId) -> build(userId, periodId) }.toMutableList(),
        )
        service.sendReminders(reminders)
        return reminders.toContributionReminderResults()
    }

    private fun build(userId: Long, contributionPeriodId: Long) =
        ContributionReminder(
            user = users.findById(userId),
            contributionPeriod = contributionPeriods.findById(contributionPeriodId),
        )
}
