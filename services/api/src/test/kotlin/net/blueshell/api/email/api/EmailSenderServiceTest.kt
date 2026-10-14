package net.blueshell.api.email.api

import net.blueshell.api.email.domain.EmailService
import net.blueshell.api.email.domain.EmailTemplateService
import net.blueshell.api.email.domain.EmailTransportClient
import net.blueshell.api.email.persistence.Email
import net.blueshell.api.shared.email.EmailContent
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.mail.MailSendException

@ExtendWith(OutputCaptureExtension::class)
class EmailSenderServiceTest {
    private val templates = mock<EmailTemplateService>()
    private val client = mock<EmailTransportClient>()
    private val emails = mock<EmailService>()
    private val sender =
        EmailSenderService(templates, client, emails, "https://site", "https://api", "Blueshell", "noreply@club.test", "board@club.test")
    private val content = EmailContent("ann@example.org", "Ann", "Welcome", "Hello")
    private val outbox = Email(recipientEmail = "ann@example.org").also { it.id = 5 }

    init {
        whenever(templates.createEmail(any(), any(), any(), any())).thenReturn("<html><body></body></html>")
        whenever(emails.forSend(content, "welcome", null)).thenReturn(outbox)
    }

    @Test
    fun `a sent email is logged by its outbox id`(output: CapturedOutput) {
        whenever(client.send(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn("<m@club.test>")

        sender.send(content, "welcome")

        assertThat(output.all).contains("Sent email id=5 type=welcome").doesNotContain("ann@example.org")
    }

    @Test
    fun `a failed send records and logs the failure without the address it names`(output: CapturedOutput) {
        whenever(client.send(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenThrow(MailSendException("550 <ann@example.org> unknown"))

        assertThatThrownBy { sender.send(content, "welcome") }.isInstanceOf(IllegalStateException::class.java)

        verify(emails).markFailed(eq(outbox), eq("MailSendException"), eq("550 <[email]> unknown"))
        assertThat(output.all).contains("Failed to send email id=5 type=welcome")
    }

    @Test
    fun `sends into the record its job queued, with the tracking pixel in it`() {
        val queued = Email(trackingToken = "tok")
        whenever(emails.forSend(content, "email.test", 7)).thenReturn(queued)
        whenever(client.send(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn("<m@club.test>")

        sender.send(content, "email.test", 7)

        verify(client).send(
            eq("ann@example.org"),
            eq("Ann"),
            eq("Welcome"),
            argThat { contains("https://api/track/email/open/tok") },
            any(),
            any(),
            any(),
            any(),
        )
        verify(emails).markSent(queued, "<m@club.test>")
    }

    @Test
    fun `fails the email a job queued where the job broke before sending, without the address its error names`() {
        sender.failQueued(7, IllegalStateException("No user ann@example.org"))
        sender.failQueued(null, IllegalStateException("no job"))
        sender.failQueued(8, IllegalStateException())

        verify(emails).failQueued(7, "IllegalStateException", "No user [email]")
        verify(emails).failQueued(8, "IllegalStateException", "Could not compose the email")
    }
}
