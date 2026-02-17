package net.blueshell.api.domain.user.web.mapping

import net.blueshell.api.domain.user.command.BoardCreateMembershipCommand
import net.blueshell.api.domain.user.command.UpdateMembershipCommand
import net.blueshell.api.domain.user.web.dto.request.BoardCreateMembershipRequest
import net.blueshell.api.domain.user.web.dto.request.UpdateMembershipRequest
import tech.mappie.api.ObjectMappie

object BoardCreateMembershipRequestToCommandMapper : ObjectMappie<BoardCreateMembershipRequest, BoardCreateMembershipCommand>() {
    override fun map(from: BoardCreateMembershipRequest) = mapping {
        BoardCreateMembershipCommand::userId fromValue from.userId!!
        BoardCreateMembershipCommand::memberType fromValue from.memberType!!
        BoardCreateMembershipCommand::startDate fromValue from.startDate!!
        BoardCreateMembershipCommand::endDate fromValue from.endDate
        BoardCreateMembershipCommand::incasso fromValue from.incasso!!
    }
}

internal data class UpdateMembershipCommandRequest(
    val id: Long,
    val request: UpdateMembershipRequest
)

internal object UpdateMembershipCommandRequestToCommandMapper : ObjectMappie<UpdateMembershipCommandRequest, UpdateMembershipCommand>() {
    override fun map(from: UpdateMembershipCommandRequest) = mapping {
        UpdateMembershipCommand::id fromProperty from::id
        UpdateMembershipCommand::userId fromValue from.request.userId!!
        UpdateMembershipCommand::memberType fromValue from.request.memberType
        UpdateMembershipCommand::startDate fromValue from.request.startDate!!
        UpdateMembershipCommand::endDate fromValue from.request.endDate
        UpdateMembershipCommand::incasso fromValue from.request.incasso
        UpdateMembershipCommand::version fromValue from.request.version!!
    }
}

fun BoardCreateMembershipRequest.asCommand(): BoardCreateMembershipCommand =
    BoardCreateMembershipRequestToCommandMapper.map(this)

fun UpdateMembershipRequest.asCommand(id: Long): UpdateMembershipCommand =
    UpdateMembershipCommandRequestToCommandMapper.map(UpdateMembershipCommandRequest(id, this))
