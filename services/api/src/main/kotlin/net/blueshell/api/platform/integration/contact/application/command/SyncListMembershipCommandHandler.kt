package net.blueshell.api.platform.integration.contact.application.command

import net.blueshell.api.platform.integration.contact.adapter.ContactListAdapter
import net.blueshell.api.platform.integration.contact.adapter.ExternalContactGoneException
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactListMembershipRepository
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactListRepository
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactRepository
import net.blueshell.api.shared.command.CommandHandler
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
 * list in one external system.
 *
 * Self-healing:
 * - If the contact has no external id for [SyncListMembershipCommand.system],
 *   we enqueue a contact sync and throw retryable so the next retry runs
 *   after the contact pairing is in place.
 * - If the adapter reports the external contact is gone (deleted/merged
 *   upstream), we clear the stale pairing, enqueue a contact sync to
 *   re-establish it, and throw retryable so the add is reattempted after the
 *   contact lands at its new id.
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
                // Without this nudge the list job would loop on its retry
                // schedule until something else syncs the contact.
                jobs.enqueue(ContactJobs.SyncContact, ContactJobs.SyncContactPayload(command.userId))
                throw RetryableContactNotSyncedException(
                    "Contact not yet synced to ${command.system} for user ${command.userId} — " +
                        "enqueued sync, will retry"
                )
            }
            if (externalListId == null) {
                throw NonRetryableJobException(
                    "List ${command.contactListId} has no ${command.system} external ID — cannot add contact"
                )
            }
            try {
                adapter.addToList(externalContactId, externalListId)
                log.debug("Added user {} to {} list {}", command.userId, command.system, command.contactListId)
            } catch (e: ExternalContactGoneException) {
                // Upstream contact is gone; the local pairing is stale. Clear
                // it and trigger a contact sync so the next retry can re-add.
                if (contact != null) {
                    contact.clearExternalId(command.system)
                    contactRepository.save(contact)
                }
                jobs.enqueue(ContactJobs.SyncContact, ContactJobs.SyncContactPayload(command.userId))
                throw RetryableContactNotSyncedException(
                    "Upstream contact for user ${command.userId} (${command.system}) was gone — " +
                        "cleared pairing and enqueued sync, will retry",
                    e,
                )
            }
        } else {
            if (externalContactId != null && externalListId != null) {
                adapter.removeFromList(externalContactId, externalListId)
                log.debug("Removed user {} from {} list {}", command.userId, command.system, command.contactListId)
            } else {
                log.debug(
                    "No {} IDs for user {}/list {} — skipping removal",
                    command.system, command.userId, command.contactListId,
                )
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(SyncListMembershipCommandHandler::class.java)
    }
}

internal class RetryableContactNotSyncedException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
