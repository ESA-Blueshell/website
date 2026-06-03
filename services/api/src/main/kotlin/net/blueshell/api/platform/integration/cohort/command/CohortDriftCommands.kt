package net.blueshell.api.platform.integration.cohort.command

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import net.blueshell.api.platform.integration.sync.persistence.ExternalIdMapping
import net.blueshell.api.platform.integration.sync.port.TargetSystem
import net.blueshell.api.shared.command.Command

data class LinkExternalUserCommand(
    @field:NotNull
    val subjectId: Long,

    @field:NotNull
    val userId: Long,

    @field:NotNull
    val system: TargetSystem,

    @field:NotBlank
    val externalUserId: String,
) : Command<ExternalIdMapping>
