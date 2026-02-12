package net.blueshell.api.domain.event.web.mapping

import net.blueshell.api.domain.event.command.CreateEventCommand
import net.blueshell.api.domain.event.command.UpdateEventCommand
import net.blueshell.api.domain.event.web.dto.CreateEventRequest
import net.blueshell.api.domain.event.web.dto.UpdateEventRequest
import tech.mappie.api.ObjectMappie

object CreateEventRequestToCommandMapper : ObjectMappie<CreateEventRequest, CreateEventCommand>() {
    override fun map(from: CreateEventRequest) = mapping {
        CreateEventCommand::committeeId fromProperty { from.committeeId!! }
        CreateEventCommand::title fromProperty { from.title!! }
        CreateEventCommand::description fromProperty { from.description!! }
        CreateEventCommand::location fromProperty { from.location }
        CreateEventCommand::startTime fromProperty { from.startTime!! }
        CreateEventCommand::endTime fromProperty { from.endTime!! }
        CreateEventCommand::memberPrice fromProperty { from.memberPrice }
        CreateEventCommand::publicPrice fromProperty { from.publicPrice }
        CreateEventCommand::approved fromProperty { from.approved!! }
        CreateEventCommand::membersOnly fromProperty { from.membersOnly!! }
        CreateEventCommand::signUp fromProperty { from.signUp!! }
        CreateEventCommand::banner fromProperty { from.banner }
        CreateEventCommand::signUpForm fromProperty { from.signUpForm }
    }
}

private data class UpdateEventCommandRequest(
    val id: Long,
    val request: UpdateEventRequest
)

object UpdateEventCommandRequestToCommandMapper : ObjectMappie<UpdateEventCommandRequest, UpdateEventCommand>() {
    override fun map(from: UpdateEventCommandRequest) = mapping {
        UpdateEventCommand::id fromProperty from::id
        UpdateEventCommand::committeeId fromProperty { from.request.committeeId!! }
        UpdateEventCommand::title fromProperty { from.request.title!! }
        UpdateEventCommand::description fromProperty { from.request.description!! }
        UpdateEventCommand::location fromProperty { from.request.location }
        UpdateEventCommand::startTime fromProperty { from.request.startTime!! }
        UpdateEventCommand::endTime fromProperty { from.request.endTime!! }
        UpdateEventCommand::memberPrice fromProperty { from.request.memberPrice }
        UpdateEventCommand::publicPrice fromProperty { from.request.publicPrice }
        UpdateEventCommand::approved fromProperty { from.request.approved!! }
        UpdateEventCommand::membersOnly fromProperty { from.request.membersOnly!! }
        UpdateEventCommand::signUp fromProperty { from.request.signUp!! }
        UpdateEventCommand::banner fromProperty { from.request.banner }
        UpdateEventCommand::signUpForm fromProperty { from.request.signUpForm }
        UpdateEventCommand::version fromProperty { from.request.version }
    }
}

fun CreateEventRequest.asCommand(): CreateEventCommand = CreateEventRequestToCommandMapper.map(this)

fun UpdateEventRequest.asCommand(id: Long): UpdateEventCommand =
    UpdateEventCommandRequestToCommandMapper.map(UpdateEventCommandRequest(id, this))
