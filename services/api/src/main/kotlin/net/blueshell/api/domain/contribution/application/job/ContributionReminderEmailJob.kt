package net.blueshell.api.domain.contribution.application.job

import net.blueshell.api.domain.contribution.application.ContributionReminderService
import net.blueshell.api.domain.contribution.application.email.createContributionReminderEmail
import net.blueshell.api.domain.contribution.persistence.ContributionReminder
import net.blueshell.api.email.api.EmailSenderService
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import net.blueshell.api.shared.job.EmailJobs
import net.blueshell.api.shared.job.requireExists
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class ContributionReminderEmailJob(
    objectMapper: ObjectMapper,
    private val reminders: ContributionReminderService,
    private val emails: EmailSenderService,
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
        emails.send(
            createContributionReminderEmail(reminder.user, reminder.contributionPeriod, frontendUrl),
            "email.contribution-reminder",
            currentExecutionId,
        )
    }
}
