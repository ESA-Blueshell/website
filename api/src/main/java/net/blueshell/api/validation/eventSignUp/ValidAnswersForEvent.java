package net.blueshell.api.validation.eventSignUp;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidAnswersForEventValidator.class)
public @interface ValidAnswersForEvent {
    String message() default "Invalid answers for event form";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}