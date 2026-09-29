package net.blueshell.api.email.domain

import jakarta.mail.Flags
import jakarta.mail.Folder
import jakarta.mail.Message
import jakarta.mail.Session
import jakarta.mail.search.FlagTerm
import net.blueshell.api.shared.credentials.Credentials
import net.blueshell.api.shared.credentials.RotatingSecret
import net.blueshell.api.shared.credentials.WhenCredentialsSet
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.Properties

/**
 * Scans the IMAP bounce mailbox for new DSNs, looks each up by `Message-ID` on the outbox and
 * marks the matching record bounced.
 *
 * Runs where the bounce mailbox's host, username and password are all set, so dev and test JVMs
 * stay quiet, and is paced by `email.bounce.poll-interval-ms`. Parsing lives in [BounceMessageParser], where it
 * can be exercised on its own.
 */
@Service
@WhenCredentialsSet(Credentials.IMAP_HOST, Credentials.IMAP_USERNAME, Credentials.IMAP_PASSWORD)
class ImapBouncePollingService(
    private val emailService: EmailService,
    @param:Value($$"${email.bounce.imap.host:}") private val host: String,
    @param:Value($$"${email.bounce.imap.port:993}") private val port: Int,
    @param:Value($$"${email.bounce.imap.username:}") private val username: String,
    environment: Environment,
    @param:Value($$"${email.bounce.imap.folder:INBOX}") private val folder: String,
    @param:Value($$"${email.bounce.imap.tls:true}") private val useTls: Boolean,
) {
    // Read at each connect, so a rotated password is used on the next poll.
    private val password = RotatingSecret(environment, Credentials.IMAP_PASSWORD)

    @Scheduled(fixedDelayString = "\${email.bounce.poll-interval-ms:300000}")
    fun pollBounces() {
        if (host.isBlank() || username.isBlank()) {
            log.debug("IMAP bounce poller skipped — host/username not configured")
            return
        }
        val protocol = if (useTls) "imaps" else "imap"
        val session =
            Session.getInstance(
                Properties().apply {
                    setProperty("mail.store.protocol", protocol)
                },
            )
        try {
            session.getStore(protocol).use { store ->
                store.connect(host, port, username, password.current())
                store.getFolder(folder).use { mailbox ->
                    mailbox.open(Folder.READ_WRITE)
                    val unseen = mailbox.search(FlagTerm(Flags(Flags.Flag.SEEN), false))
                    log.debug("IMAP bounce poller: {} unseen messages in {}", unseen.size, folder)
                    unseen.forEach { processOne(it) }
                }
            }
        } catch (e: Exception) {
            log.error("IMAP bounce poll failed: {}", e.message, e)
        }
    }

    private fun processOne(message: Message) {
        try {
            val parsed = BounceMessageParser.parse(message)
            if (parsed == null) {
                log.debug("Skipping non-DSN message subject='{}'", message.subject)
                message.setFlag(Flags.Flag.SEEN, true)
                return
            }
            val outbox = emailService.findByMessageId(parsed.originalMessageId)
            if (outbox == null) {
                log.info(
                    "Bounce for unknown message id={} recipient={} — marking seen anyway",
                    parsed.originalMessageId,
                    parsed.recipient,
                )
            } else {
                emailService.markBounced(outbox, parsed.describe())
                log.info(
                    "Marked email id={} as BOUNCED (messageId={} recipient={})",
                    outbox.id,
                    parsed.originalMessageId,
                    parsed.recipient,
                )
            }
            message.setFlag(Flags.Flag.SEEN, true)
        } catch (e: Exception) {
            log.error("Failed to process bounce message: {}", e.message, e)
        }
    }

    private inline fun <R> jakarta.mail.Store.use(block: (jakarta.mail.Store) -> R): R =
        try {
            block(this)
        } finally {
            runCatching { if (isConnected) close() }
        }

    private inline fun <R> Folder.use(block: (Folder) -> R): R =
        try {
            block(this)
        } finally {
            runCatching { if (isOpen) close(false) }
        }

    companion object {
        private val log = LoggerFactory.getLogger(ImapBouncePollingService::class.java)
    }
}
