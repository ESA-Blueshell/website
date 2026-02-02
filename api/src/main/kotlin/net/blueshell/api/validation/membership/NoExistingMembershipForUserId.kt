package net.blueshell.api.validation.membership;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Custom annotation to ensure the uniqueness of the username.
 */
@Documented
@Constraint(validatedBy = NoExistingMembershipForUserIdValidator.class)
@Target({FIELD})
@Retention(RUNTIME)
public @interface NoExistingMembershipForUserId {
    String message() default "User is already a member.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
