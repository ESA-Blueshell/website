package net.blueshell.api.validation.file;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = AllowedContentTypesValidator.class)
@Target({ ElementType.PARAMETER, ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
public @interface AllowedContentTypes {
    String[] value();

    String message() default "Unsupported media type. Allowed: {value}";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

