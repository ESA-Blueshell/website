package net.blueshell.api.event.api.validation

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import net.blueshell.api.event.api.dto.EventSignUpDTO

class GuestOrUserRequiredValidator : ConstraintValidator<GuestOrUserRequired, EventSignUpDTO> {
    override fun isValid(dto: EventSignUpDTO?, ctx: ConstraintValidatorContext): Boolean {
        if (dto == null) return true

        val hasGuest = dto.guest != null
        val hasUser = dto.user != null || dto.userId != null

        if (hasGuest || hasUser) return true

        ctx.disableDefaultConstraintViolation()
        ctx.buildConstraintViolationWithTemplate("Either guest or user (or userId) must be provided.")
            .addPropertyNode("guest")
            .addConstraintViolation()
        return false
    }
}
