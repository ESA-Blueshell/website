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
    private val controller = EmailManagementController(emails, mock(), mock(), mock(), resends, mock(), mock())

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

    @Test
    fun `answers one email with the emails made again from it, and who queued it where a person did`() {
        val first =
            Entities.email(2).apply {
                initiatedByUserId = 7
                createdAt = Instant.EPOCH
                updatedAt = Instant.EPOCH
            }
        val made =
            Entities.email(3).apply {
                resentFromId = 2
                createdAt = Instant.EPOCH
                updatedAt = Instant.EPOCH
            }
        whenever(emails.findById(2)).thenReturn(first)
        whenever(resends.madeFrom(2)).thenReturn(listOf(made))

        val detail = controller.findEmail(2)

        assertThat(detail.email.initiatedByUserId).isEqualTo(7)
        assertThat(detail.resends.map { it.id }).containsExactly(3)
    }
}
