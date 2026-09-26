package net.blueshell.api.committee.web

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import net.blueshell.api.shared.model.DESCRIPTION_MAX

/** What a committee's own members change about it. */
@Schema(name = "CommitteeOwnPageRequest")
data class CommitteeOwnPageRequest(
    @field:NotBlank(message = "Committee description cannot be empty.")
    @field:Size(min = 1, max = DESCRIPTION_MAX, message = "Description must be 1-$DESCRIPTION_MAX characters")
    val description: String,
    val banner: String? = null,
    val icon: String? = null,
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
