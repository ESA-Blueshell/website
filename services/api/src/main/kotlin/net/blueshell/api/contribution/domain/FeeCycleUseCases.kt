package net.blueshell.api.contribution.domain

import net.blueshell.api.contribution.api.ContributionPeriodService
import net.blueshell.api.contribution.persistence.ContributionReminder
import net.blueshell.api.contribution.persistence.IncassoNotification
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import net.blueshell.api.shared.dto.bulk.BulkSelectionRejected
import net.blueshell.api.shared.dto.bulk.FeeCycleGroup
import net.blueshell.api.user.api.UserService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Asking a period's unpaid members for what they owe, in one operation.
 *
 * Both statements go out from one confirmation: the direct-debit group is told what will be
 * debited and when, the rest are asked to transfer by a date. Which group a member is in is
 * the `incasso` flag on the membership, not a choice made here.
 *
 * The plan is read once and used for the whole send, so what went out matches what was
 * previewed, and one timestamp covers the whole cycle so it reads back as one act rather
 * than a hundred moments. A fee type submitted for a member the cycle will not write to is refused
 * rather than ignored: it means the operator was looking at a table that has since moved.
 */
@Service
class FeeCycleUseCases(
    private val planner: FeeCyclePlanner,
    private val periods: ContributionPeriodService,
    private val users: UserService,
    private val reminders: ContributionReminderService,
    private val preNotifications: IncassoNotificationService,
) {
    @Transactional(readOnly = true)
    fun preview(contributionPeriodId: Long): FeeCyclePlan = planner.plan(contributionPeriodId)

    @Transactional
    fun send(
        contributionPeriodId: Long,
        dates: FeeCycleDates,
        feeTypeOverrides: Map<Long, BulkFeeType>,
    ): FeeCycleResult {
        val plan = planner.plan(contributionPeriodId)
        rejectOverridesForNonRecipients(plan, feeTypeOverrides)

        val period = periods.findById(contributionPeriodId)
        var paymentRequests = 0
        var preNotified = 0

        val askedAt = Instant.now()

        for (participant in plan.recipients) {
            // A recipient always has a fee type: the only member without one is honorary,
            // and honorary members are excluded before they get here.
            val feeType = feeTypeOverrides[participant.userId] ?: participant.feeType!!
            val amount = resolveFeeAmount(feeType, period)
            val member = users.findById(participant.userId)

            when (participant.group) {
                FeeCycleGroup.TRANSFER -> {
                    val id = ContributionReminder.Id(participant.userId, contributionPeriodId)
                    // One row per member and period, so asking again restates the record
                    // rather than adding a second one the treasurer would have to reconcile.
                    // Every field of the statement is restated, `askedAt` included, so the
                    // row always describes the most recent ask rather than a mixture.
                    val reminder = if (reminders.existsById(id)) {
                        reminders.update(
                            reminders.findById(id).apply {
                                this.feeType = feeType
                                this.amount = amount
                                this.paymentDueDate = dates.paymentDue
                                this.askedAt = askedAt
                            },
                        )
                    } else {
                        reminders.create(
                            ContributionReminder(
                                user = member,
                                contributionPeriod = period,
                                feeType = feeType,
                                amount = amount,
                                paymentDueDate = dates.paymentDue,
                                askedAt = askedAt,
                            ),
                        )
                    }
                    reminders.sendReminder(reminder)
                    paymentRequests++
                }

                FeeCycleGroup.DIRECT_DEBIT -> {
                    val id = IncassoNotification.Id(participant.userId, contributionPeriodId)
                    val notification = if (preNotifications.existsById(id)) {
                        preNotifications.update(
                            preNotifications.findById(id).apply {
                                this.feeType = feeType
                                this.amount = amount
                                this.debitDate = dates.debit
                                this.askedAt = askedAt
                            },
                        )
                    } else {
                        preNotifications.create(
                            IncassoNotification(
                                user = member,
                                contributionPeriod = period,
                                feeType = feeType,
                                amount = amount,
                                debitDate = dates.debit,
                                askedAt = askedAt,
                            ),
                        )
                    }
                    preNotifications.sendNotification(notification)
                    preNotified++
                }
            }
        }

        return FeeCycleResult(
            paymentRequestsQueued = paymentRequests,
            preNotificationsQueued = preNotified,
            excluded = plan.participants.count { !it.willSend },
        )
    }

    /**
     * A fee type for somebody the cycle will not write to is a stale table, not a no-op.
     *
     * Applying the rest and dropping that one leaves the operator believing they changed a
     * member's fee when they did not, so the whole send is refused with the ids at fault.
     */
    private fun rejectOverridesForNonRecipients(plan: FeeCyclePlan, feeTypeOverrides: Map<Long, BulkFeeType>) {
        val recipients = plan.recipients.map { it.userId }.toSet()
        val stray = feeTypeOverrides.keys.filterNot { it in recipients }.sorted()
        if (stray.isEmpty()) return
        throw BulkSelectionRejected(
            "SendFeeCycleRequest",
            listOf(
                BulkSelectionRejected.Violation(
                    field = "feeTypeOverrides",
                    code = BulkSelectionRejected.NON_RECIPIENT_FEE_TYPES,
                    values = stray,
                    message = "${stray.size} of the fee types name members this cycle does not write to.",
                ),
            ),
        )
    }
}

/**
 * What a send did, per side of the partition.
 *
 * Reported separately because they are different statements: a treasurer checking a cycle
 * went out needs to know both halves happened, and a total of the two hides one being zero.
 */
data class FeeCycleResult(
    val paymentRequestsQueued: Int,
    val preNotificationsQueued: Int,
    /** Members in the cycle who were not written to, with their reasons visible in the preview. */
    val excluded: Int,
)
