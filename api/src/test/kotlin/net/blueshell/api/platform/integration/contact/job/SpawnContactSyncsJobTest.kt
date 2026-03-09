package net.blueshell.api.platform.integration.contact.job

import tools.jackson.databind.ObjectMapper
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.platform.integration.contact.application.job.SpawnContactSyncsJob
import net.blueshell.api.shared.enums.ContactSystem
import net.blueshell.api.shared.job.ContactIntegrationJobProvider
import net.blueshell.api.shared.job.ContactJobs
import net.blueshell.api.shared.job.JobDefinition
import net.blueshell.api.shared.job.ListmonkJobs
import net.blueshell.api.shared.job.TrackedJobDispatcher
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

/**
 * Unit tests for [SpawnContactSyncsJob].
 *
 * No Spring context — instantiate directly with mocks.
 */
class SpawnContactSyncsJobTest {

    private val objectMapper = ObjectMapper()
    private val userService: UserService = mock()
    private val jobs: TrackedJobDispatcher = mock()

    private fun providerFor(system: ContactSystem): ContactIntegrationJobProvider {
        val payload = ListmonkJobs.ListmonkContactSyncPayload(userId = 0L)
        val def: JobDefinition<*> = ListmonkJobs.SyncContact
        return mock {
            whenever(mock.system).thenReturn(system)
            whenever(mock.contactSyncJob(any())).thenAnswer { invocation ->
                val userId = invocation.getArgument<Long>(0)
                Pair(def, ListmonkJobs.ListmonkContactSyncPayload(userId))
            }
        }
    }

    private fun userWithId(id: Long): User = mock<User>().also {
        whenever(it.id).thenReturn(id)
    }

    @Test
    fun `enqueues one contactSyncJob per user per provider`() {
        val provider1 = providerFor(ContactSystem.LISTMONK)
        val provider2 = providerFor(ContactSystem.BREVO)
        val job = SpawnContactSyncsJob(objectMapper, userService, listOf(provider1, provider2), jobs)

        val users = listOf(userWithId(1L), userWithId(2L))
        whenever(userService.findAll()).thenReturn(users.toMutableList())

        job.handle(objectMapper.writeValueAsString(ContactJobs.SpawnContactSyncsPayload()))

        // 2 users × 2 providers = 4 enqueue calls
        verify(jobs, times(4)).enqueue(any<String>(), any(), anyOrNull())
    }

    @Test
    fun `does nothing when no users exist`() {
        val provider = providerFor(ContactSystem.LISTMONK)
        val job = SpawnContactSyncsJob(objectMapper, userService, listOf(provider), jobs)

        whenever(userService.findAll()).thenReturn(mutableListOf())

        job.handle(objectMapper.writeValueAsString(ContactJobs.SpawnContactSyncsPayload()))

        verifyNoInteractions(jobs)
    }

    @Test
    fun `continues when provider throws for one user`() {
        val provider = mock<ContactIntegrationJobProvider>()
        whenever(provider.system).thenReturn(ContactSystem.LISTMONK)
        whenever(provider.contactSyncJob(1L)).thenThrow(RuntimeException("provider failure"))
        whenever(provider.contactSyncJob(2L)).thenReturn(
            Pair(ListmonkJobs.SyncContact, ListmonkJobs.ListmonkContactSyncPayload(2L))
        )
        val job = SpawnContactSyncsJob(objectMapper, userService, listOf(provider), jobs)

        val users = listOf(userWithId(1L), userWithId(2L))
        whenever(userService.findAll()).thenReturn(users.toMutableList())

        job.handle(objectMapper.writeValueAsString(ContactJobs.SpawnContactSyncsPayload()))

        // Only user 2 was successfully enqueued
        verify(jobs, times(1)).enqueue(any<String>(), any(), anyOrNull())
    }
}
