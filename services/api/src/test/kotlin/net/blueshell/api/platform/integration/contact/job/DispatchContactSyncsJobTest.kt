package net.blueshell.api.platform.integration.contact.job

import tools.jackson.databind.ObjectMapper
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.platform.integration.contact.application.job.DispatchContactSyncsJob
import net.blueshell.api.platform.integration.sync.application.ContactSyncService
import net.blueshell.api.shared.job.ContactJobs
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.SimpleTransactionStatus

class DispatchContactSyncsJobTest {

    private val objectMapper = ObjectMapper()
    private val userService: UserService = mock()
    private val contactSync: ContactSyncService = mock()
    private val transactionManager: PlatformTransactionManager = mock<PlatformTransactionManager>().also {
        whenever(it.getTransaction(org.mockito.kotlin.any())).thenReturn(SimpleTransactionStatus())
    }
    private val job = DispatchContactSyncsJob(objectMapper, userService, contactSync, transactionManager)

    private fun userWithId(id: Long): User = mock<User>().also {
        whenever(it.id).thenReturn(id)
    }

    @Test
    fun `calls ContactSyncService once per user`() {
        val users = mutableListOf(userWithId(1L), userWithId(2L))
        whenever(userService.findAll()).thenReturn(users)

        job.handle(objectMapper.writeValueAsString(ContactJobs.DispatchContactSyncsPayload()))

        verify(contactSync).sync(eq(1L))
        verify(contactSync).sync(eq(2L))
    }

    @Test
    fun `does nothing when no users exist`() {
        whenever(userService.findAll()).thenReturn(mutableListOf())

        job.handle(objectMapper.writeValueAsString(ContactJobs.DispatchContactSyncsPayload()))

        verifyNoInteractions(contactSync)
    }

    @Test
    fun `continues iterating when sync throws for one user`() {
        val users = mutableListOf(userWithId(1L), userWithId(2L))
        whenever(userService.findAll()).thenReturn(users)
        doThrow(RuntimeException("sync failure")).whenever(contactSync).sync(1L)

        job.handle(objectMapper.writeValueAsString(ContactJobs.DispatchContactSyncsPayload()))

        verify(contactSync, times(1)).sync(eq(1L))
        verify(contactSync, times(1)).sync(eq(2L))
    }

    @Test
    fun `opens a fresh transaction per user`() {
        val users = mutableListOf(userWithId(1L), userWithId(2L), userWithId(3L))
        whenever(userService.findAll()).thenReturn(users)

        job.handle(objectMapper.writeValueAsString(ContactJobs.DispatchContactSyncsPayload()))

        // One getTransaction call per user proves the TransactionTemplate with
        // REQUIRES_NEW propagation is invoked individually — i.e. a failing sync
        // can only mark its own transaction rollback-only, not the dispatcher's.
        verify(transactionManager, times(3)).getTransaction(org.mockito.kotlin.any())
    }
}
