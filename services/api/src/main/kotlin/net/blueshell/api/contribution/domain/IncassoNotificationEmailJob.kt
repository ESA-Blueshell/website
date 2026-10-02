package net.blueshell.api.contribution.domain

import net.blueshell.api.email.api.EmailJob
import net.blueshell.api.email.api.EmailSenderService
import net.blueshell.api.shared.email.EmailContent
import net.blueshell.api.shared.job.requireExists
import net.blueshell.api.user.api.MaskedIban
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
) : EmailJob<ContributionJobs.IncassoNotificationPayload>(
        objectMapper,
        ContributionJobs.IncassoNotification,
        emails,
        "email.incasso-notification",
    ) {
    override fun compose(payload: ContributionJobs.IncassoNotificationPayload): EmailContent {
        val notification = requireExists { notifications.findById(payload.incassoNotificationId) }
        return createIncassoNotificationEmail(
            notification.user,
            notification.contributionPeriod,
            notification.feeType,
            notification.amount,
            notification.debitDate,
            MaskedIban.of(notification.ibanMasked),
            notification.mandateReference,
        )
    }
}
