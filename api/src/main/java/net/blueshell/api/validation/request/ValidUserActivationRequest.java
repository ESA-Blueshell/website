package net.blueshell.api.validation.request;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = ValidUserActivationRequestValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidUserActivationRequest {
    String message() default "Invalid request.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
