package net.blueshell.api.validation.date

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import java.time.LocalDate

class TodayValidator : ConstraintValidator<net.blueshell.api.validation.date.Today?, LocalDate?> {
    override fun isValid(value: LocalDate?, context: ConstraintValidatorContext?): Boolean {
        if (value == null) {
            return true // let @NotNull handle null
        }
        return LocalDate.now() == value
    }
}

