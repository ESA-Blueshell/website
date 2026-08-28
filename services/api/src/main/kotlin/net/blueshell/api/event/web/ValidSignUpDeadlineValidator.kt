package net.blueshell.api.event.web

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext

class ValidSignUpDeadlineValidator : ConstraintValidator<ValidSignUpDeadline, HasSignUpDeadline> {
    override fun isValid(value: HasSignUpDeadline?, ctx: ConstraintValidatorContext): Boolean {
        if (value == null) return true
        val deadline = value.signUpDeadline ?: return true

        var valid = true

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
