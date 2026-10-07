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

    // The session a poll connects through. A test swaps in one whose store records the login.
    internal var sessionFor: (String) -> Session = { protocol ->
        Session.getInstance(Properties().apply { setProperty("mail.store.protocol", protocol) })
    }

    @Scheduled(fixedDelayString = "\${email.bounce.poll-interval-ms:300000}")
    fun pollBounces() {
        if (host.isBlank() || username.isBlank()) {
            log.debug("IMAP bounce poller skipped — host/username not configured")
            return
        }
        val protocol = if (useTls) "imaps" else "imap"
        val session = sessionFor(protocol)
        try {
            session.getStore(protocol).use { store ->
                store.connect(host, port, username, password.current())
                store.getFolder(folder).use { mailbox ->
                    mailbox.open(Folder.READ_WRITE)
                    takeBounces(emailService, mailbox)
                }
            }
        } catch (e: Exception) {
            log.error("IMAP bounce poll failed: {}", e.message, e)
        }
    }

    internal fun processOne(message: Message) = takeBounce(emailService, message)

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

/** Takes every unseen delivery report in [mailbox], open for writing, marking each seen once read. */
internal fun takeBounces(
    emailService: EmailService,
    mailbox: Folder,
) {
    mailbox.search(FlagTerm(Flags(Flags.Flag.SEEN), false)).forEach { takeBounce(emailService, it) }
}

/** Marks the outbox record a delivery report names bounced; anything else is only marked seen. */
internal fun takeBounce(
    emailService: EmailService,
    message: Message,
) {
    try {
        val parsed = BounceMessageParser.parse(message)
        if (parsed == null) {
            bounceLog.debug("Skipping a message that is no delivery report")
            message.setFlag(Flags.Flag.SEEN, true)
            return
        }
        val outbox = emailService.findByMessageId(parsed.originalMessageId)
        if (outbox == null) {
            bounceLog.info("Bounce for a message the outbox does not hold; marking it seen anyway")
        } else {
            emailService.markBounced(outbox, parsed.describe())
            bounceLog.info("Marked email id={} as BOUNCED", outbox.id)
        }
        message.setFlag(Flags.Flag.SEEN, true)
    } catch (e: Exception) {
        bounceLog.error("Failed to process bounce message: {}", e.message, e)
    }
}

private val bounceLog = LoggerFactory.getLogger(ImapBouncePollingService::class.java)
