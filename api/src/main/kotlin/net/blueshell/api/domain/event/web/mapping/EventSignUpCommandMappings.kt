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
        AnswerDTO::questionId fromValue { from.questionId!! }
        AnswerDTO::optionSelections fromValue { from.optionSelections }
        AnswerDTO::textResponse fromValue { from.textResponse }
    }
}

object CreateGuestRequestToGuestDTOMapper : ObjectMappie<CreateGuestRequest, GuestDTO>() {
    override fun map(from: CreateGuestRequest) = mapping {
        GuestDTO::name fromValue { from.name!! }
        GuestDTO::discord fromValue { from.discord!! }
        GuestDTO::email fromValue { from.email!! }
        GuestDTO::phoneNumber fromValue { from.phoneNumber }
        GuestDTO::version fromValue { from.version }
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
        EventSignUpDTO::eventId fromValue { from.eventId }
        EventSignUpDTO::answers fromValue {
            from.request.answers?.map { AnswerRequestToAnswerDTOMapper.map(it) }?.toMutableList()
        }
        EventSignUpDTO::guest fromValue {
            from.request.guest?.let { CreateGuestRequestToGuestDTOMapper.map(it) }
        }
        EventSignUpDTO::userId fromValue { from.request.userId }
    }
}

object EventSignUpUpdateRequestPayloadToDtoMapper : ObjectMappie<EventSignUpUpdateRequestPayload, EventSignUpDTO>() {
    override fun map(from: EventSignUpUpdateRequestPayload) = mapping {
        EventSignUpDTO::eventId fromValue { from.eventId }
        EventSignUpDTO::answers fromValue {
            from.request.answers?.map { AnswerRequestToAnswerDTOMapper.map(it) }?.toMutableList()
        }
        EventSignUpDTO::guest fromValue {
            from.request.guest?.let { CreateGuestRequestToGuestDTOMapper.map(it) }
        }
        EventSignUpDTO::userId fromValue { from.request.userId }
        EventSignUpDTO::version fromValue { from.request.version }
    }
}

internal data class CreateEventSignUpCommandRequest(
    val eventId: Long,
    val principalId: Long?,
    val request: CreateEventSignUpRequest
)

internal data class UpdateEventSignUpCommandRequest(
    val eventId: Long,
    val principalId: Long?,
    val accessToken: String?,
    val request: UpdateEventSignUpRequest
)

internal object CreateEventSignUpCommandRequestToCommandMapper : ObjectMappie<CreateEventSignUpCommandRequest, CreateEventSignUpCommand>() {
    override fun map(from: CreateEventSignUpCommandRequest) = mapping {
        CreateEventSignUpCommand::dto fromValue {
            EventSignUpRequestPayloadToDtoMapper.map(EventSignUpRequestPayload(from.eventId, from.request))
        }
        CreateEventSignUpCommand::principalId fromValue { from.principalId }
    }
}

internal object UpdateEventSignUpCommandRequestToCommandMapper : ObjectMappie<UpdateEventSignUpCommandRequest, UpdateEventSignUpCommand>() {
    override fun map(from: UpdateEventSignUpCommandRequest) = mapping {
        UpdateEventSignUpCommand::eventId fromValue { from.eventId }
        UpdateEventSignUpCommand::dto fromValue {
            EventSignUpUpdateRequestPayloadToDtoMapper.map(EventSignUpUpdateRequestPayload(from.eventId, from.request))
        }
        UpdateEventSignUpCommand::accessToken fromValue { from.accessToken }
        UpdateEventSignUpCommand::principalId fromValue { from.principalId }
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
