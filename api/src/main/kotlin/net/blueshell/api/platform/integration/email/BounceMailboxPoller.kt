package net.blueshell.api.platform.integration.email

import io.micrometer.core.instrument.MeterRegistry
import net.blueshell.api.platform.config.ImapBounceProperties
import net.blueshell.api.platform.integration.email.application.service.EmailService
import net.blueshell.api.shared.enums.EmailDeliveryStatus
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Periodically polls the sender's IMAP mailbox for bounce/DSN messages,
 * parses them, and updates the corresponding [Email] records to BOUNCED status.
 *
 * Non-bounce messages (auto-replies, OOO, regular mail) are marked as read and skipped.
 * Processing failures for individual messages do not abort the batch.
 */
@Component
class BounceMailboxPoller(
    private val imapClient: ImapBounceClient,
    private val parser: BounceMessageParser,
    private val emailService: EmailService,
    private val properties: ImapBounceProperties,
    private val meterRegistry: MeterRegistry,
) {
    private val logger = LoggerFactory.getLogger(BounceMailboxPoller::class.java)

    @Scheduled(fixedDelayString = "\${app.email.bounce-poll-interval-ms:300000}")
    fun poll() {
        if (!properties.enabled) return

        val messages = try {
            imapClient.fetchUnseenMessages()
        } catch (e: Exception) {
            logger.error("IMAP bounce poll failed: {}", e.message, e)
            meterRegistry.counter("email.bounce.imap.error").increment()
            return
        }

        if (messages.isEmpty()) return
        logger.info("Fetched {} unseen IMAP messages for bounce processing", messages.size)

        for (message in messages) {
            try {
                processMessage(message)
            } catch (e: Exception) {
                logger.error("Failed to process IMAP message uid={}: {}", message.uid, e.message, e)
            }
        }
    }

    private fun processMessage(message: RawBounceMessage) {
        val bounce = parser.parse(message)
        if (bounce == null) {
            logger.debug("Non-bounce message, marking seen: uid={} subject='{}'", message.uid, message.subject)
            markSeen(message)
            return
        }

        val email = emailService.findByMessageId(bounce.originalMessageId)
        if (email == null) {
            logger.info("Bounce for unknown Message-ID: {}, subject='{}'", bounce.originalMessageId, bounce.rawSubject)
            meterRegistry.counter("email.bounce.unmatched").increment()
            markSeen(message)
            return
        }

        if (email.deliveryStatus == EmailDeliveryStatus.BOUNCED) {
            logger.debug("Email already bounced: id={}, messageId={}", email.id, email.messageId)
            markSeen(message)
            return
        }

        val reason = buildReason(bounce)
        emailService.markBounced(email, reason)
        meterRegistry.counter("email.bounce.matched").increment()
        logger.info(
            "Marked email as bounced: id={}, messageId={}, status={}, action={}",
            email.id, email.messageId, bounce.status, bounce.action
        )
        markSeen(message)
    }

    private fun markSeen(message: RawBounceMessage) {
        try {
            imapClient.markSeen(message)
        } catch (e: Exception) {
            logger.warn("Failed to mark IMAP message uid={} as seen: {}", message.uid, e.message, e)
        }
    }

    private fun buildReason(bounce: ParsedBounce): String {
        val parts = mutableListOf<String>()
        bounce.status?.let { parts.add("status=$it") }
        parts.add("action=${bounce.action}")
        bounce.diagnosticCode?.let { parts.add("diagnostic=$it") }
        parts.add("subject='${bounce.rawSubject}'")
        return parts.joinToString("; ")
    }
}
