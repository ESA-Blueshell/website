package net.blueshell.api.platform.integration.email.adapter

/**
 * Abstraction over email transport.
 * Implemented by [SmtpEmailClient] (via Spring's [org.springframework.mail.javamail.JavaMailSender])
 * in dev/prod and [net.blueshell.api.platform.integration.mock.InMemoryEmailClient] in tests.
 */
interface EmailTransportClient {
    /**
     * Send a transactional HTML email. Returns a messageId for outbox tracking.
     */
    fun send(
        toEmail: String,
        toName: String,
        subject: String,
        htmlContent: String,
        senderName: String,
        senderAddress: String,
        replyToAddress: String,
    ): String
}
