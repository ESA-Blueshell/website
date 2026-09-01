package net.blueshell.api.contribution.domain

import net.blueshell.api.contribution.api.ContributionPeriodService
import net.blueshell.api.contribution.persistence.ContributionReminder
import net.blueshell.api.contribution.persistence.IncassoNotification
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import net.blueshell.api.shared.dto.bulk.BulkSelectionRejected
import net.blueshell.api.user.api.UserService
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
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
        val recipients = plan.recipients(forciblyIncluded)
        rejectStatementsForNonRecipients(recipients, feeTypeOverrides, kindOverrides)

        val kindOf = { row: ContributionEmailRow -> kindOverrides[row.userId] ?: row.defaultKind }
        requireDatesFor(recipients.map(kindOf), paymentDueDate, debitDate)

        val period = periods.findById(contributionPeriodId)
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

    /** A date is required exactly when it reaches somebody. */
    private fun requireDatesFor(
        kinds: List<ContributionEmailKind>,
        paymentDueDate: LocalDate?,
        debitDate: LocalDate?,
    ) {
        if (ContributionEmailKind.REMINDER in kinds && paymentDueDate == null) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "A payment due date is required: some of these members are being asked to transfer.",
            )
        }
        if (ContributionEmailKind.INCASSO_NOTIFICATION in kinds && debitDate == null) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "A debit date is required: some of these members are being told when the money is taken.",
            )
        }
    }

    /**
     * A statement about somebody the send skips means the table has moved. Refused whole,
     * rather than leaving the operator believing they changed something they did not.
     */
    private fun rejectStatementsForNonRecipients(
        recipients: List<ContributionEmailRow>,
        feeTypeOverrides: Map<Long, BulkFeeType>,
        kindOverrides: Map<Long, ContributionEmailKind>,
    ) {
        val ids = recipients.map { it.userId }.toSet()
        val violations = buildList {
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
        throw BulkSelectionRejected("SendPaymentEmailsRequest", violations)
    }

    private fun strayIds(stated: Set<Long>, recipients: Set<Long>): List<Long>? =
        stated.filterNot { it in recipients }.sorted().ifEmpty { null }
}

/** Counted per statement, because one total hides either half being zero. */
data class ContributionEmailResult(
    val remindersSent: Int,
    val incassoNotificationsSent: Int,
    val notWrittenTo: Int,
)
