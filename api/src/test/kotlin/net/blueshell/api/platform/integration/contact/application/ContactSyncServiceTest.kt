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
import net.blueshell.api.shared.job.ContactJobs
import net.blueshell.api.shared.job.TrackedJobDispatcher
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
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
    private val jobs: TrackedJobDispatcher = mock()

    private val service = ContactSyncService(
        contactSyncAdapters = listOf(listmonkAdapter, brevoAdapter),
        contactRepository = contactRepository,
        userService = userService,
        jobs = jobs,
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
    fun `dispatches SyncContactToSystem job per adapter on first sync`() {
        whenever(contactRepository.findByUserId(userId)).thenReturn(null)

        service.syncContact(userId)

        verify(jobs).enqueue(
            eq(ContactJobs.SyncContactToSystem),
            eq(ContactJobs.SyncContactToSystemPayload(userId, ContactSystem.LISTMONK))
        )
        verify(jobs).enqueue(
            eq(ContactJobs.SyncContactToSystem),
            eq(ContactJobs.SyncContactToSystemPayload(userId, ContactSystem.BREVO))
        )
    }

    @Test
    fun `dispatches SyncContactToSystem job per adapter on update`() {
        val record = Contact(userId = userId).apply {
            id = 1L
            syncedEmail = "old@example.com"  // different → triggers sync
        }
        whenever(contactRepository.findByUserId(userId)).thenReturn(record)

        service.syncContact(userId)

        // One dispatch per registered adapter (listmonk + brevo = 2)
        verify(jobs, times(2)).enqueue(eq(ContactJobs.SyncContactToSystem), any<ContactJobs.SyncContactToSystemPayload>())
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

        verifyNoInteractions(jobs)
        verify(contactRepository, never()).save(any())
    }

    @Test
    fun `updates snapshot after sync`() {
        whenever(contactRepository.findByUserId(userId)).thenReturn(null)

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
    fun `deleteContact dispatches DeleteContactFromSystem job with correct externalId per adapter`() {
        val record = Contact(userId = userId).apply { id = 1L }
        record.listmonkContact = ListmonkContact(contact = record, externalId = 10L)
        record.brevoContact = BrevoContact(contact = record, externalId = 20L)

        whenever(contactRepository.findByUserId(userId)).thenReturn(record)

        service.deleteContact(userId)

        verify(jobs).enqueue(
            eq(ContactJobs.DeleteContactFromSystem),
            eq(ContactJobs.DeleteContactFromSystemPayload(10L, ContactSystem.LISTMONK))
        )
        verify(jobs).enqueue(
            eq(ContactJobs.DeleteContactFromSystem),
            eq(ContactJobs.DeleteContactFromSystemPayload(20L, ContactSystem.BREVO))
        )
        verify(contactRepository).delete(record)
    }

    @Test
    fun `deleteContact is no-op when no Contact exists`() {
        whenever(contactRepository.findByUserId(userId)).thenReturn(null)

        service.deleteContact(userId)

        verifyNoInteractions(jobs)
        verify(contactRepository, never()).delete(any<Contact>())
    }

    @Test
    fun `deleteContact skips adapter when system child is missing`() {
        val record = Contact(userId = userId).apply { id = 1L }
        record.listmonkContact = ListmonkContact(contact = record, externalId = 10L)
        // no brevoContact

        whenever(contactRepository.findByUserId(userId)).thenReturn(record)

        service.deleteContact(userId)

        verify(jobs).enqueue(
            eq(ContactJobs.DeleteContactFromSystem),
            eq(ContactJobs.DeleteContactFromSystemPayload(10L, ContactSystem.LISTMONK))
        )
        // Only one dispatch — no Brevo call because brevoContact is null
        verify(jobs, times(1)).enqueue(eq(ContactJobs.DeleteContactFromSystem), any<ContactJobs.DeleteContactFromSystemPayload>())
    }
}
