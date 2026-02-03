package net.blueshell.api.dto.error

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.base.BaseDTO
import java.net.URI

/**
 * Mirrors Spring Boot 3.x ProblemDetail JSON with binding errors included.
 * This matches the effect of:
 * include-message: always
 * include-binding-errors: always
 * include-stacktrace: never
 * include-exception: false
 */
@Schema(name = "ApiError", description = "Problem Details for HTTP APIs including validation errors.")
data class ApiErrorDTO(
    @JvmField
    @Schema(description = "Problem type URI (RFC 7807).", example = "about:blank")
    var type: String? = null,

    @JvmField
    @Schema(description = "Short, human-readable summary of the problem.", example = "Bad Request")
    var title: String? = null,

    @JvmField
    @Schema(description = "HTTP status code.", example = "400")
    var status: Int? = null,

    @JvmField
    @Schema(
        description = "Human-readable explanation specific to this occurrence.",
        example = "Validation failed for request."
    )
    var detail: String? = null,

    @JvmField
    @Schema(description = "A URI reference that identifies the specific occurrence.", example = "/api/v1/users")
    var instance: URI? = null,

    @JvmField
    @Schema(description = "List of field/object validation errors (present when binding/validation fails).")
    var errors: MutableList<FieldValidationErrorDTO?>? = null,

    @JvmField
    @Schema(
        description = "Trace or correlation id if available (Spring may add this via problem detail handlers).",
        example = "a8c0c4e5f1c24a7e"
    )
    var traceId: String? = null
) : BaseDTO()
