package net.blueshell.api.mail.domain

import jakarta.mail.Folder
import jakarta.mail.UIDFolder
import net.blueshell.api.mail.persistence.InboxCursor
import net.blueshell.api.mail.persistence.InboxCursorRepository
import org.springframework.stereotype.Component
import java.time.Clock

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
    /**
     * Reads [mailbox], already open, keeping its place under [cursorKey]. [address] is the sending
     * address the mailbox belongs to, none for the catch-all; it names a message without a Message-ID too.
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
        for (message in fresh) {
            val uid = uids.getUID(message)
            if (uid <= cursor.lastUid) continue
            val fallback = "<uid-$validity-$uid@${address ?: "inbox.local"}>"
            InboxMessageParser.parse(message, fallback, clock.instant())?.let { intake.take(it, address) }
            cursor.lastUid = uid
        }
        cursors.save(cursor)
    }
}
