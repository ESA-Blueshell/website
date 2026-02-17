package net.blueshell.api.domain.event.web.mapping.request

import net.blueshell.api.domain.event.command.CreateEventSignUpCommand
import net.blueshell.api.domain.event.command.EventSignUpData
import net.blueshell.api.domain.event.command.GuestData
import net.blueshell.api.domain.event.command.UpdateEventSignUpCommand
import net.blueshell.api.domain.event.web.dto.request.CreateEventSignUpRequest
import net.blueshell.api.domain.event.web.dto.request.CreateGuestRequest
import net.blueshell.api.domain.event.web.dto.request.UpdateEventSignUpRequest
import net.blueshell.api.domain.survey.command.AnswerData
import net.blueshell.api.domain.survey.web.dto.AnswerRequest

private fun AnswerRequest.asData(): AnswerData =
    AnswerData(
        questionId = this.questionId!!,
        optionSelections = this.optionSelections?.toList(),
        textResponse = this.textResponse,
        version = null,
    )

private fun CreateGuestRequest.asData(): GuestData =
    GuestData(
        name = this.name!!,
        discord = this.discord!!,
        email = this.email!!,
        phoneNumber = this.phoneNumber!!,
        accessToken = null,
        version = this.version,
    )

private fun CreateEventSignUpRequest.asData(eventId: Long): EventSignUpData =
    EventSignUpData(
        eventId = eventId,
        answers = this.answers?.map { it.asData() } ?: emptyList(),
        guest = this.guest?.asData(),
        userId = this.userId,
        version = null,
    )

private fun UpdateEventSignUpRequest.asData(eventId: Long): EventSignUpData =
    EventSignUpData(
        eventId = eventId,
        answers = this.answers?.map { it.asData() } ?: emptyList(),
        guest = this.guest?.asData(),
        userId = this.userId,
        version = this.version,
    )

fun CreateEventSignUpRequest.asCommand(eventId: Long, principalId: Long?): CreateEventSignUpCommand =
    CreateEventSignUpCommand(
        data = this.asData(eventId),
        principalId = principalId,
    )

fun UpdateEventSignUpRequest.asCommand(
    eventId: Long,
    principalId: Long?,
    accessToken: String?,
): UpdateEventSignUpCommand =
    UpdateEventSignUpCommand(
        eventId = eventId,
        data = this.asData(eventId),
        accessToken = accessToken,
        principalId = principalId,
    )
