package net.blueshell.api.validation.user;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Custom annotation to ensure the uniqueness of the email.
 */
@Documented
@Constraint(validatedBy = UniqueEmailValidator.class)
@Target({FIELD})
@Retention(RUNTIME)
public @interface UniqueEmail {
    String message() default "Email is already registered.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
