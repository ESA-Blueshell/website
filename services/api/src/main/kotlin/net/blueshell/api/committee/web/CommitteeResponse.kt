package net.blueshell.api.committee.web

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.file.api.Image
import java.time.Instant

/**
 * A committee as every committee read answers it. Who sits on it is only told to the board and to
 * the committee's own members (#89); anybody else gets no `members` at all.
 */
@Schema(name = "CommitteeResponse")
data class CommitteeResponse(
    var id: Long,
    var name: String,
    var description: String,
    @field:Schema(description = "The address the committee's page answers to")
    var slug: String,
    @field:Schema(description = "Whether the committee is shown among the committees to join")
    var listed: Boolean,
    @field:Schema(description = "Whether the committee no longer runs")
    var archived: Boolean,
    var banner: Image?,
    @field:Schema(description = "Its logo, absent for none")
    var icon: Image?,
    @field:Schema(description = "The codes of the games the committee organises events for")
    var gameCodes: List<String>,
    @field:Schema(description = "Who sits on it, for the board and its own members; absent for anybody else")
    var members: List<CommitteeMemberResponse>?,
    var version: Long,
    var createdAt: Instant,
    var updatedAt: Instant,
)
