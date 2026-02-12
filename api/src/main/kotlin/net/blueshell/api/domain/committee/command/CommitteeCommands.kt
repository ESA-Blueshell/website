package net.blueshell.api.domain.committee.command

import net.blueshell.api.domain.committee.persistence.Committee
import net.blueshell.api.shared.command.Command

data class CommitteeMemberData(
    val userId: Long,
    val role: String
)

data class FindCommitteesForCurrentUserCommand(
    val principalId: Long?,
    val includeAll: Boolean
) : Command<MutableList<Committee>>

class FindCommitteesCommand : Command<MutableList<Committee>>

data class FindCommitteeByIdCommand(
    val committeeId: Long
) : Command<Committee>

data class CreateCommitteeCommand(
    val name: String,
    val description: String,
    val members: MutableList<CommitteeMemberData>
) : Command<Committee>

data class UpdateCommitteeCommand(
    val id: Long,
    val name: String,
    val description: String,
    val members: MutableList<CommitteeMemberData>,
    val version: Long?
) : Command<Committee>

data class DeleteCommitteeByIdCommand(
    val id: Long
) : Command<Unit>
