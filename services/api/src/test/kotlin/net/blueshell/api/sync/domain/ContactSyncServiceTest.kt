package net.blueshell.api.sync.domain

import net.blueshell.api.contact.api.ContactAdapter
import net.blueshell.api.contact.api.ContactData
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.sync.api.ExternalIdMappingService
import net.blueshell.api.sync.persistence.ExternalIdMapping
import net.blueshell.api.testsupport.Entities
import net.blueshell.api.user.api.UserService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class ContactSyncServiceTest {
    private val userService: UserService = mock()
    private val mappings: ExternalIdMappingService = mock()
    private val contacts: ContactAdapter = mock { on { system }.thenReturn(TargetSystem.BREVO) }
    private val service = ContactSyncService(contacts, mappings, userService)

    private val userId = 42L

    private fun stubUser() {
        val user = Entities.user(id = userId, email = "a@b.c", firstName = "A", lastName = "B", roles = emptySet())
        whenever(userService.findById(userId)).thenReturn(user)
    }

    private fun mapped(externalId: String) {
        whenever(mappings.find("USER", userId, "BREVO")).thenReturn(ExternalIdMapping("USER", userId, "BREVO", externalId))
    }

    @Test
    fun `an unmapped user is created as a contact and the contact's id recorded`() {
        stubUser()
        whenever(contacts.createContact(any())).thenReturn(7L)

        assertThat(service.sync(userId)).isNull()

        val data = argumentCaptor<ContactData>()
        verify(contacts).createContact(data.capture())
        assertThat(data.firstValue.email).isEqualTo("a@b.c")
        verify(mappings).upsert("USER", userId, "BREVO", "7")
    }

    @Test
    fun `a mapped user updates its contact and records the id the update answers with`() {
        stubUser()
        mapped("7")
        whenever(contacts.updateContact(eq(7L), any())).thenReturn(8L)

        service.sync(userId)

        verify(contacts, never()).createContact(any())
        verify(mappings).upsert("USER", userId, "BREVO", "8")
    }

    @Test
    fun `removing a mapped user deletes the contact and clears the id`() {
        mapped("7")

        service.remove(userId)

        verify(contacts).deleteContact(7L)
        verify(mappings).upsert("USER", userId, "BREVO", null)
    }

    @Test
    fun `removing an unmapped user deletes nothing`() {
        service.remove(userId)

        verify(contacts, never()).deleteContact(any())
        verify(mappings).upsert("USER", userId, "BREVO", null)
    }

    @Test
    fun `sync is a no-op when the user does not exist, and says so`() {
        whenever(userService.findById(userId)).thenThrow(RuntimeException("not found"))

        assertThat(service.sync(userId)).isEqualTo("The user no longer exists.")

        verifyNoInteractions(contacts, mappings)
    }
}
