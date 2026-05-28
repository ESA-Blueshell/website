package net.blueshell.api.shared.job

import net.blueshell.api.shared.command.Command
import net.blueshell.api.shared.enums.ContactSystem

/**
 * Command to sync a single user's contact record to one external system.
 *
 * Serves as both the command object (dispatched via CommandBus) and the job payload
 * (serialized to JSON when enqueued, deserialized back by [ContactJobs.SyncContactToSystem]).
 *
 * The command handler selects the correct [ContactAdapter] by [system] at runtime,
 * so adding a new integration requires no new command or handler.
 */
data class SyncContactCommand(
    val userId: Long,
    val system: ContactSystem,
) : Command<Unit>

/**
 * Command to sync a user's list membership to one external system.
 *
 * Serves as both the command object and job payload for [ContactJobs.SyncListMembership].
 * Adds or removes the user from the external list depending on whether an active
 * ContactListMembership record exists in the database.
 */
data class SyncListMembershipCommand(
    val userId: Long,
    val contactListId: Long,
    val system: ContactSystem,
) : Command<Unit>
