package net.blueshell.api.platform.integration.email.adapter

/**
 * Abstraction over outbound email transport.
 *
 * Implemented by [SmtpEmailClient] in dev / prod (delegates to Spring's
 * `JavaMailSender`) and by
 * [net.blueshell.api.platform.integration.mock.InMemoryEmailClient] in tests.
 */
interface EmailTransportClient {
    /** Send a transactional HTML email. Returns the messageId stamped on the outbox row. */
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
