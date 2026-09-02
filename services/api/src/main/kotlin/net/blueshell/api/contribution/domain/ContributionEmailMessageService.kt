package net.blueshell.api.contribution.domain

import net.blueshell.api.contribution.api.ContributionPeriodService
import net.blueshell.api.email.api.EmailPreviewRenderer
import net.blueshell.api.platform.config.BankProperties
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import net.blueshell.api.shared.email.EmailContent
import net.blueshell.api.user.api.UserService
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate

/**
 * One member's payment email, rendered for reading. Built by the same builders the send
 * uses, so the two cannot drift. Writes no record and queues no job.
 */
@Service
class ContributionEmailMessageService(
    private val planner: ContributionEmailPlanner,
    private val periods: ContributionPeriodService,
    private val users: UserService,
    private val renderer: EmailPreviewRenderer,
    private val bank: BankProperties,
) {
    /** [kind] is whatever the row is set to, so a switched row previews what it will get. */
    @Transactional(readOnly = true)
    fun render(
        kind: ContributionEmailKind,
        contributionPeriodId: Long,
        userId: Long,
        date: LocalDate,
        feeType: BulkFeeType?,
    ): ContributionEmailMessage {
        val row = planner.plan(contributionPeriodId, listOf(userId)).byUserId(userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "That member could not be read")
        // A warned member may yet be overruled, so theirs is readable; a hard-excluded one
        // has no email at all.
        if (row.isHardExcluded) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "This send writes nothing to that member")
        }
        val effectiveFeeType = feeType ?: row.feeType!!

        val member = users.findById(userId)
        val period = periods.findById(contributionPeriodId)
        val amount = resolveFeeAmount(effectiveFeeType, period)

        val content: EmailContent = when (kind) {
            ContributionEmailKind.REMINDER ->
                createContributionReminderEmail(member, period, effectiveFeeType, amount, date, bank)
            ContributionEmailKind.INCASSO_NOTIFICATION ->
                createIncassoNotificationEmail(member, period, effectiveFeeType, amount, date)
        }

        val rendered = renderer.render(content)
        return ContributionEmailMessage(
            kind = kind,
            feeType = effectiveFeeType,
            subject = rendered.subject,
            html = rendered.html,
            recipientEmail = content.recipientEmail,
            recipientName = content.recipientName,
        )
    }
}

data class ContributionEmailMessage(
    val kind: ContributionEmailKind,
    val feeType: BulkFeeType,
    val subject: String,
    val html: String,
    val recipientEmail: String,
    val recipientName: String,
)
