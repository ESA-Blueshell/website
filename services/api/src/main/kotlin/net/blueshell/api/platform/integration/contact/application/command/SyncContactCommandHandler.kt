package net.blueshell.api.platform.integration.contact.application.command

import net.blueshell.api.platform.integration.contact.adapter.ContactAdapter
import net.blueshell.api.platform.integration.contact.adapter.ContactData
import net.blueshell.api.platform.integration.contact.adapter.toContactData
import net.blueshell.api.platform.integration.contact.persistence.Contact
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactRepository
import net.blueshell.api.platform.integration.queue.SyncLock
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.shared.command.CommandHandler
import net.blueshell.api.shared.enums.ContactSystem
import net.blueshell.api.shared.job.NonRetryableJobException
import net.blueshell.api.shared.job.SyncContactCommand
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import kotlin.reflect.KClass

/**
 * Handles [SyncContactCommand]: creates, updates, or deletes a user's contact record
 * in one external system.
 *
 * Concurrency: per-(userId, system) serialization is enforced via [SyncLock]. The
 * handler re-reads User + Contact inside the lock so a successor run that was
 * queued while a prior run was in flight observes the latest state, not the
 * state captured when the successor was enqueued. When the locally stored
 * snapshot already equals the desired state, the external API call is skipped —
 * this collapses bursts of rapid upstream changes to at most one redundant
 * push and makes the handler safe to retry.
 *
 * State machine:
 * - Soft-deleted contact + external ID exists → delete from external system, clear ID
 * - Active contact + no external ID → create in external system, store ID + snapshot
 * - Active contact + external ID + snapshot stale → update in external system, refresh snapshot
 * - Active contact + external ID + snapshot fresh → no-op
 */
@Component
class SyncContactCommandHandler(
    private val contactAdapters: List<ContactAdapter>,
    private val contactRepository: ContactRepository,
    private val userService: UserService,
    private val syncLock: SyncLock,
) : CommandHandler<SyncContactCommand, Unit> {

    override val commandType: KClass<SyncContactCommand> = SyncContactCommand::class

    @Transactional
    override fun handle(command: SyncContactCommand) {
        val adapter = contactAdapters.find { it.system == command.system }
            ?: throw NonRetryableJobException("No ContactAdapter registered for system ${command.system}")

        val lockName = lockName(command.userId, command.system)
        syncLock.withLock(lockName) {
            syncOne(command.userId, command.system, adapter)
        }
    }

    private fun syncOne(userId: Long, system: ContactSystem, adapter: ContactAdapter) {
        val contact = contactRepository.findByUserIdIncludingDeleted(userId)

        if (contact != null && contact.isSoftDeleted) {
            val externalId = contact.externalId(system) ?: run {
                log.debug("Contact for user {} deleted but has no {} ID — nothing to delete", userId, system)
                return
            }
            adapter.deleteContact(externalId)
            contact.clearExternalId(system)
            contactRepository.save(contact)
            log.info("Deleted {} contact for user {}", system, userId)
            return
        }

        val record = contact ?: contactRepository.save(Contact(userId = userId))
        val user = userService.findById(userId)
        val data = user.toContactData()

        val existingId = record.externalId(system)
        if (existingId == null) {
            val newId = adapter.createContact(data)
            record.setExternalId(system, newId)
            storeSnapshot(record, data)
            log.info("Created {} contact for user {}", system, userId)
            return
        }

        if (record.matchesSnapshot(
                email = data.email,
                firstName = data.firstName,
                lastName = data.lastName,
                phoneNumber = data.phoneNumber,
                newsletter = data.newsletter,
                isMember = data.isMember,
            )
        ) {
            log.debug("{} contact for user {} already matches snapshot — skipping external update", system, userId)
            return
        }

        adapter.updateContact(existingId, data)
        storeSnapshot(record, data)
        log.debug("Updated {} contact for user {}", system, userId)
    }

    private fun storeSnapshot(record: Contact, data: ContactData) {
        record.updateSnapshot(
            data.email,
            data.firstName,
            data.lastName,
            data.phoneNumber,
            data.newsletter,
            data.isMember,
        )
        contactRepository.save(record)
    }

    private fun lockName(userId: Long, system: ContactSystem): String =
        "contact-sync:$userId:${system.name}"

    companion object {
        private val log = LoggerFactory.getLogger(SyncContactCommandHandler::class.java)
    }
}
