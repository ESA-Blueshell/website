package net.blueshell.api.contribution.domain

import net.blueshell.api.email.api.EmailSenderService
import net.blueshell.api.jobs.api.AbstractJsonJobHandler
import net.blueshell.api.shared.job.EmailJobs
import net.blueshell.api.shared.job.requireExists
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * Renders and sends a recorded payment request.
 *
 * Which of the two reminder emails goes out is read off the record rather than decided
 * here: a request written by a bulk send states a fee, so it quotes one amount, the reason
 * for it and the date it is due. One written from a single row states none, so it lists the
 * period's fee options instead.
 */
@Component
class ContributionReminderEmailJob(
    objectMapper: ObjectMapper,
    private val reminders: ContributionReminderService,
    private val emails: EmailSenderService,
    private val channels: PaymentChannels,
) : AbstractJsonJobHandler<EmailJobs.ContributionReminderPayload>(
    objectMapper,
    EmailJobs.ContributionReminder.payloadType,
) {
    override val jobType: String = EmailJobs.ContributionReminder.type

    override fun handlePayload(payload: EmailJobs.ContributionReminderPayload) {
        val reminder = requireExists { reminders.findById(payload.contributionReminderId) }
        val stated = reminder.statedFee
        val content = if (stated == null) {
            createContributionReminderEmail(reminder.user, reminder.contributionPeriod, channels)
        } else {
            createContributionReminderEmail(
                reminder.user,
                reminder.contributionPeriod,
                stated.feeType,
                stated.amount,
                stated.paymentDueDate,
                channels,
            )
        }
        emails.send(content, "email.contribution-reminder", currentExecutionId)
    }
}
