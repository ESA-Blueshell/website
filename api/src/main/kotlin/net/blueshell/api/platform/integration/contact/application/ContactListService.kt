package net.blueshell.api.platform.integration.contact.application

import net.blueshell.api.domain.user.application.contact.ContactSystem
import net.blueshell.api.domain.user.application.contact.ListSyncAdapter
import net.blueshell.api.platform.integration.contact.persistence.BrevoList
import net.blueshell.api.platform.integration.contact.persistence.ContactList
import net.blueshell.api.platform.integration.contact.persistence.ContactListMembership
import net.blueshell.api.platform.integration.contact.persistence.ListmonkList
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactListMembershipRepository
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactListRepository
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Orchestrates contact list management across all registered [ListSyncAdapter] implementations.
 *
 * Responsibilities:
 * - `findOrCreateList`: idempotent list creation — reuses an existing [ContactList] if found by name.
 * - `addContactToList`: creates a [ContactListMembership] and calls each adapter with system-specific IDs.
 * - `removeContactFromList`: soft-deletes the membership and calls each adapter.
 * - Fault tolerance: one adapter failure does not prevent other adapters from running.
 * - Skips an adapter if the contact or list has not been synced to that system yet.
 */
@Service
class ContactListService(
    private val listSyncAdapters: List<ListSyncAdapter>,
    private val contactListRepository: ContactListRepository,
    private val contactRepository: ContactRepository,
    private val contactListMembershipRepository: ContactListMembershipRepository,
) {
    /**
     * Returns an existing [ContactList] matching [name], or creates it in all registered systems.
     */
    @Transactional
    fun findOrCreateList(name: String, folderName: String?): ContactList {
        contactListRepository.findByName(name)?.let { return it }

        val list = ContactList(name = name, folderName = folderName)

        for (adapter in listSyncAdapters) {
            try {
                val externalId = adapter.createList(name, folderName)
                when (adapter.system) {
                    ContactSystem.LISTMONK -> list.listmonkList = ListmonkList(list = list, externalId = externalId)
                    ContactSystem.BREVO -> list.brevoList = BrevoList(list = list, externalId = externalId)
                }
            } catch (e: Exception) {
                log.error("Adapter {} failed to create list '{}'", adapter.system, name, e)
            }
        }

        return contactListRepository.save(list)
    }

    /**
     * Adds the user's contact to [contactListId] in all registered systems.
     *
     * Creates a [ContactListMembership] record. Idempotent: if an active membership already
     * exists it is returned without making adapter calls.
     */
    @Transactional
    fun addContactToList(contactListId: Long, userId: Long) {
        val list = findById(contactListId)
        val record = contactRepository.findByUserId(userId)
        if (record == null) {
            log.warn("No Contact found for user {} — cannot add to list {}", userId, contactListId)
            return
        }

        val existing = contactListMembershipRepository
            .findByContactIdAndContactListId(record.id!!, contactListId)
        if (existing != null) {
            log.debug("Contact for user {} is already in list {} — skipping", userId, contactListId)
            return
        }

        contactListMembershipRepository.save(ContactListMembership(contact = record, contactList = list))

        for (adapter in listSyncAdapters) {
            val systemContactId = record.systemContactId(adapter.system)
            val systemListId = list.systemListId(adapter.system)

            if (systemContactId == null) {
                log.warn("No {} contact ID for user {} — skipping addToList", adapter.system, userId)
                continue
            }
            if (systemListId == null) {
                log.warn("No {} list ID for list {} — skipping addToList", adapter.system, contactListId)
                continue
            }

            try {
                adapter.addToList(systemContactId, systemListId)
            } catch (e: Exception) {
                log.error("Adapter {} failed to add user {} to list {}", adapter.system, userId, contactListId, e)
            }
        }
    }

    /**
     * Removes the user's contact from [contactListId] in all registered systems.
     *
     * Soft-deletes the [ContactListMembership]. Idempotent: no-op if no active membership exists.
     */
    @Transactional
    fun removeContactFromList(contactListId: Long, userId: Long) {
        val list = findById(contactListId)
        val record = contactRepository.findByUserId(userId)
        if (record == null) {
            log.debug("No Contact for user {} — nothing to remove from list {}", userId, contactListId)
            return
        }

        val membership = contactListMembershipRepository
            .findByContactIdAndContactListId(record.id!!, contactListId)
        if (membership == null) {
            log.debug("No active membership for user {} in list {} — skipping", userId, contactListId)
            return
        }

        contactListMembershipRepository.delete(membership)

        for (adapter in listSyncAdapters) {
            val systemContactId = record.systemContactId(adapter.system)
            val systemListId = list.systemListId(adapter.system)

            if (systemContactId == null) {
                log.warn("No {} contact ID for user {} — skipping removeFromList", adapter.system, userId)
                continue
            }
            if (systemListId == null) {
                log.warn("No {} list ID for list {} — skipping removeFromList", adapter.system, contactListId)
                continue
            }

            try {
                adapter.removeFromList(systemContactId, systemListId)
            } catch (e: Exception) {
                log.error("Adapter {} failed to remove user {} from list {}", adapter.system, userId, contactListId, e)
            }
        }
    }

    /**
     * Deletes [contactListId] from all registered systems and soft-deletes the [ContactList].
     */
    @Transactional
    fun deleteList(contactListId: Long) {
        val list = findById(contactListId)

        for (adapter in listSyncAdapters) {
            val systemListId = list.systemListId(adapter.system)
            if (systemListId == null) {
                log.debug("No {} list ID for list {} — skipping deleteList", adapter.system, contactListId)
                continue
            }
            try {
                adapter.deleteList(systemListId)
            } catch (e: Exception) {
                log.error("Adapter {} failed to delete list {}", adapter.system, contactListId, e)
            }
        }

        contactListRepository.delete(list)
    }

    fun findById(contactListId: Long): ContactList =
        contactListRepository.findById(contactListId)
            .orElseThrow { NoSuchElementException("ContactList not found: $contactListId") }

    companion object {
        private val log = LoggerFactory.getLogger(ContactListService::class.java)
    }
}

// ── private extension helpers ─────────────────────────────────────────────────

private fun net.blueshell.api.platform.integration.contact.persistence.Contact.systemContactId(
    system: ContactSystem
): Long? = when (system) {
    ContactSystem.LISTMONK -> listmonkContact?.externalId
    ContactSystem.BREVO -> brevoContact?.externalId
}

private fun ContactList.systemListId(system: ContactSystem): Long? = when (system) {
    ContactSystem.LISTMONK -> listmonkList?.externalId
    ContactSystem.BREVO -> brevoList?.externalId
}
