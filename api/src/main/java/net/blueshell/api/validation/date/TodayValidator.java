package net.blueshell.api.validation.date;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.LocalDate;

public class TodayValidator implements ConstraintValidator<Today, LocalDate> {

    @Override
    public boolean isValid(LocalDate value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // let @NotNull handle null
        }
        return LocalDate.now().equals(value);
    }
}

