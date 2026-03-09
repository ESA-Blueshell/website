package net.blueshell.api.platform.integration.contact.adapter.brevo

import tools.jackson.databind.ObjectMapper
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.platform.integration.contact.application.job.brevo.BrevoContactSyncJob
import net.blueshell.api.platform.integration.contact.persistence.Contact
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactRepository
import net.blueshell.api.shared.enums.ContactSystem
import net.blueshell.api.shared.job.BrevoJobs
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

/**
 * Unit tests for [BrevoContactSyncJob].
 *
 * No Spring context — instantiate directly with mocks.
 */
class BrevoContactSyncJobTest {

    private val objectMapper = ObjectMapper()
    private val adapter: BrevoContactAdapter = mock()
    private val contactRepository: ContactRepository = mock()
    private val userService: UserService = mock()

    private val job = BrevoContactSyncJob(objectMapper, adapter, contactRepository, userService)

    private val userId = 42L
    private val brevoId = 100L

    @BeforeEach
    fun setUp() {
        val user: User = mock()
        whenever(userService.findById(userId)).thenReturn(user)
        whenever(user.email).thenReturn("test@example.com")
        whenever(user.firstName).thenReturn("Test")
        whenever(user.lastName).thenReturn("User")
        whenever(user.phoneNumber).thenReturn(null)
        whenever(user.newsletter).thenReturn(false)
        whenever(user.hasRole(any())).thenReturn(false)
        whenever(contactRepository.save(any<Contact>())).thenAnswer { it.arguments[0] }
    }

    @Test
    fun `creates contact when no external ID exists`() {
        val record = Contact(userId = userId).apply { id = 1L }
        whenever(contactRepository.findByUserIdIncludingDeleted(userId)).thenReturn(record)
        whenever(adapter.createContact(any())).thenReturn(brevoId)

        job.handle(objectMapper.writeValueAsString(BrevoJobs.BrevoContactSyncPayload(userId)))

        verify(adapter).createContact(any())
        verify(contactRepository).save(any<Contact>())
    }

    @Test
    fun `updates contact when external ID already exists`() {
        val record = Contact(userId = userId).apply { id = 1L }
        record.setExternalId(ContactSystem.BREVO, brevoId)
        whenever(contactRepository.findByUserIdIncludingDeleted(userId)).thenReturn(record)

        job.handle(objectMapper.writeValueAsString(BrevoJobs.BrevoContactSyncPayload(userId)))

        verify(adapter).updateContact(eq(brevoId), any())
        verify(adapter, never()).createContact(any())
    }

    @Test
    fun `deletes contact when contact is soft-deleted`() {
        val record = Contact(userId = userId).apply {
            id = 1L
            deletedAt = Instant.now()
        }
        record.setExternalId(ContactSystem.BREVO, brevoId)
        whenever(contactRepository.findByUserIdIncludingDeleted(userId)).thenReturn(record)

        job.handle(objectMapper.writeValueAsString(BrevoJobs.BrevoContactSyncPayload(userId)))

        verify(adapter).deleteContact(brevoId)
        verify(adapter, never()).createContact(any())
        verify(adapter, never()).updateContact(any(), any())
        verify(contactRepository).save(any<Contact>())
    }

    @Test
    fun `skips deletion when soft-deleted contact has no Brevo external ID`() {
        val record = Contact(userId = userId).apply {
            id = 1L
            deletedAt = Instant.now()
        }
        // No external ID set
        whenever(contactRepository.findByUserIdIncludingDeleted(userId)).thenReturn(record)

        job.handle(objectMapper.writeValueAsString(BrevoJobs.BrevoContactSyncPayload(userId)))

        verify(adapter, never()).deleteContact(any())
        verify(contactRepository, never()).save(any<Contact>())
    }

    @Test
    fun `creates new contact record when no Contact exists`() {
        whenever(contactRepository.findByUserIdIncludingDeleted(userId)).thenReturn(null)
        whenever(adapter.createContact(any())).thenReturn(brevoId)

        job.handle(objectMapper.writeValueAsString(BrevoJobs.BrevoContactSyncPayload(userId)))

        verify(adapter).createContact(any())
        // Contact was created and saved
        verify(contactRepository, org.mockito.kotlin.atLeastOnce()).save(any<Contact>())
    }

    @Test
    fun `updates snapshot after create`() {
        whenever(contactRepository.findByUserIdIncludingDeleted(userId)).thenReturn(null)
        whenever(adapter.createContact(any())).thenReturn(brevoId)

        var saved: Contact? = null
        whenever(contactRepository.save(any<Contact>())).thenAnswer {
            saved = it.arguments[0] as Contact
            saved
        }

        job.handle(objectMapper.writeValueAsString(BrevoJobs.BrevoContactSyncPayload(userId)))

        assertThat(saved!!.syncedEmail).isEqualTo("test@example.com")
    }
}
