package net.blueshell.api.dto.error;


import io.swagger.v3.oas.annotations.media.Schema;
import net.blueshell.api.base.BaseDTO;

import java.net.URI;
import java.util.List;

/**
 * Mirrors Spring Boot 3.x ProblemDetail JSON with binding errors included.
 * This matches the effect of:
 *   include-message: always
 *   include-binding-errors: always
 *   include-stacktrace: never
 *   include-exception: false
 */
@Schema(name = "ApiError", description = "Problem Details for HTTP APIs including validation errors.")
public class ApiErrorDTO extends BaseDTO {

    @Schema(description = "Problem type URI (RFC 7807).", example = "about:blank")
    public String type;

    @Schema(description = "Short, human-readable summary of the problem.", example = "Bad Request")
    public String title;

    @Schema(description = "HTTP status code.", example = "400")
    public Integer status;

    @Schema(description = "Human-readable explanation specific to this occurrence.", example = "Validation failed for request.")
    public String detail;

    @Schema(description = "A URI reference that identifies the specific occurrence.", example = "/api/v1/users")
    public URI instance;

    @Schema(description = "List of field/object validation errors (present when binding/validation fails).")
    public List<FieldValidationErrorDTO> errors;

    @Schema(description = "Trace or correlation id if available (Spring may add this via problem detail handlers).", example = "a8c0c4e5f1c24a7e")
    public String traceId;
}

