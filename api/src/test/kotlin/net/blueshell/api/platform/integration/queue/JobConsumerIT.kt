package net.blueshell.api.platform.integration.queue

import net.blueshell.api.factory.model.UserFactory
import net.blueshell.api.platform.integration.contact.ContactService
import net.blueshell.api.shared.enums.JobExecutionStatus
import net.blueshell.api.testsupport.ServiceTestSupport
import net.blueshell.api.domain.user.application.UserService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argThat
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean

class JobConsumerIT : ServiceTestSupport() {

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
            ContactJobs.SyncContact,
            ContactJobs.SyncContactPayload(user.id!!)
        )

        consumer.handle(JobMessage(execution.id!!, execution.jobType, execution.payload))

        verify(contacts).sync(argThat { id == user.id })
        val updated = jobExecutions.findById(execution.id!!).orElseThrow()
        assertEquals(JobExecutionStatus.SUCCESS, updated.status)
    }
}
