package net.blueshell.api.validation.event

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import net.blueshell.api.dto.event.EventSignUpDTO

class GuestOrUserRequiredValidator : ConstraintValidator<GuestOrUserRequired?, EventSignUpDTO?> {
    override fun isValid(dto: EventSignUpDTO?, ctx: ConstraintValidatorContext): Boolean {
        if (dto == null) return true

        val hasGuest = dto.getGuest() != null
        val hasUser = dto.getUser() != null || dto.getUserId() != null

        if (hasGuest || hasUser) return true

        ctx.disableDefaultConstraintViolation()
        ctx.buildConstraintViolationWithTemplate("Either guest or user (or userId) must be provided.")
            .addPropertyNode("guest")
            .addConstraintViolation()
        return false
    }
}
