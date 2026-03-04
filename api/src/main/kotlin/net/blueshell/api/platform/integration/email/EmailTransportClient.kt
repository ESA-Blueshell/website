package net.blueshell.api.platform.integration.email

/**
 * Abstraction over email transport.
 * Implemented by [ListmonkEmailClient] (via Listmonk transactional API) in dev/prod
 * and [MockListmonkEmailClient] in tests.
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
