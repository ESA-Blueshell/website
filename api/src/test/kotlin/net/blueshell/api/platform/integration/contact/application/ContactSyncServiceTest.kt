package net.blueshell.api.platform.integration.contact.application

import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.application.contact.ContactData
import net.blueshell.api.domain.user.application.contact.ContactSyncAdapter
import net.blueshell.api.domain.user.application.contact.ContactSystem
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.platform.integration.contact.persistence.BrevoContact
import net.blueshell.api.platform.integration.contact.persistence.Contact
import net.blueshell.api.platform.integration.contact.persistence.ListmonkContact
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

/**
 * Unit tests for [ContactSyncService].
 *
 * No Spring context — instantiate directly with mocks.
 */
class ContactSyncServiceTest {

    private val listmonkAdapter: ContactSyncAdapter = mock {
        whenever(mock.system).thenReturn(ContactSystem.LISTMONK)
    }
    private val brevoAdapter: ContactSyncAdapter = mock {
        whenever(mock.system).thenReturn(ContactSystem.BREVO)
    }
    private val contactRepository: ContactRepository = mock()
    private val userService: UserService = mock()

    private val service = ContactSyncService(
        contactSyncAdapters = listOf(listmonkAdapter, brevoAdapter),
        contactRepository = contactRepository,
        userService = userService,
    )

    private val userId = 42L
    private val data = ContactData(
        email = "test@example.com",
        firstName = "Test",
        lastName = "User",
        phoneNumber = null,
        newsletter = true,
        isMember = true,
    )

    @BeforeEach
    fun setUp() {
        val user: User = mock()
        whenever(userService.findById(userId)).thenReturn(user)
        whenever(user.email).thenReturn(data.email)
        whenever(user.firstName).thenReturn(data.firstName)
        whenever(user.lastName).thenReturn(data.lastName)
        whenever(user.phoneNumber).thenReturn(data.phoneNumber)
        whenever(user.newsletter).thenReturn(data.newsletter)
        whenever(user.hasRole(any())).thenReturn(true)
        whenever(contactRepository.save(any<Contact>())).thenAnswer { it.arguments[0] }
    }

    @Test
    fun `calls createContact on all adapters on first sync`() {
        whenever(contactRepository.findByUserId(userId)).thenReturn(null)
        whenever(listmonkAdapter.createContact(data)).thenReturn(10L)
        whenever(brevoAdapter.createContact(data)).thenReturn(20L)

        service.syncContact(userId)

        verify(listmonkAdapter).createContact(data)
        verify(brevoAdapter).createContact(data)
        verify(listmonkAdapter, never()).updateContact(any(), any())
        verify(brevoAdapter, never()).updateContact(any(), any())
    }

    @Test
    fun `stores returned externalId in system-specific child on first sync`() {
        whenever(contactRepository.findByUserId(userId)).thenReturn(null)
        whenever(listmonkAdapter.createContact(data)).thenReturn(10L)
        whenever(brevoAdapter.createContact(data)).thenReturn(20L)

        var saved: Contact? = null
        whenever(contactRepository.save(any<Contact>())).thenAnswer {
            saved = it.arguments[0] as Contact
            saved
        }

        service.syncContact(userId)

        assertThat(saved!!.listmonkContact?.externalId).isEqualTo(10L)
        assertThat(saved!!.brevoContact?.externalId).isEqualTo(20L)
    }

    @Test
    fun `calls updateContact when record already has system-specific child`() {
        val record = Contact(userId = userId)
        record.id = 1L
        record.listmonkContact = ListmonkContact(contact = record, externalId = 10L)
        record.brevoContact = BrevoContact(contact = record, externalId = 20L)
        // Change one field so delta check doesn't short-circuit
        record.syncedEmail = "old@example.com"

        whenever(contactRepository.findByUserId(userId)).thenReturn(record)

        service.syncContact(userId)

        verify(listmonkAdapter).updateContact(eq(10L), any())
        verify(brevoAdapter).updateContact(eq(20L), any())
        verify(listmonkAdapter, never()).createContact(any())
        verify(brevoAdapter, never()).createContact(any())
    }

    @Test
    fun `skips sync when snapshot matches`() {
        val record = Contact(userId = userId).apply {
            id = 1L
            syncedEmail = data.email
            syncedFirstName = data.firstName
            syncedLastName = data.lastName
            syncedPhoneNumber = data.phoneNumber
            syncedNewsletter = data.newsletter
            syncedIsMember = data.isMember
        }

        whenever(contactRepository.findByUserId(userId)).thenReturn(record)

        service.syncContact(userId)

        verifyNoInteractions(listmonkAdapter)
        verifyNoInteractions(brevoAdapter)
        verify(contactRepository, never()).save(any())
    }

    @Test
    fun `continues with remaining adapters when one adapter throws`() {
        whenever(contactRepository.findByUserId(userId)).thenReturn(null)
        doThrow(RuntimeException("Listmonk down"))
            .whenever(listmonkAdapter).createContact(any())
        whenever(brevoAdapter.createContact(data)).thenReturn(20L)

        service.syncContact(userId)

        // Brevo still called despite Listmonk failure
        verify(brevoAdapter).createContact(data)
    }

    @Test
    fun `updates snapshot after sync`() {
        whenever(contactRepository.findByUserId(userId)).thenReturn(null)
        whenever(listmonkAdapter.createContact(data)).thenReturn(10L)
        whenever(brevoAdapter.createContact(data)).thenReturn(20L)

        var saved: Contact? = null
        whenever(contactRepository.save(any<Contact>())).thenAnswer {
            saved = it.arguments[0] as Contact
            saved
        }

        service.syncContact(userId)

        assertThat(saved!!.syncedEmail).isEqualTo(data.email)
        assertThat(saved!!.syncedFirstName).isEqualTo(data.firstName)
        assertThat(saved!!.syncedLastName).isEqualTo(data.lastName)
        assertThat(saved!!.syncedNewsletter).isEqualTo(data.newsletter)
        assertThat(saved!!.syncedIsMember).isEqualTo(data.isMember)
    }

    @Test
    fun `deleteContact calls all adapters with correct externalIds`() {
        val record = Contact(userId = userId).apply { id = 1L }
        record.listmonkContact = ListmonkContact(contact = record, externalId = 10L)
        record.brevoContact = BrevoContact(contact = record, externalId = 20L)

        whenever(contactRepository.findByUserId(userId)).thenReturn(record)

        service.deleteContact(userId)

        verify(listmonkAdapter).deleteContact(10L)
        verify(brevoAdapter).deleteContact(20L)
        verify(contactRepository).delete(record)
    }

    @Test
    fun `deleteContact is no-op when no Contact exists`() {
        whenever(contactRepository.findByUserId(userId)).thenReturn(null)

        service.deleteContact(userId)

        verifyNoInteractions(listmonkAdapter)
        verifyNoInteractions(brevoAdapter)
        verify(contactRepository, never()).delete(any<Contact>())
    }

    @Test
    fun `deleteContact skips adapter when system child is missing`() {
        val record = Contact(userId = userId).apply { id = 1L }
        record.listmonkContact = ListmonkContact(contact = record, externalId = 10L)
        // no brevoContact

        whenever(contactRepository.findByUserId(userId)).thenReturn(record)

        service.deleteContact(userId)

        verify(listmonkAdapter).deleteContact(10L)
        verify(brevoAdapter, never()).deleteContact(any())
    }
}
