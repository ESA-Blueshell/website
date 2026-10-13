package net.blueshell.api.email.web

import net.blueshell.api.email.domain.EmailResends
import net.blueshell.api.email.domain.EmailService
import net.blueshell.api.shared.enums.EmailDeliveryStatus
import net.blueshell.api.testsupport.Entities
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant

class EmailResendTest {
    private val emails: EmailService = mock()
    private val resends: EmailResends = mock()
    private val controller = EmailManagementController(emails, mock(), mock(), mock(), resends)

    @Test
    fun `answers the email made again, linked to the one it came from, and counts what is queued`() {
        val made =
            Entities.email(3).apply {
                resentFromId = 2
                createdAt = Instant.EPOCH
                updatedAt = Instant.EPOCH
            }
        whenever(resends.resend(2)).thenReturn(made)
        whenever(resends.mayResend(made)).thenReturn(false)
        whenever(emails.countByStatus(EmailDeliveryStatus.QUEUED)).thenReturn(4)

        val answered = controller.resend(2)

        assertThat(answered.resentFromId).isEqualTo(2)
        assertThat(answered.resendable).isFalse()
        assertThat(controller.getStats().queuedCount).isEqualTo(4)
    }
}
