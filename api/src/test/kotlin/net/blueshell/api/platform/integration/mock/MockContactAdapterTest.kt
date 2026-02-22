package net.blueshell.api.platform.integration.mock

import net.blueshell.api.domain.user.application.contact.ContactData
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
    fun `syncContact creates contact when email is new`() {
        val contactId = adapter.syncContact(1L, contactData(email = "new@example.com"))

        assertThat(contactId).isNotBlank()
        assertThat(adapter.getAllContacts()).hasSize(1)
        val contact = adapter.getAllContacts()[contactId]
        assertThat(contact).isNotNull
        assertThat(contact!!.email).isEqualTo("new@example.com")
        assertThat(contact.firstName).isEqualTo("First")
        assertThat(contact.newsletter).isTrue()
    }

    @Test
    fun `syncContact updates existing contact when email already exists`() {
        val originalId = adapter.syncContact(2L, contactData(email = "same@example.com", firstName = "Old", isMember = false))
        val updatedId = adapter.syncContact(
            2L,
            contactData(
                email = "same@example.com",
                firstName = "Updated",
                lastName = "Name",
                phoneNumber = "+31611111111",
                newsletter = false,
                isMember = true,
                attributes = mapOf("status" to "updated")
            )
        )

        assertThat(updatedId).isEqualTo(originalId)
        assertThat(adapter.getAllContacts()).hasSize(1)
        val contact = adapter.getAllContacts()[originalId]
        assertThat(contact).isNotNull
        assertThat(contact!!.firstName).isEqualTo("Updated")
        assertThat(contact.lastName).isEqualTo("Name")
        assertThat(contact.phoneNumber).isEqualTo("+31611111111")
        assertThat(contact.newsletter).isFalse()
        assertThat(contact.isMember).isTrue()
        assertThat(contact.attributes).containsEntry("status", "updated")
    }

    @Test
    fun `getContactId returns id for known email and null for unknown email`() {
        val createdId = adapter.syncContact(3L, contactData(email = "known@example.com"))

        assertThat(adapter.getContactId(3L, "known@example.com")).isEqualTo(createdId)
        assertThat(adapter.getContactId(3L, "unknown@example.com")).isNull()
    }

    @Test
    fun `createList stores list and returns new id`() {
        val listId = adapter.createList("Newsletters", "Email")

        assertThat(listId).isNotBlank()
        assertThat(adapter.getAllLists()).hasSize(1)
        val list = adapter.getAllLists()[listId]
        assertThat(list).isNotNull
        assertThat(list!!.listName).isEqualTo("Newsletters")
        assertThat(list.folderName).isEqualTo("Email")
    }

    @Test
    fun `addToList adds existing contact to existing list`() {
        val contactId = adapter.syncContact(4L, contactData(email = "list@example.com"))
        val listId = adapter.createList("Members", "Main")

        adapter.addToList(listId, contactId)

        assertThat(adapter.getAllLists()[listId]!!.contactIds).containsExactly(contactId)
    }

    @Test
    fun `addToList throws when list does not exist`() {
        val contactId = adapter.syncContact(5L, contactData(email = "missing-list@example.com"))

        assertThatThrownBy { adapter.addToList("missing-list", contactId) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("List not found")
    }

    @Test
    fun `addToList throws when contact does not exist`() {
        val listId = adapter.createList("Events", "Main")

        assertThatThrownBy { adapter.addToList(listId, "missing-contact") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Contact not found")
    }

    @Test
    fun `removeFromList removes existing relation and keeps list`() {
        val contactId = adapter.syncContact(6L, contactData(email = "remove@example.com"))
        val listId = adapter.createList("Participants", "Main")
        adapter.addToList(listId, contactId)
        assertThat(adapter.getAllLists()[listId]!!.contactIds).contains(contactId)

        adapter.removeFromList(listId, contactId)

        assertThat(adapter.getAllLists()[listId]!!.contactIds).doesNotContain(contactId)
    }

    @Test
    fun `removeFromList throws when contact is not in the list`() {
        val contactId = adapter.syncContact(7L, contactData(email = "other@example.com"))
        val listId = adapter.createList("NotJoined", "Main")

        assertThatThrownBy { adapter.removeFromList(listId, contactId) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("is not in list")
    }

    @Test
    fun `removeFromList throws when list does not exist`() {
        assertThatThrownBy { adapter.removeFromList("missing-list", "any-contact") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("List not found")
    }

    @Test
    fun `clear removes all contacts and lists`() {
        adapter.syncContact(8L, contactData(email = "a@example.com"))
        adapter.createList("ListA", "FolderA")
        assertThat(adapter.getAllContacts()).isNotEmpty
        assertThat(adapter.getAllLists()).isNotEmpty

        adapter.clear()

        assertThat(adapter.getAllContacts()).isEmpty()
        assertThat(adapter.getAllLists()).isEmpty()
    }

    private fun contactData(
        email: String,
        firstName: String = "First",
        lastName: String = "Last",
        phoneNumber: String? = "+31612345678",
        newsletter: Boolean = true,
        isMember: Boolean = true,
        attributes: Map<String, Any> = mapOf("source" to "test")
    ): ContactData {
        return ContactData(
            email = email,
            firstName = firstName,
            lastName = lastName,
            phoneNumber = phoneNumber,
            newsletter = newsletter,
            isMember = isMember,
            attributes = attributes
        )
    }
}
