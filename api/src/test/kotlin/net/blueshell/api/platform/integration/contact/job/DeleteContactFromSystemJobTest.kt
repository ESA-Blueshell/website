package net.blueshell.api.platform.integration.contact.job

import tools.jackson.databind.ObjectMapper
import net.blueshell.api.domain.user.application.contact.ContactSyncAdapter
import net.blueshell.api.domain.user.application.contact.ContactSystem
import net.blueshell.api.platform.integration.contact.application.job.DeleteContactFromSystemJob
import net.blueshell.api.shared.job.ContactJobs
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for [DeleteContactFromSystemJob].
 *
 * No Spring context — instantiate directly with mocks.
 */
class DeleteContactFromSystemJobTest {

    private val objectMapper = ObjectMapper()
    private val listmonkAdapter: ContactSyncAdapter = mock {
        whenever(mock.system).thenReturn(ContactSystem.LISTMONK)
    }

    private val job = DeleteContactFromSystemJob(
        objectMapper = objectMapper,
        adapters = listOf(listmonkAdapter),
    )

    @Test
    fun `calls deleteContact with the given externalId`() {
        job.handle(payload(ContactJobs.DeleteContactFromSystemPayload(externalId = 77L, system = ContactSystem.LISTMONK)))

        verify(listmonkAdapter).deleteContact(77L)
    }

    @Test
    fun `skips gracefully when no adapter registered for system`() {
        // BREVO system but only LISTMONK adapter registered
        job.handle(payload(ContactJobs.DeleteContactFromSystemPayload(externalId = 77L, system = ContactSystem.BREVO)))

        verify(listmonkAdapter, never()).deleteContact(any())
    }

    private fun payload(p: Any) = objectMapper.writeValueAsString(p)
}
