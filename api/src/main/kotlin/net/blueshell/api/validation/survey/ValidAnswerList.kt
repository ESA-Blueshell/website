package net.blueshell.api.validation.survey;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidAnswerListValidator.class)
public @interface ValidAnswerList {
    String message() default "Invalid list of answers";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

