package net.blueshell.api.platform.integration.contact.application

import net.blueshell.api.platform.integration.contact.adapter.ListAdapter
import net.blueshell.api.platform.integration.contact.persistence.Contact
import net.blueshell.api.platform.integration.contact.persistence.ContactList
import net.blueshell.api.platform.integration.contact.persistence.ContactListMembership
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactListMembershipRepository
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactListRepository
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Manages contact list records and membership in the local database.
 *
 * Responsibilities:
 * - `findOrCreateList`: idempotent list creation — creates in all registered systems synchronously,
 *   since list creation is a rare precondition for all membership operations.
 * - `createMembership`: DB-only — records that a user should be in a list. Returns false if the
 *   contact does not exist yet or membership already exists.
 * - `deleteMembership`: DB-only — records that a user should no longer be in a list.
 * - `deleteList`: removes the list from all registered systems and the local DB.
 *
 * External list membership operations (addToList / removeFromList per system) are handled
 * by [AddToListJob] and [RemoveFromListJob] respectively.
 */
@Service
class ContactListService(
    private val listSyncAdapters: List<ListAdapter>,
    private val contactListRepository: ContactListRepository,
    private val contactRepository: ContactRepository,
    private val contactListMembershipRepository: ContactListMembershipRepository,
) {
    /**
     * Returns an existing [ContactList] matching [name], or creates it in all registered systems.
     *
     * List creation is synchronous because it is a rare precondition for all list operations;
     * making it async would require job dependency chains.
     */
    @Transactional
    fun findOrCreateList(name: String, folderName: String?): ContactList {
        contactListRepository.findByName(name)?.let { return it }

        val list = ContactList(name = name, folderName = folderName)

        for (adapter in listSyncAdapters) {
            try {
                val externalId = adapter.createList(name, folderName)
                list.setExternalListId(adapter.system, externalId)
            } catch (e: Exception) {
                log.error("Adapter {} failed to create list '{}'", adapter.system, name, e)
            }
        }

        return contactListRepository.save(list)
    }

    /**
     * Creates a [ContactListMembership] DB record for the user in [contactListId].
     *
     * Returns `true` if a new membership was created, `false` if one already existed or
     * if the user has no [Contact] record yet (contact sync is handled separately).
     *
     * This method is DB-only; external system membership is dispatched via [AddToListJob].
     */
    @Transactional
    fun createMembership(contactListId: Long, userId: Long): Boolean {
        val contact = contactRepository.findByUserId(userId)
            ?: contactRepository.save(Contact(userId = userId))

        val existing = contactListMembershipRepository
            .findByContactIdAndContactListId(contact.id!!, contactListId)
        if (existing != null) {
            log.debug("Contact for user {} is already in list {} — skipping", userId, contactListId)
            return false
        }

        val list = findById(contactListId)
        contactListMembershipRepository.save(ContactListMembership(contact = contact, contactList = list))
        return true
    }

    /**
     * Deletes the [ContactListMembership] DB record for the user in [contactListId].
     *
     * Idempotent: no-op if no active membership exists.
     * This method is DB-only; external system removal is dispatched via [RemoveFromListJob].
     */
    @Transactional
    fun deleteMembership(contactListId: Long, userId: Long) {
        val contact = contactRepository.findByUserId(userId)
        if (contact == null) {
            log.debug("No Contact for user {} — nothing to remove from list {}", userId, contactListId)
            return
        }

        val membership = contactListMembershipRepository
            .findByContactIdAndContactListId(contact.id!!, contactListId)
        if (membership == null) {
            log.debug("No active membership for user {} in list {} — skipping", userId, contactListId)
            return
        }

        contactListMembershipRepository.delete(membership)
    }

    /**
     * Deletes [contactListId] from all registered systems and removes the [ContactList].
     */
    @Transactional
    fun deleteList(contactListId: Long) {
        val list = findById(contactListId)

        for (adapter in listSyncAdapters) {
            val externalListId = list.externalListId(adapter.system)
            if (externalListId == null) {
                log.debug("No {} list ID for list {} — skipping deleteList", adapter.system, contactListId)
                continue
            }
            try {
                adapter.deleteList(externalListId)
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
