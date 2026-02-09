package net.blueshell.api.platform.integration.queue

import net.blueshell.api.factory.model.UserFactory
import net.blueshell.api.platform.integration.contact.ContactService
import net.blueshell.api.platform.integration.contact.job.SyncContactJobHandler
import net.blueshell.api.platform.integration.contact.job.SyncContactPayload
import net.blueshell.api.platform.integration.job.model.JobExecutionStatus
import net.blueshell.api.testsupport.EventIntegrationTestSupport
import net.blueshell.api.user.application.UserService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.mockito.kotlin.argThat
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean

class JobConsumerIT : EventIntegrationTestSupport() {

    @Autowired
    private lateinit var dispatcher: JobDispatcher

    @Autowired
    private lateinit var consumer: JobConsumer

    @Autowired
    private lateinit var users: UserService

    @Autowired
    private lateinit var userFactory: UserFactory

    @MockBean
    private lateinit var contacts: ContactService

    @Test
    fun `dispatching sync contact job triggers handler`() {
        val user = users.create(userFactory.createBasic())
        val execution = dispatcher.enqueue(
            SyncContactJobHandler.JOB_TYPE,
            SyncContactPayload(user.id!!)
        )

        consumer.handle(JobMessage(execution.id!!, execution.jobType, execution.payload))

        verify(contacts).sync(argThat { id == user.id })
        val updated = jobExecutions.findById(execution.id!!).orElseThrow()
        assertEquals(JobExecutionStatus.SUCCESS, updated.status)
    }
}
