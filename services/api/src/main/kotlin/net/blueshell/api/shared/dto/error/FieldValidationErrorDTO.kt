package net.blueshell.api.shared.dto.error

import io.swagger.v3.oas.annotations.media.Schema

/**
 * One entry for each field/object error when validation fails.
 */
@Schema(name = "FieldValidationError", description = "Details about a single field/object validation error.")
data class FieldValidationErrorDTO(
    @JvmField
    @field:Schema(description = "Object (target) name that failed validation.", example = "createUserRequest")
    var objectName: String? = null,

    @JvmField
    @field:Schema(description = "Field that failed validation (null for global errors).", example = "email")
    var field: String? = null,

    @JvmField
    @field:Schema(description = "Human-readable validation message.", example = "must be a well-formed email address")
    var message: String? = null,

    @JvmField
    @field:Schema(description = "Validation code / constraint key.", example = "Email")
    var code: String? = null,

    @JvmField
    @field:Schema(
        description = "Identifiers the error refers to, when the failing field carries a collection. " +
            "Lets a client name the offending rows and reload them rather than restate the whole request.",
        example = "[42, 57]",
    )
    var values: List<Long>? = null,

    @JvmField
    @field:Schema(
        description = "Identifiers the error refers to when they are not numeric — an external " +
            "system's own ids, for instance. The counterpart of `values`; an error carries one or " +
            "the other, never both.",
        example = "[\"7\", \"33\"]",
    )
    var refs: List<String>? = null
)
