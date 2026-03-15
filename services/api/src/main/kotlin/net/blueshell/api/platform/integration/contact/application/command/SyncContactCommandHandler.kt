package net.blueshell.api.platform.integration.contact.application.command

import net.blueshell.api.platform.integration.contact.adapter.ContactAdapter
import net.blueshell.api.platform.integration.contact.adapter.toContactData
import net.blueshell.api.platform.integration.contact.persistence.Contact
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactRepository
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.shared.command.CommandHandler
import net.blueshell.api.shared.job.NonRetryableJobException
import net.blueshell.api.shared.job.SyncContactCommand
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import kotlin.reflect.KClass

/**
 * Handles [SyncContactCommand]: creates, updates, or deletes a user's contact record
 * in one external system.
 *
 * Selects the correct [ContactAdapter] by [SyncContactCommand.system] at runtime.
 * Registered adapters are injected as a list; adding a new integration simply requires
 * registering a new [ContactAdapter] bean — no new command or handler needed.
 *
 * State machine:
 * - Soft-deleted contact + external ID exists → delete from external system, clear ID
 * - Active contact + no external ID → create in external system, store ID + snapshot
 * - Active contact + external ID → update in external system, refresh snapshot
 */
@Component
class SyncContactCommandHandler(
    private val contactAdapters: List<ContactAdapter>,
    private val contactRepository: ContactRepository,
    private val userService: UserService,
) : CommandHandler<SyncContactCommand, Unit> {

    override val commandType: KClass<SyncContactCommand> = SyncContactCommand::class

    override fun handle(command: SyncContactCommand) {
        val adapter = contactAdapters.find { it.system == command.system }
            ?: throw NonRetryableJobException("No ContactAdapter registered for system ${command.system}")

        val userId = command.userId
        val contact = contactRepository.findByUserIdIncludingDeleted(userId)

        if (contact != null && contact.isSoftDeleted) {
            val externalId = contact.externalId(command.system) ?: run {
                log.debug("Contact for user {} deleted but has no {} ID — nothing to delete", userId, command.system)
                return
            }
            adapter.deleteContact(externalId)
            contact.clearExternalId(command.system)
            contactRepository.save(contact)
            log.info("Deleted {} contact for user {}", command.system, userId)
            return
        }

        val record = contact ?: contactRepository.save(Contact(userId = userId))
        val user = userService.findById(userId)
        val data = user.toContactData()

        val existingId = record.externalId(command.system)
        if (existingId == null) {
            val newId = adapter.createContact(data)
            record.setExternalId(command.system, newId)
            record.updateSnapshot(data.email, data.firstName, data.lastName, data.phoneNumber, data.newsletter, data.isMember)
            contactRepository.save(record)
            log.info("Created {} contact for user {}", command.system, userId)
        } else {
            adapter.updateContact(existingId, data)
            record.updateSnapshot(data.email, data.firstName, data.lastName, data.phoneNumber, data.newsletter, data.isMember)
            contactRepository.save(record)
            log.debug("Updated {} contact for user {}", command.system, userId)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(SyncContactCommandHandler::class.java)
    }
}
