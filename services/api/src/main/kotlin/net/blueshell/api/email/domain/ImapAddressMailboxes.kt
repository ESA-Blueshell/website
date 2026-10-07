package net.blueshell.api.email.domain

import jakarta.mail.Folder
import jakarta.mail.Store
import net.blueshell.api.email.api.AddressMailboxes
import net.blueshell.api.email.persistence.SendingAddressRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/** Opens each read address's mailbox with its kept login, one at a time, closing it after. */
@Component
class ImapAddressMailboxes(
    private val addresses: SendingAddressRepository,
    private val logins: SendingLogins,
) : AddressMailboxes {
    // How a route becomes a connected store. A test swaps in one that hands back a folder of its own.
    internal var storeFor: (ImapRoute) -> Store = { route ->
        sessionFor(route).getStore("imap").also { it.connect(route.host, route.port, route.login.username, route.login.password) }
    }

    override fun eachOpen(
        mode: Int,
        visit: (address: String, folder: Folder) -> Unit,
    ) {
        addresses.findAll().filter { it.imapHost != null }.forEach { address ->
            // One mailbox that cannot be read, or whose login is gone, leaves the others to be read.
            runCatching {
                val login = logins.read(requireNotNull(address.id)) ?: error("no login is kept")
                val route =
                    ImapRoute(
                        requireNotNull(address.imapHost),
                        requireNotNull(address.imapPort),
                        requireNotNull(address.imapSecurity),
                        login,
                    )
                storeFor(route).use { store ->
                    store.getFolder(INBOX).use { folder ->
                        folder.open(mode)
                        visit(address.address, folder)
                    }
                }
            }.onFailure { log.warn("Reading the mailbox of address id={} failed: {}", address.id, it.message) }
        }
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
        const val INBOX = "INBOX"
        private val log = LoggerFactory.getLogger(ImapAddressMailboxes::class.java)
    }
}
