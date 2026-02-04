package net.blueshell.api.dto.event

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import net.blueshell.api.base.dto.AuditedAutoIdDTO
import net.blueshell.api.dto.GuestDTO
import net.blueshell.api.dto.survey.AnswerDTO
import net.blueshell.api.dto.user.SimpleUserDTO
import net.blueshell.api.validation.event.GuestOrUserRequired
import net.blueshell.api.validation.event.ValidEventSignUp

@Schema(name = "EventSignUp")
@ValidEventSignUp
@GuestOrUserRequired
data class EventSignUpDTO(
    var eventId: Long? = null,

    @field:Valid
    var answers: MutableList<AnswerDTO> = mutableListOf(),
    var guest: GuestDTO? = null,
    var user: SimpleUserDTO? = null,
    var userId: Long? = null
) : AuditedAutoIdDTO()
