package net.blueshell.api.platform.integration.contact.adapter.listmonk

import tools.jackson.databind.ObjectMapper
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.platform.integration.contact.application.job.listmonk.ListmonkContactSyncJob
import net.blueshell.api.platform.integration.contact.persistence.Contact
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactRepository
import net.blueshell.api.shared.enums.ContactSystem
import net.blueshell.api.shared.job.ListmonkJobs
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
 * Unit tests for [ListmonkContactSyncJob].
 *
 * No Spring context — instantiate directly with mocks.
 */
class ListmonkContactSyncJobTest {

    private val objectMapper = ObjectMapper()
    private val adapter: ListmonkContactAdapter = mock()
    private val contactRepository: ContactRepository = mock()
    private val userService: UserService = mock()

    private val job = ListmonkContactSyncJob(objectMapper, adapter, contactRepository, userService)

    private val userId = 42L
    private val listmonkId = 200L

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
    fun `creates subscriber when no external ID exists`() {
        val record = Contact(userId = userId).apply { id = 1L }
        whenever(contactRepository.findByUserIdIncludingDeleted(userId)).thenReturn(record)
        whenever(adapter.createContact(any())).thenReturn(listmonkId)

        job.handle(objectMapper.writeValueAsString(ListmonkJobs.ListmonkContactSyncPayload(userId)))

        verify(adapter).createContact(any())
        verify(contactRepository).save(any<Contact>())
    }

    @Test
    fun `updates subscriber when external ID already exists`() {
        val record = Contact(userId = userId).apply { id = 1L }
        record.setExternalId(ContactSystem.LISTMONK, listmonkId)
        whenever(contactRepository.findByUserIdIncludingDeleted(userId)).thenReturn(record)

        job.handle(objectMapper.writeValueAsString(ListmonkJobs.ListmonkContactSyncPayload(userId)))

        verify(adapter).updateContact(eq(listmonkId), any())
        verify(adapter, never()).createContact(any())
    }

    @Test
    fun `deletes subscriber when contact is soft-deleted`() {
        val record = Contact(userId = userId).apply {
            id = 1L
            deletedAt = Instant.now()
        }
        record.setExternalId(ContactSystem.LISTMONK, listmonkId)
        whenever(contactRepository.findByUserIdIncludingDeleted(userId)).thenReturn(record)

        job.handle(objectMapper.writeValueAsString(ListmonkJobs.ListmonkContactSyncPayload(userId)))

        verify(adapter).deleteContact(listmonkId)
        verify(adapter, never()).createContact(any())
        verify(adapter, never()).updateContact(any(), any())
        verify(contactRepository).save(any<Contact>())
    }

    @Test
    fun `skips deletion when soft-deleted contact has no Listmonk external ID`() {
        val record = Contact(userId = userId).apply {
            id = 1L
            deletedAt = Instant.now()
        }
        whenever(contactRepository.findByUserIdIncludingDeleted(userId)).thenReturn(record)

        job.handle(objectMapper.writeValueAsString(ListmonkJobs.ListmonkContactSyncPayload(userId)))

        verify(adapter, never()).deleteContact(any())
        verify(contactRepository, never()).save(any<Contact>())
    }
}
