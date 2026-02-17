package net.blueshell.api.domain.event.web.mapping.request

import net.blueshell.api.domain.event.command.*
import net.blueshell.api.domain.event.web.dto.request.CreateEventSignUpRequest
import net.blueshell.api.domain.event.web.dto.request.CreateGuestRequest
import net.blueshell.api.domain.event.web.dto.request.UpdateEventSignUpRequest
import net.blueshell.api.domain.survey.command.AnswerData
import net.blueshell.api.domain.survey.web.dto.AnswerRequest
import tech.mappie.api.ObjectMappie

/**
 * Maps AnswerRequest (web DTO) to AnswerData (command layer).
 */
object AnswerRequestToAnswerDataMapper : ObjectMappie<AnswerRequest, AnswerData>() {
    override fun map(from: AnswerRequest) = mapping {
        AnswerData::questionId fromValue from.questionId!!
        AnswerData::optionSelections fromValue from.optionSelections?.toList()
        AnswerData::textResponse fromValue from.textResponse
        AnswerData::version fromValue null
    }
}

/**
 * Maps CreateGuestRequest (web DTO) to GuestData (command layer).
 */
object CreateGuestRequestToGuestDataMapper : ObjectMappie<CreateGuestRequest, GuestData>() {
    override fun map(from: CreateGuestRequest) = mapping {
        GuestData::name fromValue from.name!!
        GuestData::discord fromValue from.discord!!
        GuestData::email fromValue from.email!!
        GuestData::phoneNumber fromValue from.phoneNumber!!
        GuestData::accessToken fromValue null  // Generated in handler
        GuestData::version fromValue from.version
    }
}

/**
 * Internal payload for creating EventSignUpData.
 */
internal data class EventSignUpRequestPayload(
    val eventId: Long,
    val request: CreateEventSignUpRequest
)

/**
 * Internal payload for updating EventSignUpData.
 */
internal data class EventSignUpUpdateRequestPayload(
    val eventId: Long,
    val request: UpdateEventSignUpRequest
)

/**
 * Maps EventSignUpRequestPayload to EventSignUpData.
 */
internal object EventSignUpRequestPayloadToDataMapper : ObjectMappie<EventSignUpRequestPayload, EventSignUpData>() {
    override fun map(from: EventSignUpRequestPayload) = mapping {
        EventSignUpData::eventId fromValue from.eventId
        EventSignUpData::answers fromValue (from.request.answers?.map {
            AnswerRequestToAnswerDataMapper.map(it)
        } ?: emptyList())
        EventSignUpData::guest fromValue from.request.guest?.let {
            CreateGuestRequestToGuestDataMapper.map(it)
        }
        EventSignUpData::userId fromValue from.request.userId
        EventSignUpData::version fromValue null
    }
}

/**
 * Maps EventSignUpUpdateRequestPayload to EventSignUpData.
 */
internal object EventSignUpUpdateRequestPayloadToDataMapper : ObjectMappie<EventSignUpUpdateRequestPayload, EventSignUpData>() {
    override fun map(from: EventSignUpUpdateRequestPayload) = mapping {
        EventSignUpData::eventId fromValue from.eventId
        EventSignUpData::answers fromValue (from.request.answers?.map {
            AnswerRequestToAnswerDataMapper.map(it)
        } ?: emptyList())
        EventSignUpData::guest fromValue from.request.guest?.let {
            CreateGuestRequestToGuestDataMapper.map(it)
        }
        EventSignUpData::userId fromValue from.request.userId
        EventSignUpData::version fromValue from.request.version
    }
}

/**
 * Internal request wrapper for CreateEventSignUpCommand.
 */
internal data class CreateEventSignUpCommandRequest(
    val eventId: Long,
    val principalId: Long?,
    val request: CreateEventSignUpRequest
)

/**
 * Internal request wrapper for UpdateEventSignUpCommand.
 */
internal data class UpdateEventSignUpCommandRequest(
    val eventId: Long,
    val principalId: Long?,
    val accessToken: String?,
    val request: UpdateEventSignUpRequest
)

/**
 * Maps CreateEventSignUpCommandRequest to CreateEventSignUpCommand.
 */
internal object CreateEventSignUpCommandRequestToCommandMapper : ObjectMappie<CreateEventSignUpCommandRequest, CreateEventSignUpCommand>() {
    override fun map(from: CreateEventSignUpCommandRequest) = mapping {
        CreateEventSignUpCommand::data fromValue EventSignUpRequestPayloadToDataMapper.map(
            EventSignUpRequestPayload(from.eventId, from.request)
        )
        CreateEventSignUpCommand::principalId fromValue from.principalId
    }
}

/**
 * Maps UpdateEventSignUpCommandRequest to UpdateEventSignUpCommand.
 */
internal object UpdateEventSignUpCommandRequestToCommandMapper : ObjectMappie<UpdateEventSignUpCommandRequest, UpdateEventSignUpCommand>() {
    override fun map(from: UpdateEventSignUpCommandRequest) = mapping {
        UpdateEventSignUpCommand::eventId fromValue from.eventId
        UpdateEventSignUpCommand::data fromValue EventSignUpUpdateRequestPayloadToDataMapper.map(
            EventSignUpUpdateRequestPayload(from.eventId, from.request)
        )
        UpdateEventSignUpCommand::accessToken fromValue from.accessToken
        UpdateEventSignUpCommand::principalId fromValue from.principalId
    }
}

/**
 * Extension function to convert CreateEventSignUpRequest to CreateEventSignUpCommand.
 */
fun CreateEventSignUpRequest.asCommand(eventId: Long, principalId: Long?): CreateEventSignUpCommand =
    CreateEventSignUpCommandRequestToCommandMapper.map(CreateEventSignUpCommandRequest(eventId, principalId, this))

/**
 * Extension function to convert UpdateEventSignUpRequest to UpdateEventSignUpCommand.
 */
fun UpdateEventSignUpRequest.asCommand(
    eventId: Long,
    principalId: Long?,
    accessToken: String?
): UpdateEventSignUpCommand =
    UpdateEventSignUpCommandRequestToCommandMapper.map(
        UpdateEventSignUpCommandRequest(eventId, principalId, accessToken, this)
    )
