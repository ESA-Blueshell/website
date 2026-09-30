package net.blueshell.api.contribution.domain

import net.blueshell.api.email.api.EmailJob
import net.blueshell.api.email.api.EmailSenderService
import net.blueshell.api.shared.email.EmailContent
import net.blueshell.api.shared.job.requireExists
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
    private val channels: PaymentChannels,
) : EmailJob<ContributionJobs.JoiningContributionPayload>(
        objectMapper,
        ContributionJobs.JoiningContribution,
        emails,
        "email.joining-contribution",
    ) {
    override fun compose(payload: ContributionJobs.JoiningContributionPayload): EmailContent {
        val ask = requireExists { reminders.findById(payload.contributionReminderId) }
        // Written by JoiningContributionAskService, which always states a fee.
        val stated = requireNotNull(ask.statedFee) { "A joining ask states one fee" }
        return createJoiningContributionEmail(
            ask.user,
            ask.contributionPeriod,
            stated.feeType,
            stated.amount,
            stated.paymentDueDate,
            channels,
        )
    }
}
