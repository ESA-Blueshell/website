package net.blueshell.api.contribution.domain

import net.blueshell.api.contribution.api.ContributionPeriodService
import net.blueshell.api.contribution.persistence.ContributionPeriod
import net.blueshell.api.contribution.persistence.ContributionReminder
import net.blueshell.api.contribution.persistence.IncassoNotification
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import net.blueshell.api.shared.dto.bulk.BulkFieldRejected
import net.blueshell.api.shared.dto.bulk.BulkSelectionRejected
import net.blueshell.api.user.api.UserService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate

/**
 * Sending a period's payment emails to a selection. One confirmation, two statements: the
 * direct-debit members are told what will be taken, the rest are asked to transfer.
 *
 * Each send writes its own records, so chasing a member twice leaves two asks.
 */
@Service
class BulkContributionEmailUseCases(
    private val planner: ContributionEmailPlanner,
    private val periods: ContributionPeriodService,
    private val users: UserService,
    private val reminders: ContributionReminderService,
    private val preNotifications: IncassoNotificationService,
) {
    @Transactional(readOnly = true)
    fun preview(contributionPeriodId: Long, userIds: Collection<Long>): ContributionEmailPlan =
        planner.plan(contributionPeriodId, userIds)

    @Transactional
    fun send(
        contributionPeriodId: Long,
        userIds: Collection<Long>,
        forciblyIncluded: Set<Long>,
        kindOverrides: Map<Long, ContributionEmailKind>,
        paymentDueDate: LocalDate?,
        debitDate: LocalDate?,
        feeTypeOverrides: Map<Long, BulkFeeType>,
    ): ContributionEmailResult {
        val plan = planner.plan(contributionPeriodId, userIds)
        rejectIncoherentSelection(userIds, forciblyIncluded, plan)

        val recipients = plan.recipients(forciblyIncluded)
        rejectStatementsForNonRecipients(recipients, forciblyIncluded, feeTypeOverrides, kindOverrides)

        val kindOf = { row: ContributionEmailRow -> kindOverrides[row.userId] ?: row.defaultKind }
        val period = periods.findById(contributionPeriodId)
        rejectDates(period, recipients.map(kindOf), paymentDueDate, debitDate)

        val sentAt = Instant.now()
        var remindersSent = 0
        var notificationsSent = 0

        for (row in recipients) {
            // Only an honorary member has no fee type, and honorary is a hard exclusion.
            val feeType = feeTypeOverrides[row.userId] ?: row.feeType!!
            val amount = resolveFeeAmount(feeType, period)
            val member = users.findById(row.userId)

            when (kindOf(row)) {
                ContributionEmailKind.REMINDER -> {
                    reminders.sendReminder(
                        reminders.create(
                            ContributionReminder(
                                user = member,
                                contributionPeriod = period,
                                feeType = feeType,
                                amount = amount,
                                paymentDueDate = requireNotNull(paymentDueDate),
                                askedAt = sentAt,
                            ),
                        ),
                    )
                    remindersSent++
                }

                ContributionEmailKind.INCASSO_NOTIFICATION -> {
                    preNotifications.sendNotification(
                        preNotifications.create(
                            IncassoNotification(
                                user = member,
                                contributionPeriod = period,
                                feeType = feeType,
                                amount = amount,
                                debitDate = requireNotNull(debitDate),
                                askedAt = sentAt,
                            ),
                        ),
                    )
                    notificationsSent++
                }
            }
        }

        return ContributionEmailResult(
            remindersSent = remindersSent,
            incassoNotificationsSent = notificationsSent,
            notWrittenTo = userIds.distinct().size - recipients.size,
        )
    }

    /**
     * The selection has to describe itself before anything reads it: an id naming nobody,
     * an id named twice, or somebody ticked back in who was never selected all mean the
     * table the operator was looking at and the one the api holds have parted company.
     */
    private fun rejectIncoherentSelection(
        userIds: Collection<Long>,
        forciblyIncluded: Set<Long>,
        plan: ContributionEmailPlan,
    ) {
        val known = plan.rows.map { it.userId }.toSet()
        val violations = buildList {
            userIds.groupBy { it }.filterValues { it.size > 1 }.keys.sorted().ifEmpty { null }?.let { repeated ->
                add(
                    BulkSelectionRejected.Violation(
                        field = "userIds",
                        code = BulkSelectionRejected.DUPLICATE_USERS,
                        values = repeated,
                        message = "The selection names the same member more than once.",
                    ),
                )
            }
            // The planner drops an id that resolves to nobody, so the rows it kept name
            // everybody who exists.
            strayIds(userIds.toSet(), known)?.let { unknown ->
                add(
                    BulkSelectionRejected.Violation(
                        field = "userIds",
                        code = BulkSelectionRejected.UNKNOWN_USERS,
                        values = unknown,
                        message = "${unknown.size} of the selected users no longer exist.",
                    ),
                )
            }
            strayIds(forciblyIncluded, userIds.toSet())?.let { stray ->
                add(
                    BulkSelectionRejected.Violation(
                        field = "forciblyIncludedUserIds",
                        code = BulkSelectionRejected.UNKNOWN_FORCED,
                        values = stray,
                        message = "Some of the members ticked back in are not in the selection.",
                    ),
                )
            }
        }
        if (violations.isEmpty()) return
        throw BulkSelectionRejected(OBJECT_NAME, violations)
    }

    /**
     * A statement about somebody the send skips means the table has moved. Refused whole,
     * rather than leaving the operator believing they changed something they did not.
     */
    private fun rejectStatementsForNonRecipients(
        recipients: List<ContributionEmailRow>,
        forciblyIncluded: Set<Long>,
        feeTypeOverrides: Map<Long, BulkFeeType>,
        kindOverrides: Map<Long, ContributionEmailKind>,
    ) {
        val ids = recipients.map { it.userId }.toSet()
        val violations = buildList {
            strayIds(forciblyIncluded, ids)?.let { stray ->
                add(
                    BulkSelectionRejected.Violation(
                        field = "forciblyIncludedUserIds",
                        code = BulkSelectionRejected.NON_RECIPIENT_FORCED,
                        values = stray,
                        message = "Some of the members ticked back in are ones this send does not write to.",
                    ),
                )
            }
            strayIds(feeTypeOverrides.keys, ids)?.let { stray ->
                add(
                    BulkSelectionRejected.Violation(
                        field = "feeTypeOverrides",
                        code = BulkSelectionRejected.NON_RECIPIENT_FEE_TYPES,
                        values = stray,
                        message = "${stray.size} of the fee types name members this send does not write to.",
                    ),
                )
            }
            strayIds(kindOverrides.keys, ids)?.let { stray ->
                add(
                    BulkSelectionRejected.Violation(
                        field = "kindOverrides",
                        code = BulkSelectionRejected.NON_RECIPIENT_EMAIL_KINDS,
                        values = stray,
                        message = "${stray.size} of the chosen emails name members this send does not write to.",
                    ),
                )
            }
        }
        if (violations.isEmpty()) return
        throw BulkSelectionRejected(OBJECT_NAME, violations)
    }

    /**
     * Each date is required exactly when it reaches somebody, and each one the request
     * states has to sit against the period being billed.
     */
    private fun rejectDates(
        period: ContributionPeriod,
        kinds: List<ContributionEmailKind>,
        paymentDueDate: LocalDate?,
        debitDate: LocalDate?,
    ) {
        val violations =
            dateViolations("paymentDueDate", paymentDueDate, ContributionEmailKind.REMINDER in kinds, period) +
                dateViolations(
                    "debitDate",
                    debitDate,
                    ContributionEmailKind.INCASSO_NOTIFICATION in kinds,
                    period,
                )
        if (violations.isEmpty()) return
        throw BulkFieldRejected(OBJECT_NAME, violations)
    }

    private fun dateViolations(
        field: String,
        date: LocalDate?,
        reachesSomebody: Boolean,
        period: ContributionPeriod,
    ): List<BulkFieldRejected.Violation> = when {
        date == null ->
            if (reachesSomebody) {
                listOf(
                    BulkFieldRejected.Violation(
                        field = field,
                        code = BulkFieldRejected.DATE_REQUIRED,
                        message = "A date is required: somebody in this batch gets an email that states one.",
                    ),
                )
            } else {
                emptyList()
            }

        date < period.startDate || date > period.endDate.plusMonths(MONTHS_PAST_PERIOD_END) -> listOf(
            BulkFieldRejected.Violation(
                field = field,
                code = BulkFieldRejected.DATE_OUTSIDE_PERIOD,
                message = "A date must fall within the contribution period, or shortly after it ends.",
            ),
        )

        else -> emptyList()
    }

    private fun strayIds(stated: Set<Long>, recipients: Set<Long>): List<Long>? =
        stated.filterNot { it in recipients }.sorted().ifEmpty { null }

    private companion object {
        const val OBJECT_NAME = "SendPaymentEmailsRequest"

        /**
         * How far past the end of a period a date may still fall. Chasing the last unpaid
         * members in the final weeks needs a due date beyond the period; a mistyped year
         * does not get one.
         *
         * Mirrored in the browser by `PERIOD_OVERHANG_MONTHS` in
         * `services/frontend/src/utils/contributionEmail.ts`. Two implementations of one
         * rule: changing this one means changing that one.
         */
        const val MONTHS_PAST_PERIOD_END = 3L
    }
}

/** Counted per statement, because one total hides either half being zero. */
data class ContributionEmailResult(
    val remindersSent: Int,
    val incassoNotificationsSent: Int,
    val notWrittenTo: Int,
)
