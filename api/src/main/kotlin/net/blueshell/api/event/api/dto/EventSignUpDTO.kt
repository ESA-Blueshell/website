package net.blueshell.api.event.api.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import net.blueshell.api.event.api.dto.GuestDTO
import net.blueshell.api.shared.dto.AuditedAutoIdDTO
import net.blueshell.api.survey.api.dto.AnswerDTO
import net.blueshell.api.user.api.dto.SimpleUserDTO

@Schema(name = "EventSignUp")
@net.blueshell.api.event.api.validation.ValidEventSignUp
@net.blueshell.api.event.api.validation.GuestOrUserRequired
data class EventSignUpDTO(
    var eventId: Long? = null,

    @field:Valid
    var answers: MutableList<AnswerDTO> = mutableListOf(),
    var guest: GuestDTO? = null,
    var user: SimpleUserDTO? = null,
    var userId: Long? = null
) : AuditedAutoIdDTO()
