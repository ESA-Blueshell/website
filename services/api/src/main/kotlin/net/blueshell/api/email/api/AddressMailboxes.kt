package net.blueshell.api.email.api

import jakarta.mail.Folder

/**
 * The mailboxes of the addresses the site reads (api ADR-039), opened one at a time with the login
 * kept in Vault, so a reader never handles a login itself.
 */
fun interface AddressMailboxes {
    /**
     * Opens each read address's INBOX in [mode] (`Folder.READ_ONLY` or `Folder.READ_WRITE`) and hands
     * it to [visit] with the address. One that cannot be opened is skipped, and the rest are read.
     */
    fun eachOpen(
        mode: Int,
        visit: (address: String, folder: Folder) -> Unit,
    )
}
