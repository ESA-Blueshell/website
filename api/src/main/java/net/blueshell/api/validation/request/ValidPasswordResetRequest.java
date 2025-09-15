package net.blueshell.api.validation.request;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = ValidPasswordResetRequestValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPasswordResetRequest {
    String message() default "Invalid request.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
