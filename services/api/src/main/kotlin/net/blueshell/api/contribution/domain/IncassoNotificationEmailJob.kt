package net.blueshell.api.contribution.domain

import net.blueshell.api.email.api.EmailSenderService
import net.blueshell.api.jobs.api.AbstractJsonJobHandler
import net.blueshell.api.jobs.api.EMAIL_RETRY_SCHEDULE
import net.blueshell.api.shared.job.NonRetryableJobException
import net.blueshell.api.shared.job.requireExists
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/**
 * Renders and sends a recorded pre-notification.
 *
 * The record carries the fee type and the debit date, so the email states the reason that
 * actually applied rather than one recovered from an amount. A run after the notice cutoff
 * fails for good: retrying cannot make the email arrive before the debit it announces.
 */
@Component
class IncassoNotificationEmailJob(
    objectMapper: ObjectMapper,
    private val notifications: IncassoNotificationService,
    private val emails: EmailSenderService,
    @Value("\${app.contribution.incasso-notice-days:0}")
    private val noticeDays: Int = 0,
    private val clock: Clock = Clock.systemDefaultZone(),
) : AbstractJsonJobHandler<ContributionJobs.IncassoNotificationPayload>(
        objectMapper,
        ContributionJobs.IncassoNotification,
    ) {
    override val retrySchedule get() = EMAIL_RETRY_SCHEDULE

    override fun handlePayload(payload: ContributionJobs.IncassoNotificationPayload) {
        val notification = requireExists { notifications.findById(payload.incassoNotificationId) }
        val today = LocalDate.now(clock.withZone(AMSTERDAM))
        if (!forced && today > notification.debitDate.minusDays(noticeDays.toLong())) {
            throw NonRetryableJobException(
                "The pre-notification can no longer arrive the notice period before the debit date " +
                    "${notification.debitDate}.",
            )
        }
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

    private companion object {
        val AMSTERDAM: ZoneId = ZoneId.of("Europe/Amsterdam")
    }
}
