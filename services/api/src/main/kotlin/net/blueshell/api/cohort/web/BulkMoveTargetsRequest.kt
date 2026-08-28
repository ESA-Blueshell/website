package net.blueshell.api.cohort.web

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty

@Schema(name = "BulkMoveTargetsRequest", description = "Where to file several targets at once.")
data class BulkMoveTargetsRequest(
    @field:NotEmpty(message = "Select at least one target")
    @field:Schema(
        description = "The external ids of the targets to move, as the system itself names them.",
        example = "[\"7\", \"33\"]",
    )
    val externalIds: List<String>,

    @field:NotBlank(message = "A folder is required")
    @field:Schema(
        description = "The folder to move them into. Must already exist in the system.",
        example = "Committees",
    )
    val folder: String,
)
