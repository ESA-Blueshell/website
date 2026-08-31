package net.blueshell.api.contribution.domain

import net.blueshell.api.contribution.persistence.ContributionReminder
import net.blueshell.api.email.api.EmailSenderService
import net.blueshell.api.jobs.api.AbstractJsonJobHandler
import net.blueshell.api.shared.job.EmailJobs
import net.blueshell.api.shared.job.requireExists
import net.blueshell.api.platform.config.BankProperties
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * Renders and sends a recorded payment request.
 *
 * Which of the two reminder emails goes out is read off the record rather than decided
 * here: a request written by the fee cycle carries the fee type it stated and the date it
 * asked to be paid by, so it quotes one amount and the reason for it. One written from a
 * single row carries neither, so it lists the period's fee options.
 */
@Component
class ContributionReminderEmailJob(
    objectMapper: ObjectMapper,
    private val reminders: ContributionReminderService,
    private val emails: EmailSenderService,
    private val bank: BankProperties,
    @param:Value($$"${frontend.url}") private val frontendUrl: String,
) : AbstractJsonJobHandler<EmailJobs.ContributionReminderPayload>(
    objectMapper,
    EmailJobs.ContributionReminder.payloadType,
) {
    override val jobType: String = EmailJobs.ContributionReminder.type

    override fun handlePayload(payload: EmailJobs.ContributionReminderPayload) {
        val reminder = requireExists {
            reminders.findById(ContributionReminder.Id(payload.userId, payload.contributionPeriodId))
        }
        val feeType = reminder.feeType
        val amount = reminder.amount
        val dueDate = reminder.paymentDueDate
        val content = if (feeType != null && amount != null && dueDate != null) {
            createContributionReminderEmail(
                reminder.user,
                reminder.contributionPeriod,
                feeType,
                amount,
                dueDate,
                bank,
            )
        } else {
            createContributionReminderEmail(reminder.user, reminder.contributionPeriod, frontendUrl)
        }
        emails.send(content, "email.contribution-reminder", currentExecutionId)
    }
}
