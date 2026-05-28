package net.blueshell.api.platform.integration.contact.application.command

import net.blueshell.api.platform.integration.contact.adapter.ContactListAdapter
import net.blueshell.api.platform.integration.contact.adapter.ExternalContactGoneException
import net.blueshell.api.platform.integration.contact.persistence.Contact
import net.blueshell.api.platform.integration.contact.persistence.ContactList
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactListMembershipRepository
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactListRepository
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactRepository
import net.blueshell.api.shared.command.CommandHandler
import net.blueshell.api.shared.enums.ContactSystem
import net.blueshell.api.shared.job.ContactJobs
import net.blueshell.api.shared.job.NonRetryableJobException
import net.blueshell.api.shared.job.SyncListMembershipCommand
import net.blueshell.api.shared.job.TrackedJobDispatcher
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import kotlin.reflect.KClass

/**
 * Handles [SyncListMembershipCommand]: adds or removes a user from a contact
 * list in one external system, and self-heals stale local pairings.
 */
@Component
class SyncListMembershipCommandHandler(
    private val listAdapters: List<ContactListAdapter>,
    private val contactRepository: ContactRepository,
    private val contactListRepository: ContactListRepository,
    private val contactListMembershipRepository: ContactListMembershipRepository,
    private val jobs: TrackedJobDispatcher,
) : CommandHandler<SyncListMembershipCommand, Unit> {

    override val commandType: KClass<SyncListMembershipCommand> = SyncListMembershipCommand::class

    @Transactional
    override fun handle(command: SyncListMembershipCommand) {
        val adapter = adapterFor(command.system)
        val contact = contactRepository.findByUserId(command.userId)
        val list = contactListRepository.findById(command.contactListId).orElse(null)

        if (hasLocalMembership(contact, command.contactListId)) {
            addMembership(adapter, command, contact, list)
        } else {
            removeMembership(adapter, command, contact, list)
        }
    }

    private fun adapterFor(system: ContactSystem): ContactListAdapter =
        listAdapters.find { it.system == system }
            ?: throw NonRetryableJobException("No ContactListAdapter registered for system $system")

    private fun hasLocalMembership(contact: Contact?, contactListId: Long): Boolean {
        val contactId = contact?.id ?: return false
        return contactListMembershipRepository.findByContactIdAndContactListId(contactId, contactListId) != null
    }

    private fun addMembership(
        adapter: ContactListAdapter,
        command: SyncListMembershipCommand,
        contact: Contact?,
        list: ContactList?,
    ) {
        val externalContactId = contact?.externalId(command.system)
            ?: enqueueContactSyncAndRetry(command, "Contact not yet synced to ${command.system}")

        val externalListId = list?.externalListId(command.system)
            ?: throw NonRetryableJobException(
                "List ${command.contactListId} has no ${command.system} external ID — cannot add contact"
            )

        try {
            adapter.addToList(externalContactId, externalListId)
            log.debug("Added user {} to {} list {}", command.userId, command.system, command.contactListId)
        } catch (e: ExternalContactGoneException) {
            contact?.let {
                it.clearExternalId(command.system)
                contactRepository.save(it)
            }
            enqueueContactSyncAndRetry(
                command,
                "Upstream contact for user ${command.userId} (${command.system}) was gone — cleared pairing",
                cause = e,
            )
        }
    }

    private fun removeMembership(
        adapter: ContactListAdapter,
        command: SyncListMembershipCommand,
        contact: Contact?,
        list: ContactList?,
    ) {
        val externalContactId = contact?.externalId(command.system)
        val externalListId = list?.externalListId(command.system)
        if (externalContactId == null || externalListId == null) {
            log.debug(
                "No {} IDs for user {}/list {} — skipping removal",
                command.system, command.userId, command.contactListId,
            )
            return
        }
        adapter.removeFromList(externalContactId, externalListId)
        log.debug("Removed user {} from {} list {}", command.userId, command.system, command.contactListId)
    }

    private fun enqueueContactSyncAndRetry(
        command: SyncListMembershipCommand,
        message: String,
        cause: Throwable? = null,
    ): Nothing {
        jobs.enqueue(ContactJobs.SyncContact, ContactJobs.SyncContactPayload(command.userId))
        throw RetryableContactNotSyncedException(
            "$message — enqueued sync, will retry",
            cause,
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(SyncListMembershipCommandHandler::class.java)
    }
}

internal class RetryableContactNotSyncedException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
