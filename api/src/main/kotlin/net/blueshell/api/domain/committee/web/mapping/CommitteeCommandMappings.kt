package net.blueshell.api.domain.committee.web.mapping

import net.blueshell.api.domain.committee.command.CommitteeMemberData
import net.blueshell.api.domain.committee.command.CreateCommitteeCommand
import net.blueshell.api.domain.committee.command.UpdateCommitteeCommand
import net.blueshell.api.domain.committee.web.dto.CommitteeMemberRequest
import net.blueshell.api.domain.committee.web.dto.CreateCommitteeRequest
import net.blueshell.api.domain.committee.web.dto.UpdateCommitteeRequest
import tech.mappie.api.ObjectMappie

object CommitteeMemberRequestToDataMapper : ObjectMappie<CommitteeMemberRequest, CommitteeMemberData>() {
    override fun map(from: CommitteeMemberRequest) = mapping {
        CommitteeMemberData::userId fromValue { from.userId!! }
        CommitteeMemberData::role fromValue { from.role }
    }
}

object CreateCommitteeRequestToCommandMapper : ObjectMappie<CreateCommitteeRequest, CreateCommitteeCommand>() {
    override fun map(from: CreateCommitteeRequest) = mapping {
        CreateCommitteeCommand::name fromValue { from.name!! }
        CreateCommitteeCommand::description fromValue { from.description!! }
        CreateCommitteeCommand::members fromValue {
            from.members!!.map { CommitteeMemberRequestToDataMapper.map(it) }.toMutableList()
        }
    }
}

internal data class UpdateCommitteeCommandRequest(
    val id: Long,
    val request: UpdateCommitteeRequest
)

internal object UpdateCommitteeCommandRequestToCommandMapper : ObjectMappie<UpdateCommitteeCommandRequest, UpdateCommitteeCommand>() {
    override fun map(from: UpdateCommitteeCommandRequest) = mapping {
        UpdateCommitteeCommand::id fromProperty from::id
        UpdateCommitteeCommand::name fromValue { from.request.name!! }
        UpdateCommitteeCommand::description fromValue { from.request.description!! }
        UpdateCommitteeCommand::members fromValue {
            from.request.members!!.map { CommitteeMemberRequestToDataMapper.map(it) }.toMutableList()
        }
        UpdateCommitteeCommand::version fromValue { from.request.version }
    }
}

fun CreateCommitteeRequest.asCommand(): CreateCommitteeCommand = CreateCommitteeRequestToCommandMapper.map(this)

fun UpdateCommitteeRequest.asCommand(id: Long): UpdateCommitteeCommand =
    UpdateCommitteeCommandRequestToCommandMapper.map(UpdateCommitteeCommandRequest(id, this))
