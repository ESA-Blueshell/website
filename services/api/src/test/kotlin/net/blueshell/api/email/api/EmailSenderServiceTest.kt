package net.blueshell.api.email.api

import net.blueshell.api.email.domain.EmailService
import net.blueshell.api.email.domain.EmailTemplateService
import net.blueshell.api.email.domain.EmailTransportClient
import net.blueshell.api.email.persistence.Email
import net.blueshell.api.shared.email.EmailContent
import net.blueshell.api.shared.job.NonRetryableJobException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
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
        whenever(emails.createPending(content, "welcome", null)).thenReturn(outbox)
    }

    @Test
    fun `a sent email is logged by its outbox id`(output: CapturedOutput) {
        whenever(client.send(any(), any(), any(), any(), any(), any(), any())).thenReturn("<m@club.test>")

        sender.send(content, "welcome")

        assertThat(output.all).contains("Sent email id=5 type=welcome").doesNotContain("ann@example.org")
    }

    @Test
    fun `a failed send records and logs the failure without the address it names`(output: CapturedOutput) {
        whenever(client.send(any(), any(), any(), any(), any(), any(), any())).thenThrow(MailSendException("550 <ann@example.org> unknown"))

        assertThatThrownBy { sender.send(content, "welcome") }.isInstanceOf(IllegalStateException::class.java)

        verify(emails).markFailed(eq(outbox), eq("MailSendException"), eq("550 <[email]> unknown"))
        assertThat(output.all).contains("Failed to send email id=5 type=welcome")
    }

    @Test
    fun `a refused address keeps its non-retryable type for the job executor`() {
        val refusal = NonRetryableJobException("550 refused")
        whenever(client.send(any(), any(), any(), any(), any(), any(), any())).thenThrow(refusal)

        assertThatThrownBy { sender.send(content, "welcome") }.isSameAs(refusal)

        verify(emails).markFailed(eq(outbox), eq("NonRetryableJobException"), eq("550 refused"))
    }

    @Test
    fun `coercion and null bugs keep their terminal types too`() {
        listOf(NullPointerException("null value"), ClassCastException("bad cast")).forEach { failure ->
            whenever(client.send(any(), any(), any(), any(), any(), any(), any())).thenThrow(failure)

            assertThatThrownBy { sender.send(content, "welcome") }.isSameAs(failure)
        }
    }

    @Test
    fun `a job that already sent this type to this recipient is not sent again`(output: CapturedOutput) {
        whenever(emails.hasSent(7L, "welcome", "ann@example.org")).thenReturn(true)

        sender.send(content, "welcome", 7L)

        assertThat(output.all).contains("Email already sent for job execution 7 type=welcome; skipping.")
        verify(templates, never()).createEmail(any(), any(), any(), any())
        verify(emails, never()).createPending(any(), any(), anyOrNull())
        verifyNoInteractions(client)
    }

    @Test
    fun `a job without a prior send is looked up before it renders`() {
        whenever(emails.createPending(content, "welcome", 7L)).thenReturn(outbox)
        whenever(client.send(any(), any(), any(), any(), any(), any(), any())).thenReturn("<m@club.test>")

        sender.send(content, "welcome", 7L)

        verify(emails).hasSent(7L, "welcome", "ann@example.org")
        verify(emails).createPending(content, "welcome", 7L)
    }

    @Test
    fun `a coercion bug keeps its terminal type instead of being wrapped as retryable`() {
        whenever(client.send(any(), any(), any(), any(), any(), any(), any())).thenThrow(IllegalArgumentException("Bad recipient"))

        assertThatThrownBy { sender.send(content, "welcome") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Bad recipient")

        verify(emails).markFailed(eq(outbox), eq("IllegalArgumentException"), eq("Bad recipient"))
    }
}
