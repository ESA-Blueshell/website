package net.blueshell.api.cohort.web

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

@Schema(name = "MoveTargetRequest", description = "Where to file a target.")
data class MoveTargetRequest(
    @field:NotBlank(message = "A folder is required")
    @field:Schema(
        description = "The folder to move the target into. Must already exist in the system.",
        example = "Contribution periods",
    )
    val folder: String,
)
