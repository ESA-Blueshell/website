package net.blueshell.api.dto.event

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import net.blueshell.api.dto.GuestDTO
import net.blueshell.api.dto.base.AuditedAutoIdDTO
import net.blueshell.api.dto.survey.AnswerDTO
import net.blueshell.api.dto.user.SimpleUserDTO

@Schema(name = "EventSignUp")
@net.blueshell.api.validation.event.ValidEventSignUp
@net.blueshell.api.validation.event.GuestOrUserRequired
data class EventSignUpDTO(
    var eventId: Long? = null,

    @field:Valid
    var answers: MutableList<AnswerDTO> = mutableListOf(),
    var guest: GuestDTO? = null,
    var user: SimpleUserDTO? = null,
    var userId: Long? = null
) : AuditedAutoIdDTO()
