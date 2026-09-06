package net.blueshell.api.contact.api

import net.blueshell.api.shared.enums.TargetSystem


/**
 * Lists on one external system and who belongs to them (ADR-019). The contacts themselves are
 * [ContactAdapter]'s.
 *
 * Each implementation carries the [system] it speaks for, and a caller selects the one it wants
 * by that tag rather than calling every implementation. Every id here is the external system's
 * own: a domain id is resolved to an external one before the call reaches an adapter.
 */
interface ContactListAdapter {
    val system: TargetSystem

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

    /**
     * Every list on the system, whoever it belongs to: the catalogue an operator picks from.
     * A system that cannot be browsed answers with nothing rather than failing.
     */
    fun listAll(): List<ContactListRef> = emptyList()

    /** Lists all members currently present in the given external list. */
    fun listMembers(externalListId: Long): List<ContactListMember>
}

/** One member as the external system knows them: a native numeric id and optional email label. */
data class ContactListMember(val externalUserId: Long, val email: String?)

/**
 * One list as the external system knows it. [folderId] keys into [ContactListAdapter.listFolders];
 * it is unresolved here so a caller reads the folders once rather than per list.
 */
data class ContactListRef(
    val externalListId: Long,
    val name: String,
    val folderId: Long?,
    val memberCount: Long?,
)
