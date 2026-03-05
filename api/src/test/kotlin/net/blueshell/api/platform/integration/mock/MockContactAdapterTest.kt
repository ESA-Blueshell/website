package net.blueshell.api.platform.integration.mock

import net.blueshell.api.domain.user.application.contact.ContactData
import net.blueshell.api.domain.user.application.contact.ContactServiceException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MockContactAdapterTest {

    private val adapter = MockContactAdapter()

    @BeforeEach
    fun setUp() {
        adapter.clear()
    }

    @Test
    fun `createContact stores contact and returns new id`() {
        val contactId = adapter.createContact(contactData(email = "new@example.com"))

        assertThat(contactId).isGreaterThan(0)
        assertThat(adapter.getAllContacts()).hasSize(1)
        val contact = adapter.getAllContacts()[contactId]
        assertThat(contact).isNotNull
        assertThat(contact!!.email).isEqualTo("new@example.com")
        assertThat(contact.firstName).isEqualTo("First")
        assertThat(contact.newsletter).isTrue()
    }

    @Test
    fun `createContact assigns unique ids for different emails`() {
        val id1 = adapter.createContact(contactData(email = "a@example.com"))
        val id2 = adapter.createContact(contactData(email = "b@example.com"))

        assertThat(id1).isNotEqualTo(id2)
        assertThat(adapter.getAllContacts()).hasSize(2)
    }

    @Test
    fun `updateContact updates existing contact fields`() {
        val contactId = adapter.createContact(contactData(email = "same@example.com", firstName = "Old", isMember = false))
        adapter.updateContact(contactId, contactData(
            email = "same@example.com",
            firstName = "Updated",
            lastName = "Name",
            phoneNumber = "+31611111111",
            newsletter = false,
            isMember = true,
            attributes = mapOf("status" to "updated")
        ))

        val contact = adapter.getAllContacts()[contactId]!!
        assertThat(contact.firstName).isEqualTo("Updated")
        assertThat(contact.lastName).isEqualTo("Name")
        assertThat(contact.phoneNumber).isEqualTo("+31611111111")
        assertThat(contact.newsletter).isFalse()
        assertThat(contact.isMember).isTrue()
        assertThat(contact.attributes).containsEntry("status", "updated")
    }

    @Test
    fun `updateContact throws for unknown contact id`() {
        assertThatThrownBy { adapter.updateContact(9999L, contactData()) }
            .isInstanceOf(ContactServiceException::class.java)
            .hasMessageContaining("Contact not found")
    }

    @Test
    fun `deleteContact removes the contact`() {
        val contactId = adapter.createContact(contactData(email = "delete@example.com"))
        assertThat(adapter.getAllContacts()).hasSize(1)

        adapter.deleteContact(contactId)

        assertThat(adapter.getAllContacts()).isEmpty()
    }

    @Test
    fun `deleteContact also removes memberships for that contact`() {
        val contactId = adapter.createContact(contactData())
        val listId = adapter.createList("List", null)
        adapter.addToList(contactId, listId)
        assertThat(adapter.isInList(contactId, listId)).isTrue()

        adapter.deleteContact(contactId)

        assertThat(adapter.isInList(contactId, listId)).isFalse()
    }

    @Test
    fun `createList stores list and returns new id`() {
        val listId = adapter.createList("Newsletters", "Email")

        assertThat(listId).isGreaterThan(0)
        assertThat(adapter.getAllLists()).hasSize(1)
        val list = adapter.getAllLists()[listId]
        assertThat(list).isNotNull
        assertThat(list!!.listName).isEqualTo("Newsletters")
        assertThat(list.folderName).isEqualTo("Email")
    }

    @Test
    fun `addToList adds contact to list`() {
        val contactId = adapter.createContact(contactData())
        val listId = adapter.createList("Members", "Main")

        adapter.addToList(contactId, listId)

        assertThat(adapter.isInList(contactId, listId)).isTrue()
        assertThat(adapter.getMemberships()).contains(contactId to listId)
    }

    @Test
    fun `addToList throws when list does not exist`() {
        val contactId = adapter.createContact(contactData())

        assertThatThrownBy { adapter.addToList(contactId, 9999L) }
            .isInstanceOf(ContactServiceException::class.java)
            .hasMessageContaining("List not found")
    }

    @Test
    fun `addToList throws when contact does not exist`() {
        val listId = adapter.createList("Events", "Main")

        assertThatThrownBy { adapter.addToList(9999L, listId) }
            .isInstanceOf(ContactServiceException::class.java)
            .hasMessageContaining("Contact not found")
    }

    @Test
    fun `removeFromList removes the membership`() {
        val contactId = adapter.createContact(contactData())
        val listId = adapter.createList("Participants", "Main")
        adapter.addToList(contactId, listId)

        adapter.removeFromList(contactId, listId)

        assertThat(adapter.isInList(contactId, listId)).isFalse()
    }

    @Test
    fun `removeFromList is no-op when contact not in list`() {
        val contactId = adapter.createContact(contactData())
        val listId = adapter.createList("NotJoined", "Main")

        // should not throw
        adapter.removeFromList(contactId, listId)
    }

    @Test
    fun `deleteList removes list and its memberships`() {
        val contactId = adapter.createContact(contactData())
        val listId = adapter.createList("ToDelete", null)
        adapter.addToList(contactId, listId)

        adapter.deleteList(listId)

        assertThat(adapter.getAllLists()).isEmpty()
        assertThat(adapter.isInList(contactId, listId)).isFalse()
    }

    @Test
    fun `deleteList throws when list does not exist`() {
        assertThatThrownBy { adapter.deleteList(9999L) }
            .isInstanceOf(ContactServiceException::class.java)
            .hasMessageContaining("List not found")
    }

    @Test
    fun `clear removes all contacts, lists, and memberships`() {
        adapter.createContact(contactData(email = "a@example.com"))
        adapter.createList("ListA", "FolderA")
        assertThat(adapter.getAllContacts()).isNotEmpty
        assertThat(adapter.getAllLists()).isNotEmpty

        adapter.clear()

        assertThat(adapter.getAllContacts()).isEmpty()
        assertThat(adapter.getAllLists()).isEmpty()
        assertThat(adapter.getMemberships()).isEmpty()
    }

    private fun contactData(
        email: String = "test@example.com",
        firstName: String = "First",
        lastName: String = "Last",
        phoneNumber: String? = "+31612345678",
        newsletter: Boolean = true,
        isMember: Boolean = true,
        attributes: Map<String, Any> = mapOf("source" to "test")
    ): ContactData = ContactData(
        email = email,
        firstName = firstName,
        lastName = lastName,
        phoneNumber = phoneNumber,
        newsletter = newsletter,
        isMember = isMember,
        attributes = attributes
    )
}
