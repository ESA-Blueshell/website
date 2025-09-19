package net.blueshell.api.validation.date;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;

public class TodayValidator implements ConstraintValidator<Today, Date> {

    @Override
    public boolean isValid(Date value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // let @NotNull handle null
        }
        LocalDate inputDate = value.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
        return LocalDate.now().equals(inputDate);
    }
}

