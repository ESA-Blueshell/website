package net.blueshell.api.shared.job

import net.blueshell.api.shared.command.Command
import net.blueshell.api.shared.enums.ContactSystem

/**
 * Command to sync a single user's contact record to one external system.
 *
 * Serves as both the command object (dispatched via CommandBus) and the job payload
 * (serialized to JSON when enqueued, deserialized back by the contact sync job).
 *
 * The command handler selects the correct [ContactAdapter] by [system] at runtime,
 * so adding a new integration requires no new command or handler.
 */
data class SyncContactCommand(
    val userId: Long,
    val system: ContactSystem,
) : Command<Unit>
