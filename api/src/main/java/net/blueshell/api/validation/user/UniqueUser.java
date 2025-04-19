package net.blueshell.api.validation.user;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = UniqueUserValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface UniqueUser {
    String message() default "User has duplicate fields.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
