package net.blueshell.api.validation.event;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target(TYPE)
@Retention(RUNTIME)
@Documented
@Constraint(validatedBy = ValidEventSignUpValidator.class)
public @interface ValidEventSignUp {
    String message() default "Invalid event sign-up payload";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
