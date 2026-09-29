package net.blueshell.api.email.domain

import jakarta.mail.internet.InternetAddress
import net.blueshell.api.shared.credentials.Credentials
import net.blueshell.api.shared.credentials.RotatingSecret
import net.blueshell.api.shared.credentials.WhenCredentialsSet
import org.slf4j.LoggerFactory
import org.springframework.core.env.Environment
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.JavaMailSenderImpl
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Sends transactional HTML email through Spring's [JavaMailSender] (SMTP).
 *
 * Generates the Message-ID itself, so the same value is stored on the outbox row and emitted as
 * the MIME header, giving the bounce poller a stable identifier to match DSNs against. Active
 * in every non-test profile; tests use `InMemoryEmailClient`.
 */
@Component
@WhenCredentialsSet(Credentials.SMTP_HOST)
class SmtpEmailClient(
    private val mailSender: JavaMailSender,
    environment: Environment,
) : EmailTransportClient {
    // Boot builds the sender once; its password is set per send, so a rotated one is used next.
    private val password = RotatingSecret(environment, "spring.mail.password")

    override fun send(
        toEmail: String,
        toName: String,
        subject: String,
        htmlContent: String,
        senderName: String,
        senderAddress: String,
        replyToAddress: String,
    ): String {
        val messageId = generateMessageId(senderAddress)
        val mime = mailSender.createMimeMessage()
        // Set Message-ID on the underlying MimeMessage before MimeMessageHelper
        // writes any other headers; otherwise Jakarta Mail synthesises its own
        // at send time and our outbox correlation key is lost.
        mime.setHeader("Message-ID", messageId)

        val helper = MimeMessageHelper(mime, true, Charsets.UTF_8.name())
        helper.setFrom(InternetAddress(senderAddress, senderName))
        helper.setTo(InternetAddress(toEmail, toName))
        helper.setReplyTo(replyToAddress)
        helper.setSubject(subject)
        helper.setText(htmlContent, true)

        // Re-assert — MimeMessageHelper's setters can rewrite headers.
        mime.setHeader("Message-ID", messageId)

        (mailSender as? JavaMailSenderImpl)?.password = password.current()
        mailSender.send(mime)
        log.info("Sent email via SMTP to={} subject='{}' messageId={}", toEmail, subject, messageId)
        return messageId
    }

    companion object {
        private val log = LoggerFactory.getLogger(SmtpEmailClient::class.java)

        internal fun generateMessageId(senderAddress: String): String {
            val host = senderAddress.substringAfter('@', missingDelimiterValue = "blueshell.local")
            return "<${UUID.randomUUID()}@$host>"
        }
    }
}
