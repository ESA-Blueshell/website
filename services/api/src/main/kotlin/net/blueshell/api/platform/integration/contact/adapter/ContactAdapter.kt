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
interface ContactAdapter {
    val system: ContactSystem

    fun createContact(data: ContactData): Long

    /**
     * Updates the contact and returns its current external id. The returned id is
     * usually the same one passed in, but adapters may repair stale pairing on
     * the fly (e.g. when the external contact was deleted) and return a new id;
     * the orchestration layer persists the result so the mapping stays correct.
     */
    fun updateContact(externalId: Long, data: ContactData): Long

    fun deleteContact(externalId: Long)
}
