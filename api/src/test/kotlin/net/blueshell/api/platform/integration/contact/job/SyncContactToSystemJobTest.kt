package net.blueshell.api.platform.integration.contact.job

import tools.jackson.databind.ObjectMapper
import net.blueshell.api.domain.user.application.contact.ContactData
import net.blueshell.api.domain.user.application.contact.ContactSystem
import net.blueshell.api.domain.user.application.contact.ContactSystemAdapter
import net.blueshell.api.platform.integration.contact.application.job.SyncContactToSystemJob
import net.blueshell.api.platform.integration.contact.persistence.Contact
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
    private val listmonkAdapter: ContactSystemAdapter = mock {
        whenever(mock.system).thenReturn(ContactSystem.LISTMONK)
    }
    private val contactRepository: ContactRepository = mock()

    private val job = SyncContactToSystemJob(
        objectMapper = objectMapper,
        adapters = listOf(listmonkAdapter),
        contactRepository = contactRepository,
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
        whenever(contactRepository.save(any<Contact>())).thenAnswer { it.arguments[0] }
    }

    private fun contactWithSnapshot(userId: Long): Contact =
        Contact(userId = userId).apply {
            id = 1L
            syncedEmail = data.email
            syncedFirstName = data.firstName
            syncedLastName = data.lastName
            syncedPhoneNumber = data.phoneNumber
            syncedNewsletter = data.newsletter
            syncedIsMember = data.isMember
        }

    @Test
    fun `calls createContact and saves externalId when no externalId exists`() {
        val record = contactWithSnapshot(userId)
        whenever(contactRepository.findByUserId(userId)).thenReturn(record)
        whenever(listmonkAdapter.createContact(data)).thenReturn(42L)

        job.handle(payload(ContactJobs.SyncContactToSystemPayload(userId, ContactSystem.LISTMONK)))

        verify(listmonkAdapter).createContact(data)
        verify(listmonkAdapter, never()).updateContact(any(), any())

        val captor = argumentCaptor<Contact>()
        verify(contactRepository).save(captor.capture())
        assertThat(captor.firstValue.externalId(ContactSystem.LISTMONK)).isEqualTo(42L)
    }

    @Test
    fun `calls updateContact when externalId already exists`() {
        val record = contactWithSnapshot(userId)
        record.setExternalId(ContactSystem.LISTMONK, 99L)
        whenever(contactRepository.findByUserId(userId)).thenReturn(record)

        job.handle(payload(ContactJobs.SyncContactToSystemPayload(userId, ContactSystem.LISTMONK)))

        verify(listmonkAdapter).updateContact(eq(99L), any())
        verify(listmonkAdapter, never()).createContact(any())
    }

    @Test
    fun `creates Contact DB record when none exists`() {
        whenever(contactRepository.findByUserId(userId)).thenReturn(null)
        whenever(listmonkAdapter.createContact(any())).thenReturn(5L)

        job.handle(payload(ContactJobs.SyncContactToSystemPayload(userId, ContactSystem.LISTMONK)))

        // Two saves: first to persist the placeholder Contact, second to persist the externalId
        val captor = argumentCaptor<Contact>()
        verify(contactRepository, times(2)).save(captor.capture())
        assertThat(captor.lastValue.externalId(ContactSystem.LISTMONK)).isEqualTo(5L)
    }

    @Test
    fun `skips gracefully when no adapter registered for system`() {
        val record = contactWithSnapshot(userId)
        whenever(contactRepository.findByUserId(userId)).thenReturn(record)

        // BREVO system requested but only LISTMONK adapter registered
        job.handle(payload(ContactJobs.SyncContactToSystemPayload(userId, ContactSystem.BREVO)))

        verify(listmonkAdapter, never()).createContact(any())
        verify(listmonkAdapter, never()).updateContact(any(), any())
    }

    private fun payload(p: Any) = objectMapper.writeValueAsString(p)
}
