package net.blueshell.api.contact.api

import net.blueshell.api.shared.enums.ContactSystem

/**
 * Creates, updates and deletes one contact on one external system (ADR-019). List membership is
 * [ContactListAdapter]'s.
 *
 * One implementation per system, fanned out across by the orchestration services. Every id here
 * is that system's own: domain ids are resolved before a call reaches an adapter.
 */
interface ContactAdapter {
    val system: ContactSystem

    fun createContact(data: ContactData): Long

    /** Updates the contact and returns its current external id (which adapters may rewrite when repairing stale pairing). */
    fun updateContact(externalId: Long, data: ContactData): Long

    fun deleteContact(externalId: Long)
}
