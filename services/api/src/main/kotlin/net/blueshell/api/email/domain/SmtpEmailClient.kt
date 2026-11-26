package net.blueshell.api.email.domain

import jakarta.mail.SendFailedException
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import net.blueshell.api.shared.credentials.Credentials
import net.blueshell.api.shared.credentials.RotatingSecret
import net.blueshell.api.shared.credentials.WhenCredentialsSet
import net.blueshell.api.shared.job.NonRetryableJobException
import org.slf4j.LoggerFactory
import org.springframework.core.env.Environment
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.JavaMailSenderImpl
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Component
import java.util.Date
import java.util.UUID

/**
 * Sends transactional HTML email through Spring's [JavaMailSender] (SMTP), reusing one connection
 * while an [SmtpSession] is open on this thread. Generates the Message-ID itself, so the same
 * value is stored on the outbox row and emitted as the MIME header, giving the bounce poller a
 * stable identifier to match DSNs against. Active where a relay host is set; tests use
 * `InMemoryEmailClient`.
 */
@Component
@WhenCredentialsSet(Credentials.SMTP_HOST)
class SmtpEmailClient(
    private val mailSender: JavaMailSender,
    environment: Environment,
    private val session: SmtpSession = SmtpSession(),
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
    ): String {
        val messageId = generateMessageId(senderAddress)
        val mime = createMessage(toEmail, toName, subject, htmlContent, senderName, senderAddress, replyToAddress, messageId)

        (mailSender as? JavaMailSenderImpl)?.password = password.current()
        try {
            val transport =
                session.connection(
                    connect = ::connectTransport,
                    connected = Transport::isConnected,
                    close = { held -> held.close() },
                )
            if (transport == null) mailSender.send(mime) else sendOver(transport, mime, messageId)
        } catch (ex: Exception) {
            throw classified(ex)
        }
        return messageId
    }

    private fun sendOver(
        transport: Transport,
        mime: MimeMessage,
        messageId: String,
    ) {
        // JavaMailSenderImpl.send does both of these; a raw Transport does neither.
        if (mime.sentDate == null) mime.sentDate = Date()
        mime.saveChanges()
        // saveChanges may synthesise an id; the outbox row carries ours.
        mime.setHeader("Message-ID", messageId)
        val delivered = runCatching { transport.sendMessage(mime, mime.allRecipients) }
        // A failed send may leave the connection mid-command, so the next send starts afresh.
        if (delivered.isFailure) session.discard() else session.sent()
        delivered.getOrThrow()
    }

    private fun createMessage(
        toEmail: String,
        toName: String,
        subject: String,
        htmlContent: String,
        senderName: String,
        senderAddress: String,
        replyToAddress: String,
        messageId: String,
    ): MimeMessage {
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
        return mime
    }

    private fun connectTransport(): Transport {
        val impl = mailSender as JavaMailSenderImpl
        return newTransport(impl).also { transport ->
            transport.connect(impl.host, impl.port, impl.username, password.current())
        }
    }

    internal open fun newTransport(impl: JavaMailSenderImpl): Transport = impl.session.getTransport("smtp")

    private fun classified(ex: Exception): Exception =
        if (ex !is NonRetryableJobException && permanentFailure(ex)) {
            NonRetryableJobException("Permanent SMTP failure", ex)
        } else {
            ex
        }

    private fun permanentFailure(failure: Throwable): Boolean {
        var current: Throwable? = failure
        while (current != null) {
            if (current is SendFailedException && !current.invalidAddresses.isNullOrEmpty()) return true
            // Angus' SMTP exceptions sit behind jakarta.mail's API at compile time, so the class is
            // recognised by name; a 5xx refusal will not change on a retry.
            if (current.javaClass.simpleName in SMTP_FAILURE_TYPES && returnCodeOf(current) in MIN_PERMANENT_CODE..MAX_PERMANENT_CODE) {
                return true
            }
            current = current.cause?.takeIf { it !== current }
        }
        return false
    }

    private fun returnCodeOf(failure: Throwable): Int? =
        runCatching {
            failure.javaClass.methods
                .firstOrNull { it.name == RETURN_CODE_METHOD && it.parameterCount == 0 }
                ?.invoke(failure) as? Int
        }.getOrNull()

    companion object {
        private val log = LoggerFactory.getLogger(SmtpEmailClient::class.java)
        private const val MAIL_PASSWORD = "spring.mail.password"
        private const val RETURN_CODE_METHOD = "getReturnCode"
        private const val MIN_PERMANENT_CODE = 500
        private const val MAX_PERMANENT_CODE = 599
        private val SMTP_FAILURE_TYPES = setOf("SMTPAddressFailedException", "SMTPSendFailedException")

        internal fun generateMessageId(senderAddress: String): String {
            val host = senderAddress.substringAfter('@', missingDelimiterValue = "blueshell.local")
            return "<${UUID.randomUUID()}@$host>"
        }
    }
}
