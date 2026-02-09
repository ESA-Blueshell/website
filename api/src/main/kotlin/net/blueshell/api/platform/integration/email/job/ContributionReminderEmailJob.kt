package net.blueshell.api.platform.integration.email.job

import com.fasterxml.jackson.databind.ObjectMapper
import net.blueshell.api.platform.integration.email.service.EmailService
import net.blueshell.api.platform.integration.queue.AbstractMailJobHandler
import org.springframework.stereotype.Component

@Component
class ContributionReminderEmailJob(
    objectMapper: ObjectMapper,
    emails: EmailService
) : AbstractMailJobHandler<ContributionReminderEmailJob.Payload>(
    objectMapper,
    Payload::class.java,
    emails
) {
    override val jobType: String = TYPE

    override fun handlePayload(payload: Payload) {
        emails.sendContributionReminderEmail(payload.userId, payload.contributionPeriodId)
    }

    companion object {
        const val TYPE = "email.contribution-reminder"
    }

    data class Payload(
        val userId: Long,
        val contributionPeriodId: Long
    )
}
