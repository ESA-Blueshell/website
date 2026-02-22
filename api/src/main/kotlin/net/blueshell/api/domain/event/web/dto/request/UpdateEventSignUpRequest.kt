package net.blueshell.api.domain.event.web.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import net.blueshell.api.domain.survey.web.dto.request.AnswerRequest

@Schema(name = "UpdateEventSignUpRequest")
data class UpdateEventSignUpRequest(
    @field:Valid
    var answers: MutableList<AnswerRequest>? = mutableListOf(),

    @field:Valid
    var guest: CreateGuestRequest? = null,

    var userId: Long? = null,

    var version: Long? = null
)
