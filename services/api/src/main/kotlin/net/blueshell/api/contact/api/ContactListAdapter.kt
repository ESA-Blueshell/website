package net.blueshell.api.contact.api

import net.blueshell.api.shared.enums.ContactSystem


/**
 * Lists on one external system and who belongs to them (ADR-019). The contacts themselves are
 * [ContactAdapter]'s.
 *
 * Each implementation carries the [system] it speaks for, and a caller selects the one it wants
 * by that tag rather than calling every implementation. Every id here is the external system's
 * own: a domain id is resolved to an external one before the call reaches an adapter.
 */
interface ContactListAdapter {
    val system: ContactSystem

    fun createList(name: String, folderName: String?): Long
    fun addToList(externalUserId: Long, externalListId: Long)
    fun removeFromList(externalUserId: Long, externalListId: Long)
    fun deleteList(externalListId: Long)

    /**
     * Move a list into a folder. Brevo takes a name or a folder in one call but not both,
     * so this only ever moves.
     */
    fun moveList(externalListId: Long, folderId: Long): Unit =
        throw UnsupportedOperationException("This adapter cannot move a list between folders")

    /** Every folder, as id to name. */
    fun listFolders(): Map<Long, String> = emptyMap()

    /** Lists all members currently present in the given external list. */
    fun listMembers(externalListId: Long): List<ContactListMember>
}

/** One member as the external system knows them: a native numeric id and optional email label. */
data class ContactListMember(val externalUserId: Long, val email: String?)
