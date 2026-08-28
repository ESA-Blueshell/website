package net.blueshell.api.contact.domain

import net.blueshell.api.shared.job.ContactJobs
import net.blueshell.api.shared.job.TrackedJobDispatcher
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Daily contact-aggregate sync trigger. Pushes the current state of every
 * user as a Contact to every registered contact target (Brevo + future
 * integrations) at 02:00.
 *
 * Cohort-membership convergence is event-driven through `CohortRuleListener`
 * — there is no nightly equivalent for it. The cohort engine reconciles on
 * every fact-changing event for the affected user.
 */
@Component
class ContactSyncScheduler(
    private val jobs: TrackedJobDispatcher,
) {
    @Scheduled(cron = "\${contact.sync-cron:0 0 2 * * *}")
    fun syncAllContacts() {
        log.info("Scheduling contact sync spawn job")
        jobs.runAsync(ContactJobs.SyncAllContacts, ContactJobs.SyncAllContactsPayload())
    }

    companion object {
        private val log = LoggerFactory.getLogger(ContactSyncScheduler::class.java)
    }
}
