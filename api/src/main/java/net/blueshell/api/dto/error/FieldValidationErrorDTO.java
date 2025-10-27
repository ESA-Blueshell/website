package net.blueshell.api.dto.error;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One entry for each field/object error when validation fails.
 */
@Schema(name = "FieldValidationError", description = "Details about a single field/object validation error.")
public class FieldValidationErrorDTO {

    @Schema(description = "Object (target) name that failed validation.", example = "createUserRequest")
    public String objectName;

    @Schema(description = "Field that failed validation (null for global errors).", example = "email")
    public String field;

    @Schema(description = "Rejected value (may be omitted for security).", example = "not-an-email")
    public Object rejectedValue;

    @Schema(description = "Human-readable validation message.", example = "must be a well-formed email address")
    public String message;

    @Schema(description = "Validation code / constraint key.", example = "Email")
    public String code;
}
