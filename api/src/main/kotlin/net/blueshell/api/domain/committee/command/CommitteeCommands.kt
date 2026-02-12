package net.blueshell.api.domain.committee.command

import net.blueshell.api.domain.committee.persistence.Committee
import net.blueshell.api.domain.committee.web.dto.AdvancedCommitteeDTO
import net.blueshell.api.shared.command.Command

data class FindCommitteesForCurrentUserCommand(
    val principalId: Long?,
    val includeAll: Boolean
) : Command<MutableList<Committee>>

class FindCommitteesCommand : Command<MutableList<Committee>>

data class FindCommitteeByIdCommand(
    val committeeId: Long
) : Command<Committee>

data class CreateCommitteeCommand(
    val dto: AdvancedCommitteeDTO
) : Command<Committee>

data class UpdateCommitteeCommand(
    val id: Long,
    val dto: AdvancedCommitteeDTO
) : Command<Committee>

data class DeleteCommitteeByIdCommand(
    val id: Long
) : Command<Unit>
