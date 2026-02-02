package net.blueshell.api.dto.event

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import lombok.Data
import lombok.EqualsAndHashCode
import lombok.NoArgsConstructor
import net.blueshell.api.base.BaseDTO
import net.blueshell.api.dto.GuestDTO
import net.blueshell.api.dto.survey.AnswerDTO
import net.blueshell.api.dto.user.SimpleUserDTO
import net.blueshell.api.validation.event.GuestOrUserRequired
import net.blueshell.api.validation.event.ValidEventSignUp

@Data
@EqualsAndHashCode(callSuper = false)
@Schema(name = "EventSignUp")
@ValidEventSignUp
@GuestOrUserRequired
@NoArgsConstructor
class EventSignUpDTO : BaseDTO() {
    private val id: Long? = null
    private val eventId: Long? = null

    @Valid
    private val answers: @Valid MutableList<AnswerDTO?>? = null
    private val guest: GuestDTO? = null
    private val user: SimpleUserDTO? = null
    private val userId: Long? = null
}
