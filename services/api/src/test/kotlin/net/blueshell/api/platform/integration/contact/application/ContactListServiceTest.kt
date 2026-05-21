package net.blueshell.api.platform.integration.contact.application

import net.blueshell.api.platform.integration.contact.adapter.ContactListAdapter
import net.blueshell.api.platform.integration.contact.persistence.Contact
import net.blueshell.api.shared.enums.ContactSystem
import net.blueshell.api.platform.integration.contact.persistence.ContactList
import net.blueshell.api.platform.integration.contact.persistence.ContactListMembership
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactListMembershipRepository
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactListRepository
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional

/**
 * Unit tests for [ContactListService].
 *
 * No Spring context — instantiate directly with mocks.
 */
class ContactListServiceTest {

    private val brevoAdapter: ContactListAdapter = mock {
        whenever(mock.system).thenReturn(ContactSystem.BREVO)
    }
    private val contactListRepository: ContactListRepository = mock()
    private val contactRepository: ContactRepository = mock()
    private val membershipRepository: ContactListMembershipRepository = mock()

    private val service = ContactListService(
        listSyncAdapters = listOf(brevoAdapter),
        contactListRepository = contactListRepository,
        contactRepository = contactRepository,
        contactListMembershipRepository = membershipRepository,
    )

    private val userId = 1L
    private val listId = 10L

    @BeforeEach
    fun setUp() {
        whenever(contactListRepository.save(any<ContactList>())).thenAnswer { it.arguments[0] }
        whenever(membershipRepository.save(any<ContactListMembership>())).thenAnswer { it.arguments[0] }
        whenever(contactRepository.save(any<Contact>())).thenAnswer { invocation ->
            val c = invocation.arguments[0] as Contact
            if (c.id == null) c.id = userId
            c
        }
    }

    // ── findOrCreateList ──────────────────────────────────────────────────────

    @Test
    fun `returns existing list when found by name`() {
        val existing = contactListWithId(listId, "Existing")
        whenever(contactListRepository.findByName("Existing")).thenReturn(existing)

        val result = service.findOrCreateList("Existing", null)

        assertThat(result).isSameAs(existing)
        verify(brevoAdapter, never()).createList(any(), any())
    }

    @Test
    fun `creates list in all adapters when not found by name`() {
        whenever(contactListRepository.findByName("New")).thenReturn(null)
        whenever(brevoAdapter.createList("New", "folder")).thenReturn(200L)

        var saved: ContactList? = null
        whenever(contactListRepository.save(any<ContactList>())).thenAnswer {
            saved = it.arguments[0] as ContactList
            saved
        }

        service.findOrCreateList("New", "folder")

        verify(brevoAdapter).createList("New", "folder")
        assertThat(saved!!.externalListId(ContactSystem.BREVO)).isEqualTo(200L)
    }

    @Test
    fun `still saves the list when an adapter throws`() {
        whenever(contactListRepository.findByName("New")).thenReturn(null)
        doThrow(RuntimeException("Brevo down")).whenever(brevoAdapter).createList(any(), any())

        service.findOrCreateList("New", null)

        verify(contactListRepository).save(any<ContactList>())
    }

    // ── createMembership ──────────────────────────────────────────────────────

    @Test
    fun `creates membership record when contact exists`() {
        val contact = contactWithId(userId)
        val list = contactListWithId(listId, "List")

        whenever(contactRepository.findByUserId(userId)).thenReturn(contact)
        whenever(contactListRepository.findById(listId)).thenReturn(Optional.of(list))
        whenever(membershipRepository.findByContactIdAndContactListId(contact.id!!, listId)).thenReturn(null)

        val result = service.createMembership(listId, userId)

        assertThat(result).isTrue()
        verify(membershipRepository).save(any<ContactListMembership>())
    }

    @Test
    fun `creates new Contact and membership when no Contact record exists`() {
        val list = contactListWithId(listId, "List")
        whenever(contactRepository.findByUserId(userId)).thenReturn(null)
        whenever(contactListRepository.findById(listId)).thenReturn(Optional.of(list))
        whenever(membershipRepository.findByContactIdAndContactListId(any(), any())).thenReturn(null)

        val result = service.createMembership(listId, userId)

        assertThat(result).isTrue()
        verify(contactRepository).save(any<Contact>())
        verify(membershipRepository).save(any<ContactListMembership>())
    }

    @Test
    fun `returns false when membership already exists`() {
        val contact = contactWithId(userId)
        val list = contactListWithId(listId, "List")
        val existing = ContactListMembership(contact = contact, contactList = list)

        whenever(contactRepository.findByUserId(userId)).thenReturn(contact)
        whenever(contactListRepository.findById(listId)).thenReturn(Optional.of(list))
        whenever(membershipRepository.findByContactIdAndContactListId(contact.id!!, listId)).thenReturn(existing)

        val result = service.createMembership(listId, userId)

        assertThat(result).isFalse()
        verify(membershipRepository, never()).save(any())
    }

    // ── deleteMembership ──────────────────────────────────────────────────────

    @Test
    fun `deletes membership record when it exists`() {
        val contact = contactWithId(userId)
        val list = contactListWithId(listId, "List")
        val membership = ContactListMembership(contact = contact, contactList = list)

        whenever(contactRepository.findByUserId(userId)).thenReturn(contact)
        whenever(contactListRepository.findById(listId)).thenReturn(Optional.of(list))
        whenever(membershipRepository.findByContactIdAndContactListId(contact.id!!, listId)).thenReturn(membership)

        service.deleteMembership(listId, userId)

        verify(membershipRepository).delete(any<ContactListMembership>())
    }

    @Test
    fun `is no-op when no Contact exists`() {
        whenever(contactRepository.findByUserId(userId)).thenReturn(null)

        service.deleteMembership(listId, userId)

        verify(membershipRepository, never()).delete(any<ContactListMembership>())
    }

    @Test
    fun `is no-op when no membership exists`() {
        val contact = contactWithId(userId)
        val list = contactListWithId(listId, "List")

        whenever(contactRepository.findByUserId(userId)).thenReturn(contact)
        whenever(contactListRepository.findById(listId)).thenReturn(Optional.of(list))
        whenever(membershipRepository.findByContactIdAndContactListId(contact.id!!, listId)).thenReturn(null)

        service.deleteMembership(listId, userId)

        verify(membershipRepository, never()).delete(any<ContactListMembership>())
    }

    // ── deleteList ────────────────────────────────────────────────────────────

    @Test
    fun `calls all adapters and deletes list`() {
        val list = contactListWithId(listId, "List", brevoId = 200L)
        whenever(contactListRepository.findById(listId)).thenReturn(Optional.of(list))

        service.deleteList(listId)

        verify(brevoAdapter).deleteList(200L)
        verify(contactListRepository).delete(list)
    }

    @Test
    fun `skips adapter when list has no system-specific external ID`() {
        val list = contactListWithId(listId, "List", brevoId = null)
        whenever(contactListRepository.findById(listId)).thenReturn(Optional.of(list))

        service.deleteList(listId)

        verify(brevoAdapter, never()).deleteList(any())
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun contactWithId(userId: Long, brevoId: Long? = null): Contact {
        val c = Contact(userId = userId)
        c.id = userId
        brevoId?.let { c.setExternalId(ContactSystem.BREVO, it) }
        return c
    }

    private fun contactListWithId(
        id: Long,
        name: String,
        brevoId: Long? = null,
    ): ContactList {
        val l = ContactList(name = name)
        l.id = id
        brevoId?.let { l.setExternalListId(ContactSystem.BREVO, it) }
        return l
    }
}
