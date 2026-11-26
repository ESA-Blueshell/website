package net.blueshell.api.email.domain

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.mail.MessagingException
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.MimeMessage
import net.blueshell.api.shared.job.NonRetryableJobException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.mail.javamail.JavaMailSenderImpl
import org.springframework.mock.env.MockEnvironment
import java.util.Properties

class SmtpEmailClientSessionTest {
    private val transport = mockk<Transport>(relaxed = true) { every { isConnected } returns true }
    private val transports = ArrayDeque(listOf(transport))
    private val sender =
        mockk<JavaMailSenderImpl>(relaxed = true) {
            every { createMimeMessage() } answers { MimeMessage(Session.getInstance(Properties())) }
            every { host } returns "smtp.test"
            every { port } returns 587
            every { username } returns "user"
        }
    private val smtpSession = SmtpSession()
    private val client =
        object : SmtpEmailClient(sender, MockEnvironment().withProperty("spring.mail.password", "secret"), smtpSession) {
            override fun newTransport(impl: JavaMailSenderImpl): Transport = transports.removeFirst()
        }

    @Test
    fun `two sends inside a session share one connected transport`() {
        smtpSession.open {
            send()
            send()
        }

        verify(exactly = 1) { transport.connect("smtp.test", 587, "user", "secret") }
        verify(exactly = 2) { transport.sendMessage(any(), any()) }
        verify(exactly = 1) { transport.close() }
    }

    @Test
    fun `a failed send closes the transport and the next send reconnects`() {
        val replacement = mockk<Transport>(relaxed = true) { every { isConnected } returns true }
        transports.addLast(replacement)
        every { transport.sendMessage(any(), any()) } throws MessagingException("421 lost connection")

        smtpSession.open {
            assertThatThrownBy { send() }.isInstanceOf(MessagingException::class.java)
            send()
        }

        verify { transport.close() }
        verify { replacement.connect("smtp.test", 587, "user", "secret") }
        verify { replacement.sendMessage(any(), any()) }
    }

    @Test
    fun `a 550 address refusal becomes a non-retryable job failure`() {
        every { sender.send(any<MimeMessage>()) } throws SMTPAddressFailedException(550)

        assertThatThrownBy { send() }
            .isInstanceOf(NonRetryableJobException::class.java)
            .hasCauseInstanceOf(SMTPAddressFailedException::class.java)
    }

    @Test
    fun `a new transport comes from the sender's mail session`() {
        val real = JavaMailSenderImpl()
        val plain = SmtpEmailClient(real, MockEnvironment().withProperty("spring.mail.password", "secret"), SmtpSession())

        assertThat(plain.newTransport(real).isConnected).isFalse()
    }

    private fun send() {
        client.send("to@example.com", "To", "Hi", "<p>Hi</p>", "Blueshell", "no-reply@esa-blueshell.nl", "board@example.com")
    }

    /** Angus' exception is runtime-only behind jakarta.mail's API; its name and code are what we read. */
    private class SMTPAddressFailedException(
        code: Int,
    ) : MessagingException("550 rejected") {
        private val returnCode = code

        fun getReturnCode(): Int = returnCode
    }
}
