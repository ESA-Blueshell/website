package net.blueshell.api.domain.user.web.mapping.request

import net.blueshell.api.domain.user.command.BoardCreateMembershipCommand
import net.blueshell.api.domain.user.command.CorrectMembershipCommand
import net.blueshell.api.domain.user.web.dto.request.BoardCreateMembershipRequest
import net.blueshell.api.domain.user.web.dto.request.UpdateMembershipRequest

fun BoardCreateMembershipRequest.asCommand(): BoardCreateMembershipCommand =
    BoardCreateMembershipCommand(
        userId = this.userId!!,
        memberType = this.memberType!!,
        startDate = this.startDate!!,
        endDate = this.endDate,
        incasso = this.incasso!!,
    )

fun UpdateMembershipRequest.asCommand(id: Long): CorrectMembershipCommand =
    CorrectMembershipCommand(
        id = id,
        userId = this.userId!!,
        memberType = this.memberType,
        startDate = this.startDate!!,
        endDate = this.endDate,
        incasso = this.incasso,
        version = this.version!!,
    )
