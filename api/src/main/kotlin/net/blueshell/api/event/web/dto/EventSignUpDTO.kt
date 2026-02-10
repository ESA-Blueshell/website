package net.blueshell.api.event.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import net.blueshell.api.shared.dto.AuditedAutoIdDTO
import net.blueshell.api.survey.web.dto.AnswerDTO
import net.blueshell.api.user.web.dto.SimpleUserDTO

@Schema(name = "EventSignUp")
@net.blueshell.api.event.web.validation.ValidEventSignUp
@net.blueshell.api.event.web.validation.GuestOrUserRequired
data class EventSignUpDTO(
    @field:NotNull
    var eventId: Long? = null,

    @field:Valid
    var answers: MutableList<AnswerDTO> = mutableListOf(),

    var guest: GuestDTO? = null,
    var user: SimpleUserDTO? = null,
    var userId: Long? = null
) : AuditedAutoIdDTO()
