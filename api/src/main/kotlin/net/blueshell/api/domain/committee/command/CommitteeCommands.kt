package net.blueshell.api.domain.committee.command

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import net.blueshell.api.domain.committee.persistence.Committee
import net.blueshell.api.shared.command.Command

data class CommitteeMemberData(
    @field:NotNull(message = "User ID is required")
    var userId: Long,

    @field:NotBlank(message = "Role is required")
    @field:Size(min = 1, max = 50, message = "Role must be 1-50 characters")
    var role: String
)

data class FindCommitteesForCurrentUserCommand(
    val principalId: Long?,
    val includeAll: Boolean
) : Command<MutableList<Committee>>

class FindCommitteesCommand : Command<MutableList<Committee>>

data class FindCommitteeByIdCommand(
    @field:NotNull(message = "Committee ID is required")
    var committeeId: Long
) : Command<Committee>

data class CreateCommitteeCommand(
    @field:NotBlank(message = "Committee name is required")
    @field:Size(min = 1, max = 100, message = "Name must be 1-100 characters")
    var name: String,

    @field:NotBlank(message = "Description is required")
    @field:Size(min = 1, max = 1000, message = "Description must be 1-1000 characters")
    var description: String,

    @field:NotEmpty(message = "Committee must have at least one member")
    @field:Valid
    var members: MutableList<CommitteeMemberData>
) : Command<Committee>

data class UpdateCommitteeCommand(
    @field:NotNull(message = "Committee ID is required")
    var id: Long,

    @field:NotBlank(message = "Committee name is required")
    @field:Size(min = 1, max = 100, message = "Name must be 1-100 characters")
    var name: String,

    @field:NotBlank(message = "Description is required")
    @field:Size(min = 1, max = 1000, message = "Description must be 1-1000 characters")
    var description: String,

    @field:NotEmpty(message = "Committee must have at least one member")
    @field:Valid
    var members: MutableList<CommitteeMemberData>,

    val version: Long?
) : Command<Committee>

data class DeleteCommitteeByIdCommand(
    @field:NotNull(message = "Committee ID is required")
    var id: Long
) : Command<Unit>
