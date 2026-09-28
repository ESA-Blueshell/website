package net.blueshell.api.contribution.domain

import net.blueshell.api.email.api.EmailSenderService
import net.blueshell.api.jobs.api.AbstractJsonJobHandler
import net.blueshell.api.shared.job.requireExists
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * Renders and sends a recorded pre-notification.
 *
 * The record carries the fee type and the debit date, so the email states the reason that
 * actually applied rather than one recovered from an amount.
 */
@Component
class IncassoNotificationEmailJob(
    objectMapper: ObjectMapper,
    private val notifications: IncassoNotificationService,
    private val emails: EmailSenderService,
) : AbstractJsonJobHandler<ContributionJobs.IncassoNotificationPayload>(
        objectMapper,
        ContributionJobs.IncassoNotification,
    ) {
    override fun handlePayload(payload: ContributionJobs.IncassoNotificationPayload) {
        val notification = requireExists { notifications.findById(payload.incassoNotificationId) }
        emails.send(
            createIncassoNotificationEmail(
                notification.user,
                notification.contributionPeriod,
                notification.feeType,
                notification.amount,
                notification.debitDate,
            ),
            "email.incasso-notification",
            currentExecutionId,
        )
    }
}
