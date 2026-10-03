package net.blueshell.api.email.domain

import net.blueshell.api.alerts.api.AlertAudience
import net.blueshell.api.alerts.api.AlertKind
import net.blueshell.api.alerts.api.AlertSource
import net.blueshell.api.alerts.api.RaisedAlert
import net.blueshell.api.email.persistence.EmailRepository
import net.blueshell.api.shared.enums.EmailDeliveryStatus
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration

/**
 * Emails that bounced or failed lately, for the board to resend. Only the last [WINDOW]: an old
 * bounce nobody resent is history, not something to act on now.
 */
@Component
class EmailAlerts(
    private val emails: EmailRepository,
    private val clock: Clock,
) : AlertSource {
    override val audience = AlertAudience.BOARD

    @Transactional(readOnly = true)
    override fun raised(): List<RaisedAlert> {
        val after = clock.instant().minus(WINDOW)
        val latest = emails.findTopByDeliveryStatusInAndCreatedAtAfterOrderByIdDesc(UNDELIVERED, after) ?: return emptyList()
        return listOf(
            RaisedAlert(
                key = "email-failed:${latest.id}",
                kind = AlertKind.EMAIL_FAILED,
                subjectId = null,
                subjectLabel = null,
                count = emails.countByDeliveryStatusInAndCreatedAtAfter(UNDELIVERED, after),
                since = latest.createdAt,
            ),
        )
    }

    companion object {
        val WINDOW: Duration = Duration.ofDays(30)
        private val UNDELIVERED = setOf(EmailDeliveryStatus.FAILED, EmailDeliveryStatus.BOUNCED)
    }
}
