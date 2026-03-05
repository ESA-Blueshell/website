package net.blueshell.api.domain.user.application.listener

import net.blueshell.api.domain.user.application.event.UserDeleted
import net.blueshell.api.shared.job.ContactJobs
import net.blueshell.api.testsupport.ServiceTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class ErasureListenerTest : ServiceTestSupport() {

    @Autowired
    private lateinit var listener: ErasureListener

    @Test
    fun `dispatches DeleteContact job when UserDeleted is published`() {
        val event = UserDeleted(userId = 1L)

        listener.onDeleted(event)

        val jobs = findJobsByType(ContactJobs.DeleteContact.type)
        assertThat(jobs)
            .describedAs("Should schedule one DeleteContact job")
            .hasSize(1)

        val payload = jobs.first().payload
        assertThat(payload).contains("\"userId\":1")
    }
}
