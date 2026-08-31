package net.blueshell.api.contribution.domain

import net.blueshell.api.contribution.api.ContributionPeriodService
import net.blueshell.api.email.api.EmailPreviewRenderer
import net.blueshell.api.platform.config.BankProperties
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import net.blueshell.api.shared.dto.bulk.FeeCycleGroup
import net.blueshell.api.shared.email.EmailContent
import net.blueshell.api.user.api.UserService
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

/**
 * One member's fee-cycle email, rendered for reading.
 *
 * Which of the two statements it is, and what it says, come from the same plan and the same
 * builders the send uses, so changing the email changes the preview and the two cannot
 * drift. Nothing here writes a record or enqueues a job: reading is free.
 */
@Service
class FeeCycleEmailPreviewService(
    private val planner: FeeCyclePlanner,
    private val periods: ContributionPeriodService,
    private val users: UserService,
    private val renderer: EmailPreviewRenderer,
    private val bank: BankProperties,
) {
    @Transactional(readOnly = true)
    fun preview(
        contributionPeriodId: Long,
        userId: Long,
        dates: FeeCycleDates,
        feeType: BulkFeeType?,
    ): FeeCycleEmailPreview {
        // Through the plan rather than from the member's flag directly: which statement a
        // member gets, and whether they get one at all, is the plan's answer.
        val participant = planner.plan(contributionPeriodId).byUserId(userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "That member is not in this period's fee cycle")
        // A member the cycle will not write to has no email to read. Refused here rather
        // than left to the dialog to hide, which would render an honorary member's
        // statement, or one addressed to nobody.
        if (!participant.willSend) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "This cycle sends nothing to that member")
        }
        // A recipient always has a fee type: the only member without one is honorary, and an
        // honorary member never gets past the check above.
        val effectiveFeeType = feeType ?: participant.feeType!!

        val member = users.findById(userId)
        val period = periods.findById(contributionPeriodId)

        // Priced here the way the send prices it, because nothing is recorded yet to read
        // an amount off.
        val amount = resolveFeeAmount(effectiveFeeType, period)
        val content: EmailContent = when (participant.group) {
            FeeCycleGroup.TRANSFER ->
                createContributionReminderEmail(member, period, effectiveFeeType, amount, dates.paymentDue, bank)
            FeeCycleGroup.DIRECT_DEBIT ->
                createIncassoNotificationEmail(member, period, effectiveFeeType, amount, dates.debit)
        }

        val rendered = renderer.render(content)
        return FeeCycleEmailPreview(
            group = participant.group,
            feeType = effectiveFeeType,
            subject = rendered.subject,
            html = rendered.html,
            recipientEmail = content.recipientEmail,
            recipientName = content.recipientName,
        )
    }
}

/** A fee-cycle email rendered for reading, and which statement it turned out to be. */
data class FeeCycleEmailPreview(
    val group: FeeCycleGroup,
    val feeType: BulkFeeType,
    val subject: String,
    val html: String,
    val recipientEmail: String,
    val recipientName: String,
)
