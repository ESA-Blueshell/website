package net.blueshell.api.email.domain

import net.blueshell.api.alerts.api.AlertAudience
import net.blueshell.api.alerts.api.AlertKind
import net.blueshell.api.alerts.api.RaisedAlert
import net.blueshell.api.email.persistence.EmailRepository
import net.blueshell.api.shared.enums.EmailDeliveryStatus
import net.blueshell.api.testsupport.Entities
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class EmailAlertsTest {
    private val now = Instant.parse("2026-09-30T10:00:00Z")
    private val emails: EmailRepository = mock()
    private val alerts = EmailAlerts(emails, Clock.fixed(now, ZoneOffset.UTC))
    private val undelivered = setOf(EmailDeliveryStatus.FAILED, EmailDeliveryStatus.BOUNCED)
    private val after = now.minus(EmailAlerts.WINDOW)

    @Test
    fun `failed and bounced emails of the last month raise one board alert`() {
        val latest = Entities.email(40).apply { createdAt = now.minusSeconds(30) }
        whenever(emails.findTopByDeliveryStatusInAndCreatedAtAfterOrderByIdDesc(undelivered, after)).thenReturn(latest)
        whenever(emails.countByDeliveryStatusInAndCreatedAtAfter(undelivered, after)).thenReturn(2)

        assertThat(alerts.audience).isEqualTo(AlertAudience.BOARD)
        assertThat(alerts.raised())
            .containsExactly(RaisedAlert("email-failed:40", AlertKind.EMAIL_FAILED, null, null, 2, now.minusSeconds(30)))
    }

    @Test
    fun `none raises nothing`() {
        assertThat(alerts.raised()).isEmpty()
    }
}
