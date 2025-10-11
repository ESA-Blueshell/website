package net.blueshell.api.validation.survey;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidQuestionListValidator.class)
public @interface ValidQuestionList {
    String message() default "Invalid list of questions";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

