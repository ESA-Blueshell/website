package net.blueshell.api.domain.event.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import net.blueshell.api.domain.event.web.validation.GuestOrUserRequired
import net.blueshell.api.domain.event.web.validation.ValidEventSignUp
import net.blueshell.api.shared.dto.AuditedAutoIdDTO
import net.blueshell.api.domain.survey.web.dto.AnswerDTO
import net.blueshell.api.domain.user.web.dto.SimpleUserDTO

@Schema(name = "EventSignUp")
@ValidEventSignUp
@GuestOrUserRequired
data class EventSignUpDTO(
    @field:NotNull
    var eventId: Long? = null,

    @field:Valid
    var answers: MutableList<AnswerDTO>? = mutableListOf(),

    var guest: GuestDTO? = null,
    var user: SimpleUserDTO? = null,
    var userId: Long? = null
) : AuditedAutoIdDTO()
