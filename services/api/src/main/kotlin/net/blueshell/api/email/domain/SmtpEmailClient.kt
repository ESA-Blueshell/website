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
    // Two sends racing across a rotation may pair the old one with a connect; that send fails
    // and the outbox shows it.
    private val password = RotatingSecret(environment, MAIL_PASSWORD)

    init {
        if (mailSender !is JavaMailSenderImpl) {
            log.warn("{} takes no rotated mail password; one needs a restart", mailSender.javaClass.name)
        }
    }

    override fun send(
        toEmail: String,
        toName: String,
        subject: String,
        htmlContent: String,
        senderName: String,
        senderAddress: String,
        replyToAddress: String,
        threadHeaders: Map<String, String>,
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
        threadHeaders.forEach(mime::setHeader)

        (mailSender as? JavaMailSenderImpl)?.password = password.current()
        mailSender.send(mime)
        return messageId
    }

    companion object {
        private val log = LoggerFactory.getLogger(SmtpEmailClient::class.java)
        private const val MAIL_PASSWORD = "spring.mail.password"

        internal fun generateMessageId(senderAddress: String): String {
            val host = senderAddress.substringAfter('@', missingDelimiterValue = "blueshell.local")
            return "<${UUID.randomUUID()}@$host>"
        }
    }
}
