package net.blueshell.api.domain.membership.web.mapping

import net.blueshell.api.domain.membership.command.BoardCreateMembershipCommand
import net.blueshell.api.domain.membership.command.UpdateMembershipCommand
import net.blueshell.api.domain.membership.web.dto.BoardCreateMembershipRequest
import net.blueshell.api.domain.membership.web.dto.UpdateMembershipRequest
import tech.mappie.api.ObjectMappie

object BoardCreateMembershipRequestToCommandMapper : ObjectMappie<BoardCreateMembershipRequest, BoardCreateMembershipCommand>() {
    override fun map(from: BoardCreateMembershipRequest) = mapping {
        BoardCreateMembershipCommand::userId fromProperty { from.userId!! }
        BoardCreateMembershipCommand::memberType fromProperty { from.memberType!! }
        BoardCreateMembershipCommand::startDate fromProperty { from.startDate!! }
        BoardCreateMembershipCommand::endDate fromProperty { from.endDate }
        BoardCreateMembershipCommand::incasso fromProperty { from.incasso!! }
    }
}

private data class UpdateMembershipCommandRequest(
    val id: Long,
    val request: UpdateMembershipRequest
)

object UpdateMembershipCommandRequestToCommandMapper : ObjectMappie<UpdateMembershipCommandRequest, UpdateMembershipCommand>() {
    override fun map(from: UpdateMembershipCommandRequest) = mapping {
        UpdateMembershipCommand::id fromProperty from::id
        UpdateMembershipCommand::userId fromProperty { from.request.userId!! }
        UpdateMembershipCommand::memberType fromProperty { from.request.memberType }
        UpdateMembershipCommand::startDate fromProperty { from.request.startDate!! }
        UpdateMembershipCommand::endDate fromProperty { from.request.endDate }
        UpdateMembershipCommand::incasso fromProperty { from.request.incasso }
        UpdateMembershipCommand::version fromProperty { from.request.version }
    }
}

fun BoardCreateMembershipRequest.asCommand(): BoardCreateMembershipCommand =
    BoardCreateMembershipRequestToCommandMapper.map(this)

fun UpdateMembershipRequest.asCommand(id: Long): UpdateMembershipCommand =
    UpdateMembershipCommandRequestToCommandMapper.map(UpdateMembershipCommandRequest(id, this))
