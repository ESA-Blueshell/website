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

/**
 * Unit tests for [SmtpEmailClient]. Uses a real [MimeMessage] returned from
 * the mocked [JavaMailSender] so the helper's effects can be asserted on
 * actual MIME headers.
 */
class SmtpEmailClientTest {

    private val session: Session = Session.getInstance(Properties())
    private val mailSender: JavaMailSender = mock {
        on { createMimeMessage() }.thenAnswer { MimeMessage(session) }
    }

    private val client = SmtpEmailClient(mailSender)

    @Test
    fun `send writes a multipart MIME with HTML body, from, to, reply-to, subject`() {
        val captured = argumentCaptor<MimeMessage>()

        val messageId = client.send(
            toEmail = "alice@example.org",
            toName = "Alice",
            subject = "Welcome",
            htmlContent = "<p>Hi Alice</p>",
            senderName = "Blueshell",
            senderAddress = "no-reply@blueshell.example",
            replyToAddress = "sitecie@blueshell.example",
        )

        verify(mailSender).send(captured.capture())
        val sent = captured.firstValue

        assertThat(sent.allRecipients).hasSize(1)
        assertThat(sent.allRecipients[0].toString()).contains("alice@example.org")
        assertThat(sent.from[0].toString()).contains("no-reply@blueshell.example")
        assertThat(sent.replyTo[0].toString()).contains("sitecie@blueshell.example")
        assertThat(sent.subject).isEqualTo("Welcome")
        assertThat(sent.getHeader("Message-ID")[0]).isEqualTo(messageId)
        assertThat(messageId).matches(Regex("<[0-9a-fA-F-]+@blueshell\\.example>").toPattern())

        assertThat(extractBody(sent)).contains("<p>Hi Alice</p>")
    }

    private fun extractBody(part: jakarta.mail.Part): String {
        val content = part.content
        return when (content) {
            is String -> content
            is jakarta.mail.Multipart -> (0 until content.count)
                .joinToString("\n") { extractBody(content.getBodyPart(it)) }
            else -> content.toString()
        }
    }

    @Test
    fun `messageId host falls back when sender has no at-sign`() {
        whenever(mailSender.createMimeMessage()).thenAnswer { MimeMessage(session) }

        val messageId = client.send(
            toEmail = "bob@example.org",
            toName = "Bob",
            subject = "Hi",
            htmlContent = "<p>hi</p>",
            senderName = "Blueshell",
            senderAddress = "no-reply",
            replyToAddress = "reply@example.org",
        )

        assertThat(messageId).endsWith("@blueshell.local>")
        verify(mailSender).send(any<MimeMessage>())
    }
}
