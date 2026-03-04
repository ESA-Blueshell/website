package net.blueshell.api.platform.integration.mock

import jakarta.mail.internet.InternetAddress
import net.blueshell.api.platform.integration.email.EmailTransportClient
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicInteger

/**
 * Test double for EmailTransportClient.
 *
 * Delegates to MockJavaMailSender so all existing MimeMessage-based test assertions
 * (recipient, subject, HTML body extraction) continue to work unchanged.
 * The [simulateSendFailure] path also works transparently via MockJavaMailSender.
 *
 * Active in 'test' and 'dev' profiles.
 */
@Component
@Primary
@Profile("test | dev")
class MockSmtpEmailClient(
    private val mailSender: MockJavaMailSender,
) : EmailTransportClient {

    private val idSequence = AtomicInteger(1)

    override fun send(
        toEmail: String,
        toName: String,
        subject: String,
        htmlContent: String,
        senderName: String,
        senderAddress: String,
        replyToAddress: String,
    ): String {
        val messageId = "<mock-${idSequence.getAndIncrement()}@test.blueshell.net>"
        val message = mailSender.createMimeMessage()
        val helper = MimeMessageHelper(message, true, "UTF-8")
        helper.setFrom(InternetAddress(senderAddress, senderName, "UTF-8"))
        helper.setTo(InternetAddress(toEmail, toName, "UTF-8"))
        helper.setReplyTo(replyToAddress)
        helper.setSubject(subject)
        val plainText = htmlContent.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
        helper.setText(plainText, htmlContent)
        message.setHeader("Message-ID", messageId)
        // Delegates to MockJavaMailSender — honours simulateSendFailure() and populates outbox
        mailSender.send(message)
        log.info("[smtp-mock] captured email: to='{}' subject='{}'", toEmail, subject)
        return messageId
    }

    companion object {
        private val log = LoggerFactory.getLogger(MockSmtpEmailClient::class.java)
    }
}
