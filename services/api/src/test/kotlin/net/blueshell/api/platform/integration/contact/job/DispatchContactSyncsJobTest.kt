package net.blueshell.api.platform.integration.contact.job

import tools.jackson.databind.ObjectMapper
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.platform.integration.contact.adapter.ContactAdapter
import net.blueshell.api.platform.integration.contact.application.job.DispatchContactSyncsJob
import net.blueshell.api.shared.enums.ContactSystem
import net.blueshell.api.shared.job.ContactJobs
import net.blueshell.api.shared.job.JobDefinition
import net.blueshell.api.shared.job.TrackedJobDispatcher
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

/**
 * Unit tests for [DispatchContactSyncsJob].
 *
 * No Spring context — instantiate directly with mocks.
 */
class DispatchContactSyncsJobTest {

    private val objectMapper = ObjectMapper()
    private val userService: UserService = mock()
    private val jobs: TrackedJobDispatcher = mock()

    private fun adapterFor(system: ContactSystem): ContactAdapter = mock<ContactAdapter>().also {
        whenever(it.system).thenReturn(system)
    }

    private fun userWithId(id: Long): User = mock<User>().also {
        whenever(it.id).thenReturn(id)
    }

    @Test
    fun `enqueues one SyncContactForSystem job per user per adapter`() {
        val adapter1 = adapterFor(ContactSystem.LISTMONK)
        val adapter2 = adapterFor(ContactSystem.BREVO)
        val job = DispatchContactSyncsJob(objectMapper, userService, listOf(adapter1, adapter2), jobs)

        val users = listOf(userWithId(1L), userWithId(2L))
        whenever(userService.findAll()).thenReturn(users.toMutableList())

        job.handle(objectMapper.writeValueAsString(ContactJobs.DispatchContactSyncsPayload()))

        // 2 users × 2 adapters = 4 enqueue calls
        verify(jobs, times(4)).enqueue(any<JobDefinition<Any>>(), any())
    }

    @Test
    fun `does nothing when no users exist`() {
        val adapter = adapterFor(ContactSystem.LISTMONK)
        val job = DispatchContactSyncsJob(objectMapper, userService, listOf(adapter), jobs)

        whenever(userService.findAll()).thenReturn(mutableListOf())

        job.handle(objectMapper.writeValueAsString(ContactJobs.DispatchContactSyncsPayload()))

        verifyNoInteractions(jobs)
    }

    @Test
    fun `continues when enqueue throws for one user`() {
        val adapter = adapterFor(ContactSystem.LISTMONK)
        val job = DispatchContactSyncsJob(objectMapper, userService, listOf(adapter), jobs)

        val users = listOf(userWithId(1L), userWithId(2L))
        whenever(userService.findAll()).thenReturn(users.toMutableList())
        // First enqueue throws, second succeeds
        whenever(jobs.enqueue(any<JobDefinition<Any>>(), any()))
            .thenThrow(RuntimeException("enqueue failure"))
            .thenReturn(null)

        job.handle(objectMapper.writeValueAsString(ContactJobs.DispatchContactSyncsPayload()))

        // 2 attempts were made despite the first failure
        verify(jobs, times(2)).enqueue(any<JobDefinition<Any>>(), any())
    }
}
