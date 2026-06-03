package net.blueshell.api.platform.integration.cohort.application.command

import net.blueshell.api.platform.integration.cohort.command.LinkExternalUserCommand
import net.blueshell.api.platform.integration.cohort.port.`in`.CohortRemediation
import net.blueshell.api.platform.integration.sync.persistence.ExternalIdMapping
import net.blueshell.api.shared.command.CommandHandler
import org.springframework.stereotype.Component

@Component
class LinkExternalUserHandler(
    private val remediation: CohortRemediation,
) : CommandHandler<LinkExternalUserCommand, ExternalIdMapping> {
    override val commandType = LinkExternalUserCommand::class

    override fun handle(command: LinkExternalUserCommand): ExternalIdMapping =
        remediation.linkUser(
            subjectId = command.subjectId,
            userId = command.userId,
            system = command.system,
            externalUserId = command.externalUserId,
        )
}
