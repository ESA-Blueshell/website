package net.blueshell.api.platform.integration.mock

import net.blueshell.api.domain.user.application.contact.ContactData
import net.blueshell.api.domain.user.application.contact.ContactServiceException
import net.blueshell.api.domain.user.application.contact.ContactSyncAdapter
import net.blueshell.api.domain.user.application.contact.ContactSystem
import net.blueshell.api.domain.user.application.contact.ListSyncAdapter
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Mock Contact Adapter for testing and development.
 *
 * Provides in-memory contact and list management without external API dependencies.
 * Active in 'test' and 'dev' profiles.
 *
 * Implements both [ContactSyncAdapter] and [ListSyncAdapter].
 * Reported system is [ContactSystem.LISTMONK] so tests exercise the Listmonk path.
 */
@Service
@Primary
@Profile("test | dev")
class MockContactAdapter : ContactSyncAdapter, ListSyncAdapter {

    override val system = ContactSystem.LISTMONK

    private val contacts = ConcurrentHashMap<Long, MockContact>()
    private val lists = ConcurrentHashMap<Long, MockList>()
    private val memberships = ConcurrentHashMap<Pair<Long, Long>, Unit>()  // (contactId, listId)
    private val contactIdSequence = AtomicLong(1000)
    private val listIdSequence = AtomicLong(2000)

    // ── ContactSyncAdapter ────────────────────────────────────────────────────

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
        log.info("Mock: Created contact id={} for {}", contactId, data.email)
        return contactId
    }

    override fun updateContact(systemContactId: Long, data: ContactData) {
        val contact = contacts[systemContactId]
            ?: throw ContactServiceException("Mock: Contact not found: $systemContactId")
        contact.apply {
            firstName = data.firstName
            lastName = data.lastName
            phoneNumber = data.phoneNumber
            newsletter = data.newsletter
            isMember = data.isMember
            attributes.clear()
            attributes.putAll(data.attributes)
        }
        log.info("Mock: Updated contact id={}", systemContactId)
    }

    override fun deleteContact(systemContactId: Long) {
        val removed = contacts.remove(systemContactId)
            ?: throw ContactServiceException("Mock: Contact not found: $systemContactId")
        memberships.keys.removeIf { (contactId, _) -> contactId == systemContactId }
        log.info("Mock: Deleted contact id={} ({})", systemContactId, removed.email)
    }

    // ── ListSyncAdapter ───────────────────────────────────────────────────────

    override fun createList(name: String, folderName: String?): Long {
        val listId = listIdSequence.getAndIncrement()
        lists[listId] = MockList(listId = listId, listName = name, folderName = folderName)
        log.info("Mock: Created list id={} name='{}'", listId, name)
        return listId
    }

    override fun addToList(systemContactId: Long, systemListId: Long) {
        if (!lists.containsKey(systemListId)) throw ContactServiceException("Mock: List not found: $systemListId")
        if (!contacts.containsKey(systemContactId)) throw ContactServiceException("Mock: Contact not found: $systemContactId")
        memberships[systemContactId to systemListId] = Unit
        log.info("Mock: Added contact {} to list {}", systemContactId, systemListId)
    }

    override fun removeFromList(systemContactId: Long, systemListId: Long) {
        if (memberships.remove(systemContactId to systemListId) == null) {
            log.warn("Mock: Contact {} was not in list {}", systemContactId, systemListId)
        } else {
            log.info("Mock: Removed contact {} from list {}", systemContactId, systemListId)
        }
    }

    override fun deleteList(systemListId: Long) {
        lists.remove(systemListId) ?: throw ContactServiceException("Mock: List not found: $systemListId")
        memberships.keys.removeIf { (_, listId) -> listId == systemListId }
        log.info("Mock: Deleted list id={}", systemListId)
    }

    // ── Test helpers ──────────────────────────────────────────────────────────

    fun getAllContacts(): Map<Long, MockContact> = contacts.toMap()
    fun getAllLists(): Map<Long, MockList> = lists.toMap()
    fun getMemberships(): Set<Pair<Long, Long>> = memberships.keys.toSet()
    fun isInList(systemContactId: Long, systemListId: Long): Boolean =
        memberships.containsKey(systemContactId to systemListId)

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
