package net.blueshell.api.committee.web

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.file.api.Image

/** A committee as its public page shows it. Its members are named by Discord only, never by name. */
@Schema(name = "CommitteePageResponse")
data class CommitteePageResponse(
    val id: Long,
    val name: String,
    val slug: String,
    val description: String,
    val listed: Boolean,
    val archived: Boolean,
    val banner: Image?,
    val gameCodes: List<String>,
    val members: List<CommitteeSeatResponse>,
)

@Schema(name = "CommitteeSeatResponse")
data class CommitteeSeatResponse(
    @field:Schema(description = "Their Discord username, absent for a member who has not linked Discord")
    val discordTag: String?,
    @field:Schema(description = "Their Discord avatar's address")
    val avatar: String?,
    val role: String?,
)
