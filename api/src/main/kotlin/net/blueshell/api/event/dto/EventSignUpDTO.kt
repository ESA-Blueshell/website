package net.blueshell.api.event.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import net.blueshell.api.event.dto.GuestDTO
import net.blueshell.api.shared.dto.AuditedAutoIdDTO
import net.blueshell.api.survey.dto.AnswerDTO
import net.blueshell.api.user.dto.SimpleUserDTO

@Schema(name = "EventSignUp")
@net.blueshell.api.event.validation.ValidEventSignUp
@net.blueshell.api.event.validation.GuestOrUserRequired
data class EventSignUpDTO(
    var eventId: Long? = null,

    @field:Valid
    var answers: MutableList<AnswerDTO> = mutableListOf(),
    var guest: GuestDTO? = null,
    var user: SimpleUserDTO? = null,
    var userId: Long? = null
) : AuditedAutoIdDTO()
