package net.blueshell.api.contact.domain

import net.blueshell.api.contact.api.ContactAdapter
import net.blueshell.api.contact.api.ContactData
import net.blueshell.api.contact.api.ContactListAdapter
import net.blueshell.api.contact.api.ContactListMember
import net.blueshell.api.contact.api.ContactListRef
import net.blueshell.api.contact.api.ContactServiceException
import net.blueshell.api.shared.credentials.Credentials
import net.blueshell.api.shared.credentials.WhenCredentialsMissing
import net.blueshell.api.shared.enums.TargetSystem
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

/**
 * In-memory [ContactAdapter] and [ContactListAdapter], standing in for Brevo where no Brevo API key
 * is set. The contact sync and the Brevo cohort target both run against it. Both ports share one
 * store, since a list names the contacts the other port holds, which is why it is long.
 *
 * Reports itself as [TargetSystem.BREVO], so a test exercises the Brevo path without reaching
 * an external API.
 */
@Suppress("TooManyFunctions")
@Service
@WhenCredentialsMissing(Credentials.BREVO)
class MockContactAdapter :
    ContactAdapter,
    ContactListAdapter {
    override val system = TargetSystem.BREVO

    private val contacts = ConcurrentHashMap<Long, MockContact>()
    private val lists = ConcurrentHashMap<Long, MockList>()
    private val memberships = ConcurrentHashMap<Pair<Long, Long>, Unit>() // (contactId, listId)
    private val contactIdSequence = AtomicLong(1000)
    private val listIdSequence = AtomicLong(2000)
    private val folderIds = ConcurrentHashMap<String, Long>()
    private val folderIdSequence = AtomicLong(3000)

    /**
     * Whether a transaction was open at each list call, for a test asserting a cohort job reaches
     * the provider only outside one.
     */
    val transactionActiveDuringCalls: MutableList<Boolean> = CopyOnWriteArrayList()

    private fun sanitizeForLog(value: String): String =
        value.map { ch -> if (ch == '\r' || ch == '\n' || ch.isISOControl()) '_' else ch }.joinToString("")

    override fun createContact(data: ContactData): Long {
        val contactId = contactIdSequence.getAndIncrement()
        contacts[contactId] =
            MockContact(
                contactId = contactId,
                email = data.email,
                firstName = data.firstName,
                lastName = data.lastName,
                phoneNumber = data.phoneNumber,
                newsletter = data.newsletter,
                isMember = data.isMember,
                attributes = data.attributes.toMutableMap(),
            )
        log.info("Mock: Created contact id={} for user {}", contactId, data.userId)
        return contactId
    }

    override fun updateContact(
        externalId: Long,
        data: ContactData,
    ): Long {
        val contact =
            contacts[externalId]
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
        contacts.remove(externalId)
            ?: throw ContactServiceException("Mock: Contact not found: $externalId")
        memberships.keys.removeIf { (contactId, _) -> contactId == externalId }
        log.info("Mock: Deleted contact id={}", externalId)
    }

    override fun createList(
        name: String,
        folderName: String?,
    ): Long {
        recordTransactionState()
        val listId = listIdSequence.getAndIncrement()
        val folder = folderName?.takeIf { it.isNotBlank() } ?: ContactListAdapter.UNFILED
        lists[listId] = MockList(listId = listId, listName = name, folderName = folder)
        folderOf(folder)
        val safeName = sanitizeForLog(name)
        log.info("Mock: Created list id={} name='{}'", listId, safeName)
        return listId
    }

    override fun addToList(
        externalUserId: Long,
        externalListId: Long,
    ) {
        recordTransactionState()
        if (!lists.containsKey(externalListId)) throw ContactServiceException("Mock: List not found: $externalListId")
        if (!contacts.containsKey(externalUserId)) throw ContactServiceException("Mock: Contact not found: $externalUserId")
        memberships[externalUserId to externalListId] = Unit
        log.info("Mock: Added contact {} to list {}", externalUserId, externalListId)
    }

    override fun removeFromList(
        externalUserId: Long,
        externalListId: Long,
    ) {
        recordTransactionState()
        if (memberships.remove(externalUserId to externalListId) == null) {
            log.warn("Mock: Contact {} was not in list {}", externalUserId, externalListId)
        } else {
            log.info("Mock: Removed contact {} from list {}", externalUserId, externalListId)
        }
    }

    override fun deleteList(externalListId: Long) {
        recordTransactionState()
        lists.remove(externalListId) ?: throw ContactServiceException("Mock: List not found: $externalListId")
        memberships.keys.removeIf { (_, listId) -> listId == externalListId }
        log.info("Mock: Deleted list id={}", externalListId)
    }

    override fun listMembers(externalListId: Long): List<ContactListMember> {
        recordTransactionState()
        return memberships.keys
            .filter { (_, listId) -> listId == externalListId }
            .map { (contactId, _) -> ContactListMember(contactId, contacts[contactId]?.email) }
    }

    private fun recordTransactionState() {
        transactionActiveDuringCalls += TransactionSynchronizationManager.isActualTransactionActive()
    }

    override fun listFolders(): Map<Long, String> {
        recordTransactionState()
        return folderIds.entries.associate { (name, id) -> id to name }
    }

    override fun listAll(): List<ContactListRef> {
        recordTransactionState()
        return lists.values.map { list ->
            ContactListRef(
                externalListId = list.listId,
                name = list.listName,
                folderId = folderOf(list.folderName),
                memberCount = memberships.keys.count { (_, listId) -> listId == list.listId }.toLong(),
            )
        }
    }

    override fun moveList(
        externalListId: Long,
        folderId: Long,
    ) {
        recordTransactionState()
        val list = lists[externalListId] ?: throw ContactServiceException("Mock: List not found: $externalListId")
        val folder =
            folderIds.entries.firstOrNull { it.value == folderId }?.key
                ?: throw ContactServiceException("Mock: Folder not found: $folderId")
        lists[externalListId] = list.copy(folderName = folder)
    }

    override fun renameList(
        externalListId: Long,
        name: String,
    ) {
        recordTransactionState()
        val list = lists[externalListId] ?: throw ContactServiceException("Mock: List not found: $externalListId")
        lists[externalListId] = list.copy(listName = name)
    }

    override fun createFolder(name: String): Long {
        recordTransactionState()
        return requireNotNull(folderOf(name)) { "A folder needs a name" }
    }

    override fun deleteFolder(folderId: Long) {
        recordTransactionState()
        val name =
            folderIds.entries.firstOrNull { it.value == folderId }?.key
                ?: throw ContactServiceException("Mock: Folder not found: $folderId")
        lists.values.removeIf { it.folderName == name }
        folderIds.remove(name)
    }

    // A folder exists once a list has been filed in it, as it would have to on Brevo first.
    private fun folderOf(name: String?): Long? =
        name?.takeIf { it.isNotBlank() }?.let { folderIds.computeIfAbsent(it) { folderIdSequence.getAndIncrement() } }

    fun getAllContacts(): Map<Long, MockContact> = contacts.toMap()

    fun getAllLists(): Map<Long, MockList> = lists.toMap()

    fun getMemberships(): Set<Pair<Long, Long>> = memberships.keys.toSet()

    fun isInList(
        externalId: Long,
        externalListId: Long,
    ): Boolean = memberships.containsKey(externalId to externalListId)

    fun clear() {
        contacts.clear()
        lists.clear()
        memberships.clear()
        folderIds.clear()
        transactionActiveDuringCalls.clear()
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
        val attributes: MutableMap<String, Any> = mutableMapOf(),
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
