package net.blueshell.api.platform.integration.contact.job

import net.blueshell.api.platform.integration.contact.application.job.RemoveContactJob
import net.blueshell.api.platform.integration.sync.application.ContactSyncService
import net.blueshell.api.shared.job.ContactJobs
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import tools.jackson.databind.ObjectMapper

class RemoveContactJobTest {

    private val objectMapper = ObjectMapper()
    private val contactSync: ContactSyncService = mock()
    private val job = RemoveContactJob(objectMapper, contactSync)

    @Test
    fun `delegates to ContactSyncService remove with the payload userId`() {
        job.handle(objectMapper.writeValueAsString(ContactJobs.RemoveContactPayload(99L)))

        verify(contactSync).remove(eq(99L))
    }
}
