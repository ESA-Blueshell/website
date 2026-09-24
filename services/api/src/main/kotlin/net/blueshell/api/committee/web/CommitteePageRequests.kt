package net.blueshell.api.committee.web

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/** What a committee's own members change about it. */
@Schema(name = "CommitteeOwnPageRequest")
data class CommitteeOwnPageRequest(
    @field:NotBlank(message = "Committee description cannot be empty.")
    @field:Size(min = 1, max = 4095, message = "Description must be 1-4095 characters")
    val description: String,
    val banner: String? = null,
    /** Absent leaves the committee's games as they are. */
    val gameCodes: List<String>? = null,
    val version: Long? = null,
)

@Schema(name = "ArchiveCommitteeRequest")
data class ArchiveCommitteeRequest(
    val archived: Boolean,
)

/** The committees that organise events for one game, set from the game's side. */
@Schema(name = "GameOrganisersRequest")
data class GameOrganisersRequest(
    val committeeIds: List<Long>,
)
