package net.blueshell.api.dto.event

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import net.blueshell.api.base.BaseDTO
import net.blueshell.api.dto.GuestDTO
import net.blueshell.api.dto.survey.AnswerDTO
import net.blueshell.api.dto.user.SimpleUserDTO
import net.blueshell.api.validation.event.GuestOrUserRequired
import net.blueshell.api.validation.event.ValidEventSignUp
@Schema(name = "EventSignUp")
@ValidEventSignUp
@GuestOrUserRequired
class EventSignUpDTO : BaseDTO() {
    val eventId: Long? = null

    @Valid
    val answers: @Valid MutableList<AnswerDTO?>? = null
    val guest: GuestDTO? = null
    val user: SimpleUserDTO? = null
    val userId: Long? = null
}
