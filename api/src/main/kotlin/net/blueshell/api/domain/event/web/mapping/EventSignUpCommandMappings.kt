package net.blueshell.api.domain.event.web.mapping

import net.blueshell.api.domain.event.command.CreateEventSignUpCommand
import net.blueshell.api.domain.event.command.UpdateEventSignUpCommand
import net.blueshell.api.domain.event.web.dto.CreateEventSignUpRequest
import net.blueshell.api.domain.event.web.dto.CreateGuestRequest
import net.blueshell.api.domain.event.web.dto.UpdateEventSignUpRequest
import net.blueshell.api.domain.event.web.dto.GuestDTO
import net.blueshell.api.domain.survey.web.dto.AnswerDTO
import net.blueshell.api.domain.survey.web.dto.AnswerRequest
import net.blueshell.api.domain.event.web.dto.EventSignUpDTO
import tech.mappie.api.ObjectMappie

object AnswerRequestToAnswerDTOMapper : ObjectMappie<AnswerRequest, AnswerDTO>() {
    override fun map(from: AnswerRequest) = mapping {
        AnswerDTO::questionId fromProperty { from.questionId!! }
        AnswerDTO::optionSelections fromProperty { from.optionSelections }
        AnswerDTO::textResponse fromProperty { from.textResponse }
    }
}

object CreateGuestRequestToGuestDTOMapper : ObjectMappie<CreateGuestRequest, GuestDTO>() {
    override fun map(from: CreateGuestRequest) = mapping {
        GuestDTO::name fromProperty { from.name!! }
        GuestDTO::discord fromProperty { from.discord!! }
        GuestDTO::email fromProperty { from.email!! }
        GuestDTO::phoneNumber fromProperty { from.phoneNumber }
        GuestDTO::version fromProperty { from.version }
    }
}

private data class EventSignUpRequestPayload(
    val eventId: Long,
    val request: CreateEventSignUpRequest
)

private data class EventSignUpUpdateRequestPayload(
    val eventId: Long,
    val request: UpdateEventSignUpRequest
)

object EventSignUpRequestPayloadToDtoMapper : ObjectMappie<EventSignUpRequestPayload, EventSignUpDTO>() {
    override fun map(from: EventSignUpRequestPayload) = mapping {
        EventSignUpDTO::eventId fromProperty { from.eventId }
        EventSignUpDTO::answers fromProperty {
            from.request.answers?.map { AnswerRequestToAnswerDTOMapper.map(it) }?.toMutableList()
        }
        EventSignUpDTO::guest fromProperty {
            from.request.guest?.let { CreateGuestRequestToGuestDTOMapper.map(it) }
        }
        EventSignUpDTO::userId fromProperty { from.request.userId }
    }
}

object EventSignUpUpdateRequestPayloadToDtoMapper : ObjectMappie<EventSignUpUpdateRequestPayload, EventSignUpDTO>() {
    override fun map(from: EventSignUpUpdateRequestPayload) = mapping {
        EventSignUpDTO::eventId fromProperty { from.eventId }
        EventSignUpDTO::answers fromProperty {
            from.request.answers?.map { AnswerRequestToAnswerDTOMapper.map(it) }?.toMutableList()
        }
        EventSignUpDTO::guest fromProperty {
            from.request.guest?.let { CreateGuestRequestToGuestDTOMapper.map(it) }
        }
        EventSignUpDTO::userId fromProperty { from.request.userId }
        EventSignUpDTO::version fromProperty { from.request.version }
    }
}

private data class CreateEventSignUpCommandRequest(
    val eventId: Long,
    val principalId: Long?,
    val request: CreateEventSignUpRequest
)

private data class UpdateEventSignUpCommandRequest(
    val eventId: Long,
    val principalId: Long?,
    val accessToken: String?,
    val request: UpdateEventSignUpRequest
)

object CreateEventSignUpCommandRequestToCommandMapper : ObjectMappie<CreateEventSignUpCommandRequest, CreateEventSignUpCommand>() {
    override fun map(from: CreateEventSignUpCommandRequest) = mapping {
        CreateEventSignUpCommand::dto fromProperty {
            EventSignUpRequestPayloadToDtoMapper.map(EventSignUpRequestPayload(from.eventId, from.request))
        }
        CreateEventSignUpCommand::principalId fromProperty { from.principalId }
    }
}

object UpdateEventSignUpCommandRequestToCommandMapper : ObjectMappie<UpdateEventSignUpCommandRequest, UpdateEventSignUpCommand>() {
    override fun map(from: UpdateEventSignUpCommandRequest) = mapping {
        UpdateEventSignUpCommand::eventId fromProperty { from.eventId }
        UpdateEventSignUpCommand::dto fromProperty {
            EventSignUpUpdateRequestPayloadToDtoMapper.map(EventSignUpUpdateRequestPayload(from.eventId, from.request))
        }
        UpdateEventSignUpCommand::accessToken fromProperty { from.accessToken }
        UpdateEventSignUpCommand::principalId fromProperty { from.principalId }
    }
}

fun CreateEventSignUpRequest.asCommand(eventId: Long, principalId: Long?): CreateEventSignUpCommand =
    CreateEventSignUpCommandRequestToCommandMapper.map(CreateEventSignUpCommandRequest(eventId, principalId, this))

fun UpdateEventSignUpRequest.asCommand(
    eventId: Long,
    principalId: Long?,
    accessToken: String?
): UpdateEventSignUpCommand =
    UpdateEventSignUpCommandRequestToCommandMapper.map(
        UpdateEventSignUpCommandRequest(eventId, principalId, accessToken, this)
    )
