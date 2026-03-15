package net.blueshell.api.domain.committee.web.mapping.request

import net.blueshell.api.domain.committee.command.CommitteeMemberData
import net.blueshell.api.domain.committee.command.CreateCommitteeCommand
import net.blueshell.api.domain.committee.command.UpdateCommitteeCommand
import net.blueshell.api.domain.committee.web.dto.request.CommitteeMemberRequest
import net.blueshell.api.domain.committee.web.dto.request.CreateCommitteeRequest
import net.blueshell.api.domain.committee.web.dto.request.UpdateCommitteeRequest

private fun CommitteeMemberRequest.asData(): CommitteeMemberData =
    CommitteeMemberData(
        userId = this.userId!!,
        role = this.role!!,
    )

fun CreateCommitteeRequest.asCommand(): CreateCommitteeCommand =
    CreateCommitteeCommand(
        name = this.name!!,
        description = this.description!!,
        members = this.members!!.map { it.asData() }.toMutableList(),
    )

fun UpdateCommitteeRequest.asCommand(id: Long): UpdateCommitteeCommand =
    UpdateCommitteeCommand(
        id = id,
        name = this.name!!,
        description = this.description!!,
        members = this.members!!.map { it.asData() }.toMutableList(),
        version = this.version!!,
    )
