package net.blueshell.api.contact.api

import net.blueshell.api.shared.enums.ContactSystem


/**
 * Lists on one external system and who belongs to them (ADR-019). The contacts themselves are
 * [ContactAdapter]'s.
 *
 * One implementation per system, fanned out across by the orchestration services. Every id here
 * is that system's own: domain ids are resolved before a call reaches an adapter.
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
