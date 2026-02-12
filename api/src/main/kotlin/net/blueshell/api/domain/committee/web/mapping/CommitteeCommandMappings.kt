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
        CommitteeMemberData::userId fromProperty { from.userId!! }
        CommitteeMemberData::role fromProperty { from.role }
    }
}

object CreateCommitteeRequestToCommandMapper : ObjectMappie<CreateCommitteeRequest, CreateCommitteeCommand>() {
    override fun map(from: CreateCommitteeRequest) = mapping {
        CreateCommitteeCommand::name fromProperty { from.name!! }
        CreateCommitteeCommand::description fromProperty { from.description!! }
        CreateCommitteeCommand::members fromProperty {
            from.members!!.map { CommitteeMemberRequestToDataMapper.map(it) }.toMutableList()
        }
    }
}

private data class UpdateCommitteeCommandRequest(
    val id: Long,
    val request: UpdateCommitteeRequest
)

object UpdateCommitteeCommandRequestToCommandMapper : ObjectMappie<UpdateCommitteeCommandRequest, UpdateCommitteeCommand>() {
    override fun map(from: UpdateCommitteeCommandRequest) = mapping {
        UpdateCommitteeCommand::id fromProperty from::id
        UpdateCommitteeCommand::name fromProperty { from.request.name!! }
        UpdateCommitteeCommand::description fromProperty { from.request.description!! }
        UpdateCommitteeCommand::members fromProperty {
            from.request.members!!.map { CommitteeMemberRequestToDataMapper.map(it) }.toMutableList()
        }
        UpdateCommitteeCommand::version fromProperty { from.request.version }
    }
}

fun CreateCommitteeRequest.asCommand(): CreateCommitteeCommand = CreateCommitteeRequestToCommandMapper.map(this)

fun UpdateCommitteeRequest.asCommand(id: Long): UpdateCommitteeCommand =
    UpdateCommitteeCommandRequestToCommandMapper.map(UpdateCommitteeCommandRequest(id, this))
