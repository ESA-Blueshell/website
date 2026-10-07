package net.blueshell.api.mail.domain

import jakarta.mail.Folder
import net.blueshell.api.email.api.AddressMailboxes
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

/** Reads each sending address's own mailbox into the inbox, read-only, so its replies arrive there too. */
@Service
class AddressInboxPoller(
    private val mailboxes: AddressMailboxes,
    private val reading: MailboxReading,
) {
    @Scheduled(fixedDelayString = "\${email.inbox.poll-interval-ms:120000}")
    fun poll() = mailboxes.eachOpen(Folder.READ_ONLY) { address, folder -> reading.read(folder, cursorKeyOf(address), address) }

    internal companion object {
        // Apart from the catch-all's, which is its folder's name.
        fun cursorKeyOf(address: String) = "address:${address.lowercase()}"
    }
}
