package net.blueshell.api.platform.integration.mock

import net.blueshell.api.domain.user.application.contact.ContactData
import net.blueshell.api.domain.user.application.contact.ContactSyncAdapter
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Mock Contact Adapter for testing and development.
 *
 * Provides in-memory contact management without external API dependencies.
 * Active in 'test' and 'dev' profiles.
 */
@Service
@Primary
@Profile("test | dev")
class MockContactAdapter : ContactSyncAdapter {

    private val contacts = ConcurrentHashMap<String, MockContact>()
    private val lists = ConcurrentHashMap<String, MockList>()
    private val contactIdSequence = AtomicLong(1000)
    private val listIdSequence = AtomicLong(2000)

    override fun syncContact(userId: Long, contactData: ContactData): String {
        log.info("Mock: Syncing contact for user {}: {}", userId, contactData.email)

        val existingContact = contacts.values.find { it.email == contactData.email }

        return if (existingContact != null) {
            // Update existing contact
            existingContact.apply {
                this.firstName = contactData.firstName
                this.lastName = contactData.lastName
                this.phoneNumber = contactData.phoneNumber
                this.newsletter = contactData.newsletter
                this.isMember = contactData.isMember
                this.attributes.clear()
                this.attributes.putAll(contactData.attributes)
            }
            log.info("Mock: Updated contact {} for user {}", existingContact.contactId, userId)
            existingContact.contactId
        } else {
            // Create new contact
            val contactId = contactIdSequence.getAndIncrement().toString()
            val newContact = MockContact(
                contactId = contactId,
                email = contactData.email,
                firstName = contactData.firstName,
                lastName = contactData.lastName,
                phoneNumber = contactData.phoneNumber,
                newsletter = contactData.newsletter,
                isMember = contactData.isMember,
                attributes = contactData.attributes.toMutableMap()
            )
            contacts[contactId] = newContact
            log.info("Mock: Created contact {} for user {}", contactId, userId)
            contactId
        }
    }

    override fun getContactId(userId: Long, email: String): String? {
        log.debug("Mock: Getting contact ID for user {}: {}", userId, email)
        val contact = contacts.values.find { it.email == email }
        return contact?.contactId
    }

    override fun addToList(listId: String, contactId: String) {
        log.info("Mock: Adding contact {} to list {}", contactId, listId)

        val list = lists[listId] ?: throw IllegalArgumentException("List not found: $listId")
        val contact = contacts[contactId] ?: throw IllegalArgumentException("Contact not found: $contactId")

        list.contactIds.add(contactId)
        log.info("Mock: Contact {} added to list {} (now {} contacts)", contactId, listId, list.contactIds.size)
    }

    override fun removeFromList(listId: String, contactId: String) {
        log.info("Mock: Removing contact {} from list {}", contactId, listId)

        val list = lists[listId] ?: throw IllegalArgumentException("List not found: $listId")

        if (list.contactIds.remove(contactId)) {
            log.info("Mock: Contact {} removed from list {} (now {} contacts)", contactId, listId, list.contactIds.size)
        } else {
            log.error("Mock: Contact {} is not in list {}", contactId, listId)
            throw IllegalStateException("Contact $contactId is not in list $listId")
        }
    }

    override fun deleteContact(contactId: String) {
        log.info("Mock: Deleting contact {}", contactId)
        val removed = contacts.remove(contactId)
            ?: throw IllegalArgumentException("Contact not found: $contactId")

        lists.values.forEach { list ->
            list.contactIds.remove(contactId)
        }
        log.info("Mock: Deleted contact {} ({})", contactId, removed.email)
    }

    override fun createList(listName: String, folderName: String): String {
        log.info("Mock: Creating list '{}' in folder '{}'", listName, folderName)

        val listId = listIdSequence.getAndIncrement().toString()
        val newList = MockList(
            listId = listId,
            listName = listName,
            folderName = folderName
        )
        lists[listId] = newList

        log.info("Mock: Created list {} with name '{}'", listId, listName)
        return listId
    }

    /**
     * Helper method to get all contacts (useful for testing/debugging)
     */
    fun getAllContacts(): Map<String, MockContact> = contacts.toMap()

    /**
     * Helper method to get all lists (useful for testing/debugging)
     */
    fun getAllLists(): Map<String, MockList> = lists.toMap()

    /**
     * Helper method to clear all data (useful for tests)
     */
    fun clear() {
        contacts.clear()
        lists.clear()
        log.info("Mock: Cleared all contacts and lists")
    }

    data class MockContact(
        val contactId: String,
        val email: String,
        var firstName: String,
        var lastName: String,
        var phoneNumber: String?,
        var newsletter: Boolean,
        var isMember: Boolean,
        val attributes: MutableMap<String, Any> = mutableMapOf()
    )

    data class MockList(
        val listId: String,
        val listName: String,
        val folderName: String,
        val contactIds: MutableSet<String> = mutableSetOf()
    )

    companion object {
        private val log = LoggerFactory.getLogger(MockContactAdapter::class.java)
    }
}
