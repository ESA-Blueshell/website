package net.blueshell.api.platform.integration.mock

import net.blueshell.api.contact.api.ContactAdapter
import net.blueshell.api.contact.api.ContactData
import net.blueshell.api.contact.api.ContactListMember
import net.blueshell.api.contact.api.ContactServiceException
import net.blueshell.api.contact.api.ContactListAdapter
import net.blueshell.api.shared.enums.ContactSystem
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * In-memory [ContactAdapter] and [ContactListAdapter] for the `test` and `dev` profiles.
 *
 * Reports itself as [ContactSystem.BREVO], so a test exercises the Brevo path without reaching
 * an external API.
 */
@Service
@Primary
@Profile("test | dev")
class MockContactAdapter : ContactAdapter, ContactListAdapter {

    override val system = ContactSystem.BREVO

    private val contacts = ConcurrentHashMap<Long, MockContact>()
    private val lists = ConcurrentHashMap<Long, MockList>()
    private val memberships = ConcurrentHashMap<Pair<Long, Long>, Unit>()  // (contactId, listId)
    private val contactIdSequence = AtomicLong(1000)
    private val listIdSequence = AtomicLong(2000)

    private fun sanitizeForLog(value: String): String =
        value.map { ch -> if (ch == '\r' || ch == '\n' || ch.isISOControl()) '_' else ch }.joinToString("")

    override fun createContact(data: ContactData): Long {
        val contactId = contactIdSequence.getAndIncrement()
        contacts[contactId] = MockContact(
            contactId = contactId,
            email = data.email,
            firstName = data.firstName,
            lastName = data.lastName,
            phoneNumber = data.phoneNumber,
            newsletter = data.newsletter,
            isMember = data.isMember,
            attributes = data.attributes.toMutableMap()
        )
        val safeEmail = sanitizeForLog(data.email)
        log.info("Mock: Created contact id={} for {}", contactId, safeEmail)
        return contactId
    }

    override fun updateContact(externalId: Long, data: ContactData): Long {
        val contact = contacts[externalId]
            ?: throw ContactServiceException("Mock: Contact not found: $externalId")
        contact.apply {
            firstName = data.firstName
            lastName = data.lastName
            phoneNumber = data.phoneNumber
            newsletter = data.newsletter
            isMember = data.isMember
            attributes.clear()
            attributes.putAll(data.attributes)
        }
        log.info("Mock: Updated contact id={}", externalId)
        return externalId
    }

    override fun deleteContact(externalId: Long) {
        val removed = contacts.remove(externalId)
            ?: throw ContactServiceException("Mock: Contact not found: $externalId")
        memberships.keys.removeIf { (contactId, _) -> contactId == externalId }
        val safeEmail = sanitizeForLog(removed.email)
        log.info("Mock: Deleted contact id={} ({})", externalId, safeEmail)
    }

    override fun createList(name: String, folderName: String?): Long {
        val listId = listIdSequence.getAndIncrement()
        lists[listId] = MockList(listId = listId, listName = name, folderName = folderName)
        val safeName = sanitizeForLog(name)
        log.info("Mock: Created list id={} name='{}'", listId, safeName)
        return listId
    }

    override fun addToList(externalUserId: Long, externalListId: Long) {
        if (!lists.containsKey(externalListId)) throw ContactServiceException("Mock: List not found: $externalListId")
        if (!contacts.containsKey(externalUserId)) throw ContactServiceException("Mock: Contact not found: $externalUserId")
        memberships[externalUserId to externalListId] = Unit
        log.info("Mock: Added contact {} to list {}", externalUserId, externalListId)
    }

    override fun removeFromList(externalUserId: Long, externalListId: Long) {
        if (memberships.remove(externalUserId to externalListId) == null) {
            log.warn("Mock: Contact {} was not in list {}", externalUserId, externalListId)
        } else {
            log.info("Mock: Removed contact {} from list {}", externalUserId, externalListId)
        }
    }

    override fun deleteList(externalListId: Long) {
        lists.remove(externalListId) ?: throw ContactServiceException("Mock: List not found: $externalListId")
        memberships.keys.removeIf { (_, listId) -> listId == externalListId }
        log.info("Mock: Deleted list id={}", externalListId)
    }

    override fun listMembers(externalListId: Long): List<ContactListMember> =
        memberships.keys
            .filter { (_, listId) -> listId == externalListId }
            .map { (contactId, _) -> ContactListMember(contactId, contacts[contactId]?.email) }

    fun getAllContacts(): Map<Long, MockContact> = contacts.toMap()
    fun getAllLists(): Map<Long, MockList> = lists.toMap()
    fun getMemberships(): Set<Pair<Long, Long>> = memberships.keys.toSet()
    fun isInList(externalId: Long, externalListId: Long): Boolean =
        memberships.containsKey(externalId to externalListId)

    fun clear() {
        contacts.clear()
        lists.clear()
        memberships.clear()
        log.info("Mock: Cleared all state")
    }

    data class MockContact(
        val contactId: Long,
        val email: String,
        var firstName: String,
        var lastName: String,
        var phoneNumber: String?,
        var newsletter: Boolean,
        var isMember: Boolean,
        val attributes: MutableMap<String, Any> = mutableMapOf()
    )

    data class MockList(
        val listId: Long,
        val listName: String,
        val folderName: String?,
    )

    companion object {
        private val log = LoggerFactory.getLogger(MockContactAdapter::class.java)
    }
}
