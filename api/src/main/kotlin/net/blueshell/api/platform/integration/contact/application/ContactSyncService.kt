package net.blueshell.api.platform.integration.contact.application

import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.application.contact.ContactData
import net.blueshell.api.domain.user.application.contact.ContactSyncAdapter
import net.blueshell.api.domain.user.application.contact.toContactData
import net.blueshell.api.platform.integration.contact.persistence.Contact
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactRepository
import net.blueshell.api.shared.job.ContactJobs
import net.blueshell.api.shared.job.TrackedJobDispatcher
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Orchestrates contact synchronization by maintaining the local [Contact] snapshot
 * and dispatching per-system sync jobs.
 *
 * Responsibilities:
 * - Delta check: skips dispatch if snapshot matches current ContactData
 * - Snapshot update: persists what was sent, ensuring per-system jobs are not re-dispatched
 *   if data hasn't changed
 * - Per-system isolation: dispatches one [ContactJobs.SyncContactToSystem] job per active adapter,
 *   so one slow or failing system does not block the other
 */
@Service
class ContactSyncService(
    private val contactSyncAdapters: List<ContactSyncAdapter>,
    private val contactRepository: ContactRepository,
    private val userService: UserService,
    private val jobs: TrackedJobDispatcher,
) {
    /**
     * Ensures the [Contact] DB record is current, then dispatches per-system sync jobs.
     *
     * - First call: creates a [Contact] record and dispatches [ContactJobs.SyncContactToSystem] per adapter.
     * - Subsequent calls with unchanged data: no-op (delta check).
     * - Subsequent calls with changed data: updates snapshot and re-dispatches.
     */
    @Transactional
    fun syncContact(userId: Long) {
        val user = userService.findById(userId)
        val data = user.toContactData()

        val record = contactRepository.findByUserId(userId)
            ?: Contact(userId = userId)

        if (record.id != null && !record.hasChangedFrom(data)) {
            log.debug("Contact unchanged for user {} — skipping sync", userId)
            return
        }

        record.updateSnapshot(data)
        contactRepository.save(record)

        contactSyncAdapters.forEach { adapter ->
            jobs.enqueue(
                ContactJobs.SyncContactToSystem,
                ContactJobs.SyncContactToSystemPayload(userId, adapter.system)
            )
        }
    }

    /**
     * Captures system-specific external IDs, dispatches per-system delete jobs,
     * then removes the [Contact] record.
     *
     * External IDs are read before deletion so the job payloads are self-contained.
     */
    @Transactional
    fun deleteContact(userId: Long) {
        val record = contactRepository.findByUserId(userId) ?: return

        contactSyncAdapters.forEach { adapter ->
            val externalId = record.externalId(adapter.system) ?: return@forEach
            jobs.enqueue(
                ContactJobs.DeleteContactFromSystem,
                ContactJobs.DeleteContactFromSystemPayload(externalId, adapter.system)
            )
        }

        contactRepository.delete(record)
    }

    fun findByUserId(userId: Long): Contact? = contactRepository.findByUserId(userId)

    companion object {
        private val log = LoggerFactory.getLogger(ContactSyncService::class.java)
    }
}

// ── private extension helpers ─────────────────────────────────────────────────

private fun Contact.hasChangedFrom(data: ContactData): Boolean =
    syncedEmail != data.email ||
    syncedFirstName != data.firstName ||
    syncedLastName != data.lastName ||
    syncedPhoneNumber != data.phoneNumber ||
    syncedNewsletter != data.newsletter ||
    syncedIsMember != data.isMember

private fun Contact.updateSnapshot(data: ContactData) {
    syncedEmail = data.email
    syncedFirstName = data.firstName
    syncedLastName = data.lastName
    syncedPhoneNumber = data.phoneNumber
    syncedNewsletter = data.newsletter
    syncedIsMember = data.isMember
}
