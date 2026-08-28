package net.blueshell.api.domain.event.web.mapping.request

import net.blueshell.api.domain.event.application.EventSignUpData
import net.blueshell.api.domain.event.application.GuestData

import net.blueshell.api.domain.event.web.dto.request.CreateEventSignUpRequest
import net.blueshell.api.domain.event.web.dto.request.CreateGuestRequest
import net.blueshell.api.domain.event.web.dto.request.UpdateEventSignUpRequest
import net.blueshell.api.survey.api.AnswerData
import net.blueshell.api.survey.web.AnswerRequest

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

fun CreateEventSignUpRequest.asData(eventId: Long): EventSignUpData =
    EventSignUpData(
        eventId = eventId,
        answers = this.answers?.map { it.asData() } ?: emptyList(),
        guest = this.guest?.asData(),
        userId = this.userId,
        version = null,
    )

fun UpdateEventSignUpRequest.asData(eventId: Long): EventSignUpData =
    EventSignUpData(
        eventId = eventId,
        answers = this.answers?.map { it.asData() } ?: emptyList(),
        guest = this.guest?.asData(),
        userId = this.userId,
        version = this.version,
    )
