package net.blueshell.api.validation.date;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = TodayValidator.class)
public @interface Today {
    String message() default "Date must be today";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

