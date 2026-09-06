package net.blueshell.api.contribution.domain

import net.blueshell.api.contribution.persistence.ContributionReminder
import net.blueshell.api.user.api.UserService
import org.springframework.stereotype.Service
import net.blueshell.api.contribution.api.ContributionPeriodService

/**
 * Asking one member, from a row rather than in bulk.
 *
 * A reminder is persisted before it is sent, so a send failure leaves a record. Each call
 * writes its own ask: calling twice records two, which is what a treasurer chasing a member
 * has actually done.
 */
@Service
class ContributionReminderUseCases(
    private val service: ContributionReminderService,
    private val users: UserService,
    private val contributionPeriods: ContributionPeriodService,
) {
    fun send(userId: Long, contributionPeriodId: Long): ContributionReminder =
        service.record(build(userId, contributionPeriodId))

    fun sendBatch(items: List<Pair<Long, Long>>): List<ContributionReminder> =
        service.recordAll(items.map { (userId, periodId) -> build(userId, periodId) })

    private fun build(userId: Long, contributionPeriodId: Long) =
        ContributionReminder(
            user = users.findById(userId),
            contributionPeriod = contributionPeriods.findById(contributionPeriodId),
        )
}
