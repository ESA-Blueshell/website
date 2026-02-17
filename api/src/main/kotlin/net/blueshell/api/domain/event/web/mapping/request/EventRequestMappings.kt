package net.blueshell.api.domain.event.web.mapping.request

import net.blueshell.api.domain.event.command.CreateEventCommand
import net.blueshell.api.domain.event.command.UpdateEventCommand
import net.blueshell.api.domain.event.web.dto.request.CreateEventRequest
import net.blueshell.api.domain.event.web.dto.request.UpdateEventRequest
import net.blueshell.api.domain.survey.web.mapping.request.asDomainData

fun CreateEventRequest.asCommand(): CreateEventCommand =
    CreateEventCommand(
        committeeId = this.committeeId!!,
        title = this.title!!,
        description = this.description!!,
        location = this.location,
        startTime = this.startTime!!,
        endTime = this.endTime!!,
        memberPrice = this.memberPrice,
        publicPrice = this.publicPrice,
        approved = this.approved!!,
        membersOnly = this.membersOnly!!,
        signUp = this.signUp!!,
        banner = this.banner?.asDomainData(),
        signUpForm = this.signUpForm?.asDomainData(),
    )

fun UpdateEventRequest.asCommand(id: Long): UpdateEventCommand =
    UpdateEventCommand(
        id = id,
        committeeId = this.committeeId!!,
        title = this.title!!,
        description = this.description!!,
        location = this.location,
        startTime = this.startTime!!,
        endTime = this.endTime!!,
        memberPrice = this.memberPrice,
        publicPrice = this.publicPrice,
        approved = this.approved!!,
        membersOnly = this.membersOnly!!,
        signUp = this.signUp!!,
        banner = this.banner?.asDomainData(),
        signUpForm = this.signUpForm?.asDomainData(),
        version = this.version!!,
    )
