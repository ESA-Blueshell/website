package net.blueshell.api.contact.api

import net.blueshell.api.shared.enums.ContactSystem

/**
 * Creates, updates and deletes one contact on one external system (ADR-019). List membership is
 * [ContactListAdapter]'s.
 *
 * Each implementation carries the [system] it speaks for, and a caller selects the one it wants
 * by that tag rather than calling every implementation. Every id here is the external system's
 * own: a domain id is resolved to an external one before the call reaches an adapter.
 */
interface ContactAdapter {
    val system: ContactSystem

    fun createContact(data: ContactData): Long

    /** Updates the contact and returns its current external id (which adapters may rewrite when repairing stale pairing). */
    fun updateContact(externalId: Long, data: ContactData): Long

    fun deleteContact(externalId: Long)
}
