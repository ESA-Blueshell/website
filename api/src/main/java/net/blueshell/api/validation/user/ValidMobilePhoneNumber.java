package net.blueshell.api.validation.user;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Custom annotation to validate that a phone number is a valid mobile number.
 */
@Documented
@Constraint(validatedBy = ValidMobilePhoneNumberValidator.class)
@Target({FIELD})
@Retention(RUNTIME)
public @interface ValidMobilePhoneNumber {
    String message() default "Invalid mobile phone number.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
