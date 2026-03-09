package net.blueshell.api.domain.event.web.validation

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import java.time.Instant

class ValidSignUpDeadlineValidator : ConstraintValidator<ValidSignUpDeadline, HasSignUpDeadline> {
    override fun isValid(value: HasSignUpDeadline?, ctx: ConstraintValidatorContext): Boolean {
        if (value == null) return true
        val deadline = value.signUpDeadline ?: return true

        var valid = true

        if (!Instant.now().isBefore(deadline)) {
            ctx.disableDefaultConstraintViolation()
            ctx.buildConstraintViolationWithTemplate("Sign-up deadline must be in the future.")
                .addPropertyNode("signUpDeadline")
                .addConstraintViolation()
            valid = false
        }

        val endTime = value.endTime
        if (endTime != null && deadline.isAfter(endTime)) {
            ctx.disableDefaultConstraintViolation()
            ctx.buildConstraintViolationWithTemplate("Sign-up deadline cannot be after event end time.")
                .addPropertyNode("signUpDeadline")
                .addConstraintViolation()
            valid = false
        }

        return valid
    }
}
