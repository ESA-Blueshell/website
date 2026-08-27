package net.blueshell.api.platform.integration.sync.job

import net.blueshell.api.platform.integration.sync.application.job.SyncContactJob
import net.blueshell.api.platform.integration.sync.application.ContactSyncService
import net.blueshell.api.shared.job.ContactJobs
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import tools.jackson.databind.ObjectMapper

class SyncContactJobTest {

    private val objectMapper = ObjectMapper()
    private val contactSync: ContactSyncService = mock()
    private val job = SyncContactJob(objectMapper, contactSync)

    @Test
    fun `delegates to ContactSyncService with the payload userId`() {
        job.handle(objectMapper.writeValueAsString(ContactJobs.SyncContactPayload(42L)))

        verify(contactSync).sync(eq(42L))
    }
}
