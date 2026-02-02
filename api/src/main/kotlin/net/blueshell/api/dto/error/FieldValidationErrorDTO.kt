package net.blueshell.api.dto.error

import io.swagger.v3.oas.annotations.media.Schema

/**
 * One entry for each field/object error when validation fails.
 */
@Schema(name = "FieldValidationError", description = "Details about a single field/object validation error.")
class FieldValidationErrorDTO {
    @JvmField
    @Schema(description = "Object (target) name that failed validation.", example = "createUserRequest")
    var objectName: String? = null

    @JvmField
    @Schema(description = "Field that failed validation (null for global errors).", example = "email")
    var field: String? = null

    @JvmField
    @Schema(description = "Rejected value (may be omitted for security).", example = "not-an-email")
    var rejectedValue: Any? = null

    @JvmField
    @Schema(description = "Human-readable validation message.", example = "must be a well-formed email address")
    var message: String? = null

    @JvmField
    @Schema(description = "Validation code / constraint key.", example = "Email")
    var code: String? = null
}
