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
    @field:Schema(description = "The folder to make it in; none puts it at the top level.", example = "Committees")
    val folder: String? = null,
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

@Schema(name = "DeleteExternalTargetRequest", description = "The list's name, typed exactly, to confirm a delete Brevo cannot undo.")
data class DeleteExternalTargetRequest(
    @field:NotBlank(message = "Type the list's name to delete it")
    val name: String,
)

@Schema(name = "ApplyTidyRequest", description = "The lists picked out of the tidy's preview.")
data class ApplyTidyRequest(
    val externalIds: List<String>,
)

@Schema(name = "CreateMissingTargetsRequest", description = "The missing lists to create, by target; none creates every one.")
data class CreateMissingTargetsRequest(
    val targetIds: List<Long> = emptyList(),
)

@Schema(name = "CreateMissingTargetsResponse")
data class CreateMissingTargetsResponse(
    @param:Schema(description = "How many lists are queued to be created")
    val queued: Int,
)
