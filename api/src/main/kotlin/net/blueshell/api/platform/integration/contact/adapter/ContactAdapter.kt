package net.blueshell.api.platform.integration.contact.adapter

import net.blueshell.api.domain.user.application.contact.ContactData
import net.blueshell.api.domain.user.application.contact.ContactSystem

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
    fun updateContact(externalId: Long, data: ContactData)
    fun deleteContact(externalId: Long)
}
