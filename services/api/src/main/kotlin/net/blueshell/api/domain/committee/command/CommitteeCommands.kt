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

    // Absent for a member who simply sits on the committee. Size tolerates null.
    @field:Size(max = 50, message = "Role must be at most 50 characters")
    var role: String?
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

    @field:NotNull(message = "Version is required for optimistic locking")
    val version: Long
) : Command<Committee>

data class DeleteCommitteeByIdCommand(
    @field:NotNull(message = "Committee ID is required")
    var id: Long
) : Command<Unit>
