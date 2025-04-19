package net.blueshell.api.validation.user;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = ValidActivationRequestValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidActivationRequest {
    String message() default "Invalid request.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
