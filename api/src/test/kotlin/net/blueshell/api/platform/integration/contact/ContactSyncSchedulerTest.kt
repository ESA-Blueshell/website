package net.blueshell.api.platform.integration.contact

import net.blueshell.api.platform.integration.contact.application.ContactSyncScheduler
import net.blueshell.api.shared.job.ContactJobs
import net.blueshell.api.shared.job.TrackedJobDispatcher
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

/**
 * Unit tests for [ContactSyncScheduler].
 *
 * No Spring context — instantiate directly with mocks.
 */
class ContactSyncSchedulerTest {

    private val jobs: TrackedJobDispatcher = mock()
    private val scheduler = ContactSyncScheduler(jobs)

    @Test
    fun `enqueues a single DispatchContactSyncs job`() {
        scheduler.syncAllContacts()

        verify(jobs).enqueue(
            eq(ContactJobs.DispatchContactSyncs),
            eq(ContactJobs.DispatchContactSyncsPayload())
        )
    }

    @Test
    fun `enqueues a single DispatchListMembershipSyncs job`() {
        scheduler.syncAllListMemberships()

        verify(jobs).enqueue(
            eq(ContactJobs.DispatchListMembershipSyncs),
            eq(ContactJobs.DispatchListMembershipSyncsPayload())
        )
    }
}
