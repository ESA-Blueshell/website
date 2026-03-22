package net.blueshell.api.platform.integration.contact.adapter

import net.blueshell.api.shared.enums.ContactSystem


/**
 * Unified domain interface for contact and list synchronization with external systems (ADR-019).
 *
 * Merges the former ContactSyncAdapter and ListSyncAdapter into a single adapter contract.
 * Each implementation handles one external system; the orchestration services fan out across
 * all registered implementations.
 *
 * All IDs are system-specific Longs. The orchestration service resolves domain IDs to system IDs
 * before calling adapter methods.
 */
interface ContactListAdapter {
    val system: ContactSystem

    fun createList(name: String, folderName: String?): Long
    fun addToList(externalUserId: Long, externalListId: Long)
    fun removeFromList(externalUserId: Long, externalListId: Long)
    fun deleteList(externalListId: Long)
}
