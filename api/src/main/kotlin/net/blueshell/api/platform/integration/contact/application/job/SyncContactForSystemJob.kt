package net.blueshell.api.platform.integration.contact.application.job

import net.blueshell.api.platform.integration.queue.AbstractCommandJobHandler
import net.blueshell.api.shared.command.CommandBus
import net.blueshell.api.shared.job.ContactJobs
import net.blueshell.api.shared.job.SyncContactCommand
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * Job handler for [ContactJobs.SyncContactForSystem].
 *
 * Thin wrapper: deserializes the [SyncContactCommand] payload from JSON,
 * then dispatches it through the [CommandBus] to [SyncContactCommandHandler].
 *
 * Works in all profiles — no @Profile needed.
 * In test/dev, [MockContactAdapter] (system=LISTMONK) is the only registered ContactAdapter.
 */
@Component
class SyncContactForSystemJob(
    objectMapper: ObjectMapper,
    commandBus: CommandBus,
) : AbstractCommandJobHandler<SyncContactCommand, Unit>(
    objectMapper,
    SyncContactCommand::class.java,
    commandBus,
) {
    override val jobType: String = ContactJobs.SyncContactForSystem.type
}
