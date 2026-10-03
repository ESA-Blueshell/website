package net.blueshell.api.mail.domain

import jakarta.mail.Folder
import jakarta.mail.Session
import jakarta.mail.Store
import jakarta.mail.UIDFolder
import net.blueshell.api.mail.persistence.InboxCursor
import net.blueshell.api.mail.persistence.InboxCursorRepository
import net.blueshell.api.shared.credentials.Credentials
import net.blueshell.api.shared.credentials.RotatingSecret
import net.blueshell.api.shared.credentials.WhenCredentialsSet
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Clock
import java.util.Properties

/**
 * Reads the catch-all mailbox into the inbox. The folder is opened read-only and read past the last
 * UID taken, so nothing in the mailbox is deleted, moved or marked; the bounce poller keeps its own
 * flags on the same messages.
 */
@Service
@WhenCredentialsSet(Credentials.IMAP_HOST, Credentials.IMAP_USERNAME, Credentials.IMAP_PASSWORD)
class InboxPoller(
    private val intake: InboxIntake,
    private val cursors: InboxCursorRepository,
    private val clock: Clock,
    @param:Value($$"${email.inbox.imap.host:${email.bounce.imap.host:}}") private val host: String,
    @param:Value($$"${email.inbox.imap.port:${email.bounce.imap.port:993}}") private val port: Int,
    @param:Value($$"${email.inbox.imap.username:${email.bounce.imap.username:}}") private val username: String,
    @param:Value($$"${email.inbox.imap.folder:INBOX}") private val folder: String,
    @param:Value($$"${email.inbox.imap.tls:${email.bounce.imap.tls:true}}") private val useTls: Boolean,
    environment: Environment,
) {
    // The catch-all is the bounce mailbox's account unless one of its own is set.
    private val password = RotatingSecret(environment, Credentials.IMAP_PASSWORD)

    internal var sessionFor: (String) -> Session = { protocol ->
        Session.getInstance(Properties().apply { setProperty("mail.store.protocol", protocol) })
    }

    @Scheduled(fixedDelayString = "\${email.inbox.poll-interval-ms:120000}")
    fun poll() {
        val protocol = if (useTls) "imaps" else "imap"
        try {
            sessionFor(protocol).getStore(protocol).use { store ->
                store.connect(host, port, username, password.current())
                store.getFolder(folder).use { mailbox -> read(mailbox) }
            }
        } catch (e: Exception) {
            log.error("Inbox poll failed: {}", e.message, e)
        }
    }

    internal fun read(mailbox: Folder) {
        mailbox.open(Folder.READ_ONLY)
        val uids = mailbox as UIDFolder
        val validity = uids.uidValidity
        val cursor = cursors.findById(folder).orElse(null)?.takeIf { it.uidValidity == validity } ?: InboxCursor(folder, validity, 0)
        val fresh = uids.getMessagesByUID(cursor.lastUid + 1, UIDFolder.MAXUID)
        for (message in fresh) {
            val uid = uids.getUID(message)
            if (uid <= cursor.lastUid) continue
            InboxMessageParser.parse(message, "<uid-$validity-$uid@inbox.local>", clock.instant())?.let(intake::take)
            cursor.lastUid = uid
        }
        cursors.save(cursor)
    }

    private inline fun <R> Store.use(block: (Store) -> R): R =
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

    private companion object {
        private val log = LoggerFactory.getLogger(InboxPoller::class.java)
    }
}
