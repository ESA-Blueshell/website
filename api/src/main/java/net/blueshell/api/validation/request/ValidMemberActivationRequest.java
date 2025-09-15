package net.blueshell.api.validation.request;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = ValidMemberActivationRequestValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidMemberActivationRequest {
    String message() default "Invalid request.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
