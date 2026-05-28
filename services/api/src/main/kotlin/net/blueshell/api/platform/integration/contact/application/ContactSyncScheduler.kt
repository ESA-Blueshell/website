package net.blueshell.api.platform.integration.contact.application

import net.blueshell.api.shared.job.ContactJobs
import net.blueshell.api.shared.job.TrackedJobDispatcher
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Daily-sync triggers. The methods run in order: period-list reconciliation
 * at 01:00, full contact sync at 02:00, list-membership sync at 02:30 — so
 * by the time the per-membership pass runs, every period has its list and
 * every contact has been pushed.
 */
@Component
class ContactSyncScheduler(
    private val jobs: TrackedJobDispatcher,
) {
    @Scheduled(cron = "\${contact.ensure-period-lists-cron:0 0 1 * * *}")
    fun ensureContributionPeriodLists() {
        jobs.enqueue(
            ContactJobs.EnsureContributionPeriodLists,
            ContactJobs.EnsureContributionPeriodListsPayload(),
        )
    }

    @Scheduled(cron = "\${contact.sync-cron:0 0 2 * * *}")
    fun syncAllContacts() {
        log.info("Scheduling contact sync spawn job")
        jobs.enqueue(ContactJobs.DispatchContactSyncs, ContactJobs.DispatchContactSyncsPayload())
    }

    @Scheduled(cron = "\${contact.list-sync-cron:0 30 2 * * *}")
    fun syncAllListMemberships() {
        log.info("Scheduling list membership sync spawn job")
        jobs.enqueue(ContactJobs.DispatchListMembershipSyncs, ContactJobs.DispatchListMembershipSyncsPayload())
    }

    companion object {
        private val log = LoggerFactory.getLogger(ContactSyncScheduler::class.java)
    }
}
