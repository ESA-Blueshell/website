package net.blueshell.api.validation.address;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({FIELD, PARAMETER})
@Retention(RUNTIME)
@Documented
@Constraint(validatedBy = CountryCodeValidatorImpl.class)
public @interface ValidCountryCode {
    String message() default "Country must be a valid ISO 3166-1 alpha-2 code";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
