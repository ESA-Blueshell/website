package net.blueshell.api.email.domain

import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.mock.env.MockEnvironment

class ImapBouncePollingServiceTest {
    private val emailService: EmailService = mock()

    @Test
    fun `a poll that cannot reach the mailbox logs and marks nothing`() {
        val environment = MockEnvironment().withProperty("email.bounce.imap.password", "secret")
        // Port 1 on loopback refuses the connection at once.
        val poller = ImapBouncePollingService(emailService, "127.0.0.1", 1, "bounce@example.com", environment, "INBOX", false)

        assertThatCode { poller.pollBounces() }.doesNotThrowAnyException()
        verifyNoInteractions(emailService)
    }
}
