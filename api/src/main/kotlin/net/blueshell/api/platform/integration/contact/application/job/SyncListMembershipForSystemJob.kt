package net.blueshell.api.platform.integration.contact.application.job

import net.blueshell.api.platform.integration.queue.AbstractCommandJobHandler
import net.blueshell.api.shared.command.CommandBus
import net.blueshell.api.shared.job.ContactJobs
import net.blueshell.api.shared.job.SyncListMembershipCommand
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * Job handler for [ContactJobs.SyncListMembershipForSystem].
 *
 * Thin wrapper: deserializes the [SyncListMembershipCommand] payload from JSON,
 * then dispatches it through the [CommandBus] to [SyncListMembershipCommandHandler].
 *
 * Works in all profiles — no @Profile needed.
 * In test/dev, [MockContactAdapter] (system=LISTMONK) is the only registered ListAdapter.
 */
@Component
class SyncListMembershipForSystemJob(
    objectMapper: ObjectMapper,
    commandBus: CommandBus,
) : AbstractCommandJobHandler<SyncListMembershipCommand, Unit>(
    objectMapper,
    SyncListMembershipCommand::class.java,
    commandBus,
) {
    override val jobType: String = ContactJobs.SyncListMembershipForSystem.type
}
