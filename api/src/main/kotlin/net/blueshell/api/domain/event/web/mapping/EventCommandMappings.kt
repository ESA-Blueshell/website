package net.blueshell.api.domain.event.web.mapping

import net.blueshell.api.domain.event.command.CreateEventCommand
import net.blueshell.api.domain.event.command.UpdateEventCommand
import net.blueshell.api.domain.event.web.dto.CreateEventRequest
import net.blueshell.api.domain.event.web.dto.UpdateEventRequest
import net.blueshell.api.domain.survey.web.mapping.asDomainData
import tech.mappie.api.ObjectMappie

object CreateEventRequestToCommandMapper : ObjectMappie<CreateEventRequest, CreateEventCommand>() {
    override fun map(from: CreateEventRequest) = mapping {
        CreateEventCommand::committeeId fromValue from.committeeId!!
        CreateEventCommand::title fromValue from.title!!
        CreateEventCommand::description fromValue from.description!!
        CreateEventCommand::location fromValue from.location
        CreateEventCommand::startTime fromValue from.startTime!!
        CreateEventCommand::endTime fromValue from.endTime!!
        CreateEventCommand::memberPrice fromValue from.memberPrice
        CreateEventCommand::publicPrice fromValue from.publicPrice
        CreateEventCommand::approved fromValue from.approved!!
        CreateEventCommand::membersOnly fromValue from.membersOnly!!
        CreateEventCommand::signUp fromValue from.signUp!!
        CreateEventCommand::banner fromValue from.banner?.asDomainData()
        CreateEventCommand::signUpForm fromValue from.signUpForm?.asDomainData()
    }
}

internal data class UpdateEventCommandRequest(
    val id: Long,
    val request: UpdateEventRequest
)

internal object UpdateEventCommandRequestToCommandMapper : ObjectMappie<UpdateEventCommandRequest, UpdateEventCommand>() {
    override fun map(from: UpdateEventCommandRequest) = mapping {
        UpdateEventCommand::id fromProperty from::id
        UpdateEventCommand::committeeId fromValue from.request.committeeId!!
        UpdateEventCommand::title fromValue from.request.title!!
        UpdateEventCommand::description fromValue from.request.description!!
        UpdateEventCommand::location fromValue from.request.location
        UpdateEventCommand::startTime fromValue from.request.startTime!!
        UpdateEventCommand::endTime fromValue from.request.endTime!!
        UpdateEventCommand::memberPrice fromValue from.request.memberPrice
        UpdateEventCommand::publicPrice fromValue from.request.publicPrice
        UpdateEventCommand::approved fromValue from.request.approved!!
        UpdateEventCommand::membersOnly fromValue from.request.membersOnly!!
        UpdateEventCommand::signUp fromValue from.request.signUp!!
        UpdateEventCommand::banner fromValue from.request.banner?.asDomainData()
        UpdateEventCommand::signUpForm fromValue from.request.signUpForm?.asDomainData()
        UpdateEventCommand::version fromValue from.request.version
    }
}

fun CreateEventRequest.asCommand(): CreateEventCommand = CreateEventRequestToCommandMapper.map(this)

fun UpdateEventRequest.asCommand(id: Long): UpdateEventCommand =
    UpdateEventCommandRequestToCommandMapper.map(UpdateEventCommandRequest(id, this))
