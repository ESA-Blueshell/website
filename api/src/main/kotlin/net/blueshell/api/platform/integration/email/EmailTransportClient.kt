package net.blueshell.api.platform.integration.email

/**
 * Abstraction over email transport.
 * Implemented by SmtpEmailClient in production and MockSmtpEmailClient in test/dev.
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
