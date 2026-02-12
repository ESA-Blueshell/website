package net.blueshell.api.domain.event.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import net.blueshell.api.domain.survey.web.dto.AnswerResponse
import net.blueshell.api.domain.user.web.dto.SimpleUserDTO
import net.blueshell.api.shared.dto.AuditedAutoIdDTO

@Schema(name = "EventSignUpResponse")
data class EventSignUpResponse(
    @field:NotNull
    var eventId: Long? = null,

    @field:Valid
    var answers: MutableList<AnswerResponse>? = mutableListOf(),

    var guest: GuestResponse? = null,
    var user: SimpleUserDTO? = null,
    var userId: Long? = null
) : AuditedAutoIdDTO()
