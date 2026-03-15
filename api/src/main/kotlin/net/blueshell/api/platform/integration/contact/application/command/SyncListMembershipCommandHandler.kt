package net.blueshell.api.platform.integration.contact.application.command

import net.blueshell.api.platform.integration.contact.adapter.ContactListAdapter
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactListMembershipRepository
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactListRepository
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactRepository
import net.blueshell.api.shared.command.CommandHandler
import net.blueshell.api.shared.job.NonRetryableJobException
import net.blueshell.api.shared.job.SyncListMembershipCommand
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import kotlin.reflect.KClass

/**
 * Handles [SyncListMembershipCommand]: adds or removes a user from a contact list
 * in one external system.
 *
 * Selects the correct [ContactListAdapter] by [SyncListMembershipCommand.system] at runtime.
 *
 * Logic:
 * - Active [ContactListMembership] exists in DB → add to external list
 *   (retryable if contact not yet synced; non-retryable if list has no external ID)
 * - No membership exists → remove from external list (no-op if IDs absent)
 */
@Component
class SyncListMembershipCommandHandler(
    private val listAdapters: List<ContactListAdapter>,
    private val contactRepository: ContactRepository,
    private val contactListRepository: ContactListRepository,
    private val contactListMembershipRepository: ContactListMembershipRepository,
) : CommandHandler<SyncListMembershipCommand, Unit> {

    override val commandType: KClass<SyncListMembershipCommand> = SyncListMembershipCommand::class

    override fun handle(command: SyncListMembershipCommand) {
        val adapter = listAdapters.find { it.system == command.system }
            ?: throw NonRetryableJobException("No ContactListAdapter registered for system ${command.system}")

        val contact = contactRepository.findByUserId(command.userId)
        val externalContactId = contact?.externalId(command.system)

        val list = contactListRepository.findById(command.contactListId).orElse(null)
        val externalListId = list?.externalListId(command.system)

        val hasMembership = contact?.id?.let { contactId ->
            contactListMembershipRepository.findByContactIdAndContactListId(contactId, command.contactListId) != null
        } ?: false

        if (hasMembership) {
            if (externalContactId == null) {
                throw RetryableContactNotSyncedException(
                    "Contact not yet synced to ${command.system} for user ${command.userId} — will retry"
                )
            }
            if (externalListId == null) {
                throw NonRetryableJobException(
                    "List ${command.contactListId} has no ${command.system} external ID — cannot add contact"
                )
            }
            adapter.addToList(externalContactId, externalListId)
            log.debug("Added user {} to {} list {}", command.userId, command.system, command.contactListId)
        } else {
            if (externalContactId != null && externalListId != null) {
                adapter.removeFromList(externalContactId, externalListId)
                log.debug("Removed user {} from {} list {}", command.userId, command.system, command.contactListId)
            } else {
                log.debug(
                    "No {} IDs for user {}/list {} — skipping removal",
                    command.system, command.userId, command.contactListId
                )
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(SyncListMembershipCommandHandler::class.java)
    }
}

private class RetryableContactNotSyncedException(message: String) : RuntimeException(message)
