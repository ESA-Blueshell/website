package net.blueshell.api.platform.integration.contact.application

import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.application.contact.ContactData
import net.blueshell.api.domain.user.application.contact.ContactSyncAdapter
import net.blueshell.api.domain.user.application.contact.ContactSystem
import net.blueshell.api.domain.user.application.contact.toContactData
import net.blueshell.api.platform.integration.contact.persistence.BrevoContact
import net.blueshell.api.platform.integration.contact.persistence.Contact
import net.blueshell.api.platform.integration.contact.persistence.ListmonkContact
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Orchestrates contact synchronization across all registered [ContactSyncAdapter] implementations.
 *
 * Responsibilities:
 * - Delta check: skips sync if snapshot matches current ContactData
 * - Create vs update: calls createContact on first sync per system, updateContact on subsequent syncs
 * - Fault tolerance: one adapter failure does not prevent other adapters from running
 * - Snapshot update: persists what was actually sent, so failed adapters are retried next cycle
 */
@Service
class ContactSyncService(
    private val contactSyncAdapters: List<ContactSyncAdapter>,
    private val contactRepository: ContactRepository,
    private val userService: UserService,
) {
    /**
     * Syncs the user's contact data to all registered external systems.
     *
     * - First call: creates a [Contact] and calls [ContactSyncAdapter.createContact] per system.
     * - Subsequent calls with unchanged data: no-op (delta check).
     * - Subsequent calls with changed data: calls [ContactSyncAdapter.updateContact] per system.
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

        for (adapter in contactSyncAdapters) {
            try {
                when (adapter.system) {
                    ContactSystem.LISTMONK -> {
                        val existing = record.listmonkContact
                        if (existing == null) {
                            val externalId = adapter.createContact(data)
                            record.listmonkContact = ListmonkContact(contact = record, externalId = externalId)
                        } else {
                            adapter.updateContact(existing.externalId, data)
                        }
                    }
                    ContactSystem.BREVO -> {
                        val existing = record.brevoContact
                        if (existing == null) {
                            val externalId = adapter.createContact(data)
                            record.brevoContact = BrevoContact(contact = record, externalId = externalId)
                        } else {
                            adapter.updateContact(existing.externalId, data)
                        }
                    }
                }
            } catch (e: Exception) {
                log.error("Adapter {} failed to sync contact for user {}", adapter.system, userId, e)
            }
        }

        record.updateSnapshot(data)
        contactRepository.save(record)
    }

    /**
     * Deletes the contact from all external systems and soft-deletes the [Contact].
     */
    @Transactional
    fun deleteContact(userId: Long) {
        val record = contactRepository.findByUserId(userId) ?: return

        for (adapter in contactSyncAdapters) {
            try {
                val externalId = when (adapter.system) {
                    ContactSystem.LISTMONK -> record.listmonkContact?.externalId
                    ContactSystem.BREVO -> record.brevoContact?.externalId
                }
                if (externalId != null) {
                    adapter.deleteContact(externalId)
                }
            } catch (e: Exception) {
                log.error("Adapter {} failed to delete contact for user {}", adapter.system, userId, e)
            }
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
