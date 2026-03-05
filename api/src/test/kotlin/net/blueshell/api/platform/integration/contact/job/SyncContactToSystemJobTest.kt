package net.blueshell.api.platform.integration.contact.job

import tools.jackson.databind.ObjectMapper
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.application.contact.ContactData
import net.blueshell.api.domain.user.application.contact.ContactSyncAdapter
import net.blueshell.api.domain.user.application.contact.ContactSystem
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.platform.integration.contact.application.job.SyncContactToSystemJob
import net.blueshell.api.platform.integration.contact.persistence.Contact
import net.blueshell.api.platform.integration.contact.persistence.ListmonkContact
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactRepository
import net.blueshell.api.shared.job.ContactJobs
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for [SyncContactToSystemJob].
 *
 * No Spring context — instantiate directly with mocks.
 */
class SyncContactToSystemJobTest {

    private val objectMapper = ObjectMapper()
    private val listmonkAdapter: ContactSyncAdapter = mock {
        whenever(mock.system).thenReturn(ContactSystem.LISTMONK)
    }
    private val contactRepository: ContactRepository = mock()
    private val userService: UserService = mock()

    private val job = SyncContactToSystemJob(
        objectMapper = objectMapper,
        adapters = listOf(listmonkAdapter),
        contactRepository = contactRepository,
        userService = userService,
    )

    private val userId = 1L
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
    fun `calls createContact and saves externalId when no externalId exists`() {
        val record = Contact(userId = userId).apply { id = 1L }
        whenever(contactRepository.findByUserId(userId)).thenReturn(record)
        whenever(listmonkAdapter.createContact(data)).thenReturn(42L)

        job.handle(payload(ContactJobs.SyncContactToSystemPayload(userId, ContactSystem.LISTMONK)))

        verify(listmonkAdapter).createContact(data)
        verify(listmonkAdapter, never()).updateContact(any(), any())

        val captor = argumentCaptor<Contact>()
        verify(contactRepository).save(captor.capture())
        assertThat(captor.firstValue.listmonkContact?.externalId).isEqualTo(42L)
    }

    @Test
    fun `calls updateContact when externalId already exists`() {
        val record = Contact(userId = userId).apply { id = 1L }
        record.listmonkContact = ListmonkContact(contact = record, externalId = 99L)
        whenever(contactRepository.findByUserId(userId)).thenReturn(record)

        job.handle(payload(ContactJobs.SyncContactToSystemPayload(userId, ContactSystem.LISTMONK)))

        verify(listmonkAdapter).updateContact(eq(99L), any())
        verify(listmonkAdapter, never()).createContact(any())
    }

    @Test
    fun `creates Contact DB record when none exists`() {
        whenever(contactRepository.findByUserId(userId)).thenReturn(null)
        whenever(listmonkAdapter.createContact(data)).thenReturn(5L)

        job.handle(payload(ContactJobs.SyncContactToSystemPayload(userId, ContactSystem.LISTMONK)))

        // Two saves: first to persist the placeholder Contact, second to persist the externalId
        val captor = argumentCaptor<Contact>()
        verify(contactRepository, times(2)).save(captor.capture())
        assertThat(captor.lastValue.listmonkContact?.externalId).isEqualTo(5L)
    }

    @Test
    fun `skips gracefully when no adapter registered for system`() {
        val record = Contact(userId = userId).apply { id = 1L }
        whenever(contactRepository.findByUserId(userId)).thenReturn(record)

        // BREVO system requested but only LISTMONK adapter registered
        job.handle(payload(ContactJobs.SyncContactToSystemPayload(userId, ContactSystem.BREVO)))

        verify(listmonkAdapter, never()).createContact(any())
        verify(listmonkAdapter, never()).updateContact(any(), any())
    }

    private fun payload(p: Any) = objectMapper.writeValueAsString(p)
}
