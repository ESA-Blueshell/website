package net.blueshell.api.platform.integration.email

import net.blueshell.api.platform.config.ListmonkConfig
import net.blueshell.clients.listmonk.api.TransactionalApi
import net.blueshell.clients.listmonk.model.TransactionalMessage
import net.blueshell.clients.listmonk.model.TransactionalMessageSubscriberMode
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Anti-Corruption Layer (ADR-019) adapter that translates [EmailTransportClient.send]
 * into Listmonk's transactional API (`POST /api/tx`).
 *
 * Listmonk returns only `{"data": true}` with no message ID, so a UUID is
 * generated locally and used as the messageId for outbox tracking.
 *
 * Active in both dev and prod (all non-test profiles).
 */
@Component
@Profile("!test")
class ListmonkEmailClient(
    private val transactionalApi: TransactionalApi,
    @Qualifier(ListmonkConfig.TEMPLATE_ID_BEAN) private val templateId: Int,
) : EmailTransportClient {

    override fun send(
        toEmail: String,
        toName: String,
        subject: String,
        htmlContent: String,
        senderName: String,
        senderAddress: String,
        replyToAddress: String,
    ): String {
        val messageId = "<${UUID.randomUUID()}@listmonk>"

        val message = TransactionalMessage().apply {
            subscriberEmail = toEmail
            subscriberMode = TransactionalMessageSubscriberMode.EXTERNAL
            this.templateId = this@ListmonkEmailClient.templateId
            this.subject = subject
            fromEmail = "$senderName <$senderAddress>"
            // Pass the full HTML body via data.body — the template renders {{ .Tx.Data.body }}
            data = mapOf("body" to htmlContent)
            // Set Reply-To and Message-ID as mail headers
            headers = listOf(
                mapOf("Reply-To" to replyToAddress),
                mapOf("Message-ID" to messageId),
            )
        }

        transactionalApi.transactWithSubscriber(message)

        log.info("Sent email via Listmonk to={} subject='{}' messageId={}", toEmail, subject, messageId)
        return messageId
    }

    companion object {
        private val log = LoggerFactory.getLogger(ListmonkEmailClient::class.java)
    }
}
