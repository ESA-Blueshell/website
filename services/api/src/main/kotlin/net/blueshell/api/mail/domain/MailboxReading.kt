package net.blueshell.api.mail.domain

import jakarta.mail.Folder
import jakarta.mail.Message
import jakarta.mail.UIDFolder
import net.blueshell.api.mail.persistence.InboxCursor
import net.blueshell.api.mail.persistence.InboxCursorRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap

/**
 * Reads one open mailbox into the inbox past the last UID taken from it. Nothing in the mailbox is
 * deleted, moved or marked; the bounce poller keeps its own flags on the same messages.
 */
@Component
class MailboxReading(
    private val intake: InboxIntake,
    private val cursors: InboxCursorRepository,
    private val clock: Clock,
) {
    // Per pod, by mailbox and UID; a restart gives a failing message its tries again.
    private val failures = ConcurrentHashMap<String, Int>()

    /**
     * Reads [mailbox], already open, keeping its place under [cursorKey]. [address] is the sending
     * address the mailbox belongs to, none for the catch-all; it names a message without a Message-ID too.
     * A backlog is read over several polls, each saving its place message by message.
     */
    fun read(
        mailbox: Folder,
        cursorKey: String,
        address: String? = null,
    ) {
        val uids = mailbox as UIDFolder
        val validity = uids.uidValidity
        val cursor = cursors.findById(cursorKey).orElse(null)?.takeIf { it.uidValidity == validity } ?: InboxCursor(cursorKey, validity, 0)
        val fresh = uids.getMessagesByUID(cursor.lastUid + 1, UIDFolder.MAXUID)
        for (message in fresh.take(PER_POLL)) {
            val uid = uids.getUID(message)
            if (uid <= cursor.lastUid) continue
            if (!kept(message, "<uid-$validity-$uid@${address ?: "inbox.local"}>", "$cursorKey#$uid", address)) return
            cursor.lastUid = uid
            cursors.save(cursor)
        }
    }

    // A message that cannot be read is passed. One that fails to be kept, as when the database is
    // away, is tried again next poll, and passed only after that has failed three times, so one
    // message never holds up the ones after it.
    private fun kept(
        message: Message,
        fallbackId: String,
        where: String,
        address: String?,
    ): Boolean =
        try {
            InboxMessageParser.parse(message, fallbackId, clock.instant())?.let { intake.take(it, address) }
            true
        } catch (e: UnreadableMessage) {
            log.warn("Inbox passed message {}: {}", where, e.message)
            true
        } catch (e: Exception) {
            val tries = failures.merge(where, 1, Int::plus) ?: 1
            log.error("Inbox could not keep message {} (try {}): {}", where, tries, e.javaClass.simpleName)
            (tries >= TRIES).also { if (it) failures.remove(where) }
        }

    private companion object {
        private const val PER_POLL = 200
        private const val TRIES = 3
        private val log = LoggerFactory.getLogger(MailboxReading::class.java)
    }
}
