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
    private val templates: EmailTemplateService = mock()
    private val transport: EmailTransportClient = mock()
    private val records: EmailService = mock()
    private val sender =
        EmailSenderService(templates, transport, records, "https://site", "https://api", "Blueshell", "no-reply@b.nl", "board@b.nl")
    private val content = EmailContent("a@b.nl", "Ann", "Hi", "Body")
    private val queued = Email(recipientEmail = "a@b.nl", trackingToken = "tok").also { it.id = 5 }

    init {
        whenever(templates.createEmail("a@b.nl", "Ann", "Hi", "Body")).thenReturn("<html><body>Hi</body></html>")
        whenever(records.forSend(content, "email.test", 7)).thenReturn(queued)
    }

    @Test
    fun `sends into the record its job queued, with the tracking pixel in it`() {
        whenever(transport.send(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn("<m@b.nl>")

        sender.send(content, "email.test", 7)

        verify(
            transport,
        ).send(
            eq("a@b.nl"),
            eq("Ann"),
            eq("Hi"),
            argThat { contains("https://api/track/email/open/tok") },
            any(),
            any(),
            any(),
            eq(emptyMap()),
        )
        verify(records).markSent(queued, "<m@b.nl>")
    }

    @Test
    fun `a sent email is logged by its outbox id`(output: CapturedOutput) {
        whenever(transport.send(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn("<m@b.nl>")

        sender.send(content, "email.test", 7)

        assertThat(output.all).contains("Sent email id=5 type=email.test").doesNotContain("a@b.nl")
    }

    @Test
    fun `a failed send records and logs the failure without the address it names`(output: CapturedOutput) {
        whenever(transport.send(any(), any(), any(), any(), any(), any(), any(), any())).thenThrow(MailSendException("550 <a@b.nl> unknown"))

        assertThatThrownBy { sender.send(content, "email.test", 7) }.isInstanceOf(IllegalStateException::class.java)

        verify(records).markFailed(eq(queued), eq("MailSendException"), eq("550 <[email]> unknown"))
        assertThat(output.all).contains("Failed to send email id=5 type=email.test")
    }
}
