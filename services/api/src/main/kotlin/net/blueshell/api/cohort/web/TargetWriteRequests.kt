package net.blueshell.api.cohort.web

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

@Schema(name = "CreateExternalTargetRequest", description = "A new target, linked to no cohort.")
data class CreateExternalTargetRequest(
    @field:NotBlank(message = "A name is required")
    @field:Size(max = 255)
    @field:Schema(example = "Pub quiz 2026")
    val name: String,
    @field:NotBlank(message = "A folder is required")
    @field:Size(max = 64)
    @field:Schema(description = "The folder to make it in; one that does not exist is made.", example = "Committees")
    val folder: String,
)

@Schema(name = "RenameExternalTargetRequest", description = "Another name for a target.")
data class RenameExternalTargetRequest(
    @field:NotBlank(message = "A name is required")
    @field:Size(max = 255)
    val name: String,
)

@Schema(name = "CreateTargetFolderRequest", description = "A folder to make, by name.")
data class CreateTargetFolderRequest(
    @field:NotBlank(message = "A name is required")
    @field:Size(max = 64)
    val name: String,
)
