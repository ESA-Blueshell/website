package net.blueshell.api.validation.survey;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidAnswerValidator.class)
public @interface ValidAnswer {
    String message() default "Invalid answer to question";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}