package net.blueshell.api.platform.integration.email

import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import net.blueshell.api.platform.integration.email.adapter.SmtpEmailClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.mail.javamail.JavaMailSender
import java.util.Properties

class SmtpEmailClientTest {

    private val session: Session = Session.getInstance(Properties())
    private val mailSender: JavaMailSender = mock<JavaMailSender>().also {
        whenever(it.createMimeMessage()).thenAnswer { MimeMessage(session) }
    }
    private val client = SmtpEmailClient(mailSender)

    @Test
    fun `send stamps the Message-ID header on the outgoing MimeMessage`() {
        val captor = argumentCaptor<MimeMessage>()

        val messageId = client.send(
            toEmail = "to@example.com",
            toName = "Recipient",
            subject = "Hello",
            htmlContent = "<p>Hi</p>",
            senderName = "Blueshell",
            senderAddress = "no-reply@esa-blueshell.nl",
            replyToAddress = "board@blueshell.utwente.nl",
        )

        verify(mailSender).send(captor.capture())
        val sent = captor.firstValue
        assertThat(sent.getHeader("Message-ID")).containsExactly(messageId)
    }

    @Test
    fun `send writes From, To, Subject, HTML body and Reply-To`() {
        val captor = argumentCaptor<MimeMessage>()
        client.send(
            toEmail = "alice@example.com",
            toName = "Alice",
            subject = "Welcome",
            htmlContent = "<p>Welcome Alice</p>",
            senderName = "Blueshell",
            senderAddress = "no-reply@esa-blueshell.nl",
            replyToAddress = "board@blueshell.utwente.nl",
        )
        verify(mailSender).send(captor.capture())

        val sent = captor.firstValue
        assertThat(sent.from.single().toString()).contains("no-reply@esa-blueshell.nl")
        assertThat(sent.allRecipients.single().toString()).contains("alice@example.com")
        assertThat(sent.subject).isEqualTo("Welcome")
        assertThat(sent.replyTo.single().toString()).contains("board@blueshell.utwente.nl")
        // The content-type isn't asserted directly: MimeMessageHelper with HTML=true wraps the
        // body in nested multiparts and the precise nesting varies by Jakarta Mail version.
    }

    @Test
    fun `messageId is stable across send and return value`() {
        val captor = argumentCaptor<MimeMessage>()

        val returned = client.send(
            toEmail = "x@y.z",
            toName = "X",
            subject = "S",
            htmlContent = "<p>B</p>",
            senderName = "Blueshell",
            senderAddress = "no-reply@esa-blueshell.nl",
            replyToAddress = "r@y.z",
        )

        verify(mailSender).send(captor.capture())
        assertThat(captor.firstValue.getHeader("Message-ID").single()).isEqualTo(returned)
        assertThat(returned).startsWith("<").endsWith("@esa-blueshell.nl>")
    }
}
