package net.blueshell.api.platform.integration.email.service

import net.blueshell.api.platform.integration.email.EmailTransportClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class EmailDeliveryService(private val emailClient: EmailTransportClient) {

    /**
     * Sends an HTML email via SMTP (blueshell.utwente.nl relay).
     * Returns the Message-ID for outbox correlation.
     * Jobs wrap retries/async.
     */
    fun sendHtmlEmail(
        toEmail: String,
        toName: String,
        subject: String,
        htmlContent: String,
        senderName: String,
        senderAddress: String,
        replyToAddress: String,
    ): String {
        try {
            val messageId = emailClient.send(toEmail, toName, subject, htmlContent, senderName, senderAddress, replyToAddress)
            log.info("Sent email to {} from {} with subject {}", toEmail, senderAddress, subject)
            return messageId
        } catch (e: Exception) {
            log.error("Failed to send email to {} with subject {}: {}", toEmail, subject, e.message, e)
            throw RuntimeException("Failed to send email", e)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(EmailDeliveryService::class.java)
    }
}
