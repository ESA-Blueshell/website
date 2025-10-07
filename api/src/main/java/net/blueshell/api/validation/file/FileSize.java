package net.blueshell.api.validation.file;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = {FileSizeValidator.class})
public @interface FileSize {

    String message() default "File size must be between {min} and {max} bytes";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /**
     * Inclusive min (bytes)
     */
    long min() default 0L;

    /**
     * Inclusive max (bytes)
     */
    long max();

    /**
     * Whether an empty upload (isEmpty) is allowed
     */
    boolean allowEmpty() default false;
}

