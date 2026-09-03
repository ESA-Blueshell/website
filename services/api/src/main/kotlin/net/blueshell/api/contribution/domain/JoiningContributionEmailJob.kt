package net.blueshell.api.contribution.domain

import net.blueshell.api.email.api.EmailSenderService
import net.blueshell.api.jobs.api.AbstractJsonJobHandler
import net.blueshell.api.platform.config.BankProperties
import net.blueshell.api.shared.job.EmailJobs
import net.blueshell.api.shared.job.requireExists
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * Renders and sends the ask made of a member on joining.
 *
 * Reads the same record the reminder job reads. Which of the two emails a record becomes is
 * the job type's business, not the row's: an ask made on joining and one made by a treasurer
 * are the same thing recorded, and only the sentence the member reads differs.
 */
@Component
class JoiningContributionEmailJob(
    objectMapper: ObjectMapper,
    private val reminders: ContributionReminderService,
    private val emails: EmailSenderService,
    private val bank: BankProperties,
    @param:Value($$"${frontend.url}") private val frontendUrl: String,
) : AbstractJsonJobHandler<EmailJobs.JoiningContributionPayload>(
    objectMapper,
    EmailJobs.JoiningContribution.payloadType,
) {
    override val jobType: String = EmailJobs.JoiningContribution.type

    override fun handlePayload(payload: EmailJobs.JoiningContributionPayload) {
        val ask = requireExists { reminders.findById(payload.contributionReminderId) }
        val content = createJoiningContributionEmail(
            ask.user,
            ask.contributionPeriod,
            // Written by JoiningContributionAskService, which sets all three.
            requireNotNull(ask.feeType) { "A joining ask states one fee type" },
            requireNotNull(ask.amount) { "A joining ask states one amount" },
            requireNotNull(ask.paymentDueDate) { "A joining ask states a due date" },
            bank,
            frontendUrl,
        )
        emails.send(content, "email.joining-contribution", currentExecutionId)
    }
}
