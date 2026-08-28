package net.blueshell.api.contact.domain

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
    fun `enqueues a single SyncAllContacts job`() {
        scheduler.syncAllContacts()

        verify(jobs).runAsync(
            eq(ContactJobs.SyncAllContacts),
            eq(ContactJobs.SyncAllContactsPayload())
        )
    }
}
