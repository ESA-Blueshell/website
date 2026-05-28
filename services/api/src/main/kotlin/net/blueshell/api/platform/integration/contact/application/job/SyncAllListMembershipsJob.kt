package net.blueshell.api.platform.integration.contact.application.job

import net.blueshell.api.platform.integration.contact.adapter.ContactListAdapter
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactListMembershipRepository
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import net.blueshell.api.shared.job.ContactJobs
import net.blueshell.api.shared.job.SyncListMembershipCommand
import net.blueshell.api.shared.job.TrackedJobDispatcher
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * Iterates all active [ContactListMembership] records and enqueues a
 * [ContactJobs.SyncListMembership] job per membership × registered list adapter.
 *
 * Parallel to [SpawnContactSyncsJob] for contacts: triggered daily to ensure all
 * list memberships are in sync across all external systems.
 *
 * Individual failures are logged and skipped so one bad record cannot block the rest.
 */
@Component
class SyncAllListMembershipsJob(
    objectMapper: ObjectMapper,
    private val contactListMembershipRepository: ContactListMembershipRepository,
    private val listAdapters: List<ContactListAdapter>,
    private val jobs: TrackedJobDispatcher,
) : AbstractJsonJobHandler<ContactJobs.SyncAllListMembershipsPayload>(
    objectMapper,
    ContactJobs.SyncAllListMemberships.payloadType,
) {
    override val jobType: String = ContactJobs.SyncAllListMemberships.type

    override fun handlePayload(payload: ContactJobs.SyncAllListMembershipsPayload) {
        val memberships = contactListMembershipRepository.findAll()
        log.info(
            "Spawning list membership syncs for {} memberships × {} systems",
            memberships.size, listAdapters.size
        )

        memberships.forEach { membership ->
            listAdapters.forEach { adapter ->
                runCatching {
                    jobs.enqueue(
                        ContactJobs.SyncListMembership,
                        SyncListMembershipCommand(
                            userId = membership.contact.userId,
                            contactListId = membership.contactList.id!!,
                            system = adapter.system,
                        )
                    )
                }.onFailure { e ->
                    log.error(
                        "Failed to enqueue list sync for user={} list={} system={}: {}",
                        membership.contact.userId, membership.contactList.id, adapter.system, e.message
                    )
                }
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(SyncAllListMembershipsJob::class.java)
    }
}
