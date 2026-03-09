package net.blueshell.api.platform.integration.mock

import net.blueshell.api.shared.enums.ContactSystem
import net.blueshell.api.shared.job.ContactIntegrationJobProvider
import net.blueshell.api.shared.job.JobDefinition
import net.blueshell.api.shared.job.ListmonkJobs
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * Mock [ContactIntegrationJobProvider] for testing.
 *
 * Reports [ContactSystem.LISTMONK] so tests exercise the Listmonk path.
 * Returns [ListmonkJobs] definitions so job type strings are resolvable in tests.
 */
@Component
@Primary
@Profile("test | dev")
class MockContactIntegrationJobProvider : ContactIntegrationJobProvider {

    override val system: ContactSystem = ContactSystem.LISTMONK

    override fun contactSyncJob(userId: Long): Pair<JobDefinition<*>, Any> =
        Pair(ListmonkJobs.SyncContact, ListmonkJobs.ListmonkContactSyncPayload(userId))

    override fun listSyncJob(userId: Long, contactListId: Long): Pair<JobDefinition<*>, Any> =
        Pair(ListmonkJobs.SyncListMembership, ListmonkJobs.ListmonkListSyncPayload(userId, contactListId))
}
