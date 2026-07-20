package net.blueshell.api.domain.user.command

import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Positive
import net.blueshell.api.shared.command.Command
import net.blueshell.api.shared.dto.bulk.BulkActionResult

data class ExecuteBulkResumeMembershipCommand(
    @field:NotEmpty(message = "At least one user ID is required")
    val userIds: List<@Positive Long>,
) : Command<BulkActionResult>
